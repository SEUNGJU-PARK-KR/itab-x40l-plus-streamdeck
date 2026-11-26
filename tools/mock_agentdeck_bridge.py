#!/usr/bin/env python3
"""Minimal AgentDeck-compatible mock bridge for ITAB deck verification."""
import asyncio
import json
import sys
from datetime import datetime, timezone

import websockets

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 9120

sessions = [
    {
        "id": "observed:claude:alpha",
        "alive": True,
        "projectName": "itab-firmware",
        "agentType": "claude-code",
        "state": "processing",
        "modelName": "SWE-2.1",
        "activity": "Editing deck-launcher sources",
        "weight": 0,
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "controlMode": "observed",
        "liveAnswerable": False,
    },
    {
        "id": "observed:codex:beta",
        "alive": True,
        "projectName": "server-setup",
        "agentType": "codex-cli",
        "state": "awaiting_permission",
        "modelName": "GPT-5",
        "activity": "Waiting for approval",
        "question": "Bash 명령 실행을 허용할까요?",
        "questionDetail": "dnf install -y python3",
        "promptType": "yes_no",
        "requestId": "mock-permission-1",
        "weight": 1,
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "controlMode": "observed",
        "liveAnswerable": True,
    },
    {
        "id": "managed:gamma",
        "alive": True,
        "projectName": "agentdeck-client",
        "agentType": "opencode",
        "state": "awaiting_option",
        "modelName": "local",
        "activity": "Waiting for a choice",
        "question": "다음 작업을 선택하세요",
        "promptType": "multi_select",
        "options": [
            {"index": 0, "label": "테스트 실행", "shortcut": "t", "recommended": True, "kind": "choice"},
            {"index": 1, "label": "빌드만 실행", "shortcut": "b", "recommended": False, "kind": "choice"},
            {"index": 2, "label": "중단", "shortcut": "x", "recommended": False, "kind": "choice"},
        ],
        "weight": 2,
        "startedAt": datetime.now(timezone.utc).isoformat(),
        "controlMode": "managed",
        "liveAnswerable": True,
    },
]

clients = set()
focused = sessions[0]["id"]


def event(type_, **fields):
    data = {"type": type_}
    data.update(fields)
    return json.dumps(data, ensure_ascii=False)


def sessions_frame():
    return event("sessions_list", sessions=sessions)


def state_frame():
    session = next((s for s in sessions if s["id"] == focused), sessions[0])
    return event(
        "state_update",
        state=session.get("state", "idle"),
        sessionId=session["id"],
        focusedSessionId=focused,
        agentType=session.get("agentType"),
        projectName=session.get("projectName"),
        modelName=session.get("modelName"),
        question=session.get("question"),
        options=session.get("options", []),
        navigable=False,
    )


def usage_frame():
    return event(
        "usage_update",
        sessionDurationSec=3720,
        inputTokens=120450,
        outputTokens=18420,
        toolCalls=42,
        fiveHourPercent=37,
        sevenDayPercent=61,
    )


async def broadcast(message):
    dead = []
    for ws in list(clients):
        try:
            await ws.send(message)
        except Exception:
            dead.append(ws)
    for ws in dead:
        clients.discard(ws)


def find_session(session_id):
    if not session_id:
        return None
    matches = [s for s in sessions if s["id"] == session_id or s["id"].startswith(session_id)]
    return matches[0] if matches else None


async def handle_command(ws, obj):
    global focused
    typ = obj.get("type")
    print("COMMAND", json.dumps(obj, ensure_ascii=False), flush=True)
    if typ == "focus_session":
        session = find_session(obj.get("sessionId"))
        if session:
            focused = session["id"]
            await broadcast(state_frame())
    elif typ == "permission_decision":
        rid = obj.get("requestId")
        for session in sessions:
            if session.get("requestId") == rid:
                session.pop("requestId", None)
                session.pop("question", None)
                session.pop("questionDetail", None)
                session["state"] = "processing" if obj.get("decision") == "allow" else "idle"
                session["activity"] = "Permission " + str(obj.get("decision"))
        await broadcast(sessions_frame())
        await broadcast(state_frame())
    elif typ == "select_option":
        session = find_session(obj.get("sessionId")) or find_session(focused)
        if session:
            idx = obj.get("index")
            label = str(idx)
            for opt in session.get("options", []):
                if opt.get("index") == idx:
                    label = opt.get("label", label)
            session.pop("question", None)
            session.pop("questionDetail", None)
            session["options"] = []
            session["state"] = "processing"
            session["activity"] = "Selected: " + label
        await broadcast(sessions_frame())
        await broadcast(state_frame())
    elif typ == "session_command":
        session = find_session(obj.get("sessionId")) or find_session(focused)
        command = (obj.get("command") or {}).get("type")
        if session and command in ("interrupt", "escape"):
            session["state"] = "idle"
            session["activity"] = "Interrupted by deck"
            session.pop("question", None)
            session.pop("requestId", None)
            session["options"] = []
        await broadcast(sessions_frame())
        await broadcast(state_frame())
    elif typ == "query_usage":
        await ws.send(usage_frame())


async def handler(ws):
    clients.add(ws)
    print("CLIENT connected", flush=True)
    try:
        await ws.send(event("connection", status="connected", sessionId=focused))
        await ws.send(state_frame())
        await ws.send(usage_frame())
        await ws.send(sessions_frame())
        async for raw in ws:
            try:
                obj = json.loads(raw)
            except Exception:
                continue
            await handle_command(ws, obj)
    finally:
        clients.discard(ws)
        print("CLIENT disconnected", flush=True)


async def main():
    print(f"mock AgentDeck bridge listening on ws://0.0.0.0:{PORT}", flush=True)
    async with websockets.serve(handler, "0.0.0.0", PORT):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
