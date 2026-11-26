import asyncio, json, websockets
async def main():
    async with websockets.connect('ws://127.0.0.1:9120') as ws:
        frames=[]
        for _ in range(4):
            frames.append(json.loads(await asyncio.wait_for(ws.recv(), timeout=3)))
        print('initial:', [f.get('type') for f in frames])
        await ws.send(json.dumps({'type':'focus_session','sessionId':'observed:codex:beta'}))
        print('after focus:', json.loads(await asyncio.wait_for(ws.recv(), timeout=3)).get('type'))
        await ws.send(json.dumps({'type':'permission_decision','requestId':'mock-permission-1','decision':'allow'}))
        got=[]
        for _ in range(2):
            got.append(json.loads(await asyncio.wait_for(ws.recv(), timeout=3)))
        print('after permission:', [f.get('type') for f in got])
asyncio.run(main())
