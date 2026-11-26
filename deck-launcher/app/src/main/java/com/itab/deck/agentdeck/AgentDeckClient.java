package com.itab.deck.agentdeck;

import android.os.Handler;
import android.os.Looper;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.List;

/**
 * AgentDeck daemon WebSocket client.
 *
 * Wire contract (shared/src/protocol.ts):
 *  - connect to ws://host:9120 or ws://host:9120?token=<32hex>
 *  - send client_register once after open
 *  - inbound frames: sessions_list / state_update / usage_update / connection
 *  - outbound commands: focus_session, select_option, permission_decision,
 *    session_command {interrupt, escape}, query_usage
 *
 * Reconnects automatically with a 1/2/4/8s backoff. An auth-looking failure
 * (HTTP 401/403, close 4001/4401) stops reconnecting and reports
 * onAuthRequired() so the user can pair or enter a token.
 *
 * All listener callbacks are posted to the main looper.
 */
public class AgentDeckClient {

    public interface Listener {
        void onSocketOpen();
        void onSessions(List<DeckSession> sessions);
        void onStateUpdate(JSONObject frame);
        void onUsageUpdate(JSONObject frame);
        void onConnectionEvent(JSONObject frame);
        void onSocketClosed(int code, String reason);
        void onSocketError(String message);
        /** Auth/permission failure — pairing or token required. No auto-retry. */
        void onAuthRequired();
        void onReconnectScheduled(long delayMs, int attempt);
    }

    private static final long[] BACKOFF_MS = {1000, 2000, 4000, 8000};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private URI endpoint;
    private WebSocketClient socket;
    private int attempts;
    private boolean manualClose;
    private boolean destroyed;

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            doConnect();
        }
    };

    public AgentDeckClient(Listener listener) {
        this.listener = listener;
    }

    /** Start (or restart) a connection. Resets the backoff counter. */
    public synchronized void connect(URI uri) {
        endpoint = uri;
        manualClose = false;
        attempts = 0;
        main.removeCallbacks(reconnectRunnable);
        doConnect();
    }

    /** Manual disconnect — stops auto-reconnect. */
    public synchronized void disconnect() {
        manualClose = true;
        main.removeCallbacks(reconnectRunnable);
        closeSocketQuietly();
    }

    public synchronized void destroy() {
        destroyed = true;
        manualClose = true;
        main.removeCallbacksAndMessages(null);
        closeSocketQuietly();
    }

    public synchronized boolean isOpen() {
        return socket != null && socket.isOpen();
    }

    // ---- internals ---------------------------------------------------------

    private synchronized void doConnect() {
        if (destroyed || manualClose || endpoint == null) return;
        closeSocketQuietly();
        final boolean[] authSuspect = {false}; // per-socket flag set by this socket's onError
        final WebSocketClient ws = new WebSocketClient(endpoint) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                synchronized (AgentDeckClient.this) {
                    attempts = 0;
                    authSuspect[0] = false;
                }
                sendRegister(this);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSocketOpen();
                    }
                });
            }

            @Override
            public void onMessage(String message) {
                final JSONObject frame;
                try {
                    frame = new JSONObject(message);
                } catch (Exception e) {
                    return; // malformed frame — ignore
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        dispatch(frame);
                    }
                });
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                final boolean auth = authSuspect[0]
                        || code == 4001 || code == 4401
                        || looksAuth(reason);
                final WebSocketClient closed = this;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        handleClose(closed, code, reason, auth);
                    }
                });
            }

            @Override
            public void onError(Exception ex) {
                final String msg = ex == null ? "unknown" : String.valueOf(ex.getMessage());
                if (looksAuth(msg)) {
                    authSuspect[0] = true;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onSocketError(msg);
                    }
                });
            }
        };
        ws.setConnectionLostTimeout(30);
        socket = ws;
        ws.connect();
    }

    private void closeSocketQuietly() {
        WebSocketClient ws = socket;
        socket = null;
        if (ws == null) return;
        try {
            ws.close();
        } catch (Exception ignored) {
        }
    }

    private void handleClose(WebSocketClient closed, int code, String reason, boolean auth) {
        boolean wasCurrent;
        synchronized (this) {
            wasCurrent = (socket == closed);
            if (wasCurrent) socket = null;
        }
        if (!wasCurrent) return; // stale close from a replaced socket — ignore
        if (auth) {
            listener.onAuthRequired();
            return;
        }
        listener.onSocketClosed(code, reason);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        synchronized (this) {
            if (destroyed || manualClose) return;
        }
        long delay;
        int attempt;
        synchronized (this) {
            delay = BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length - 1)];
            attempts++;
            attempt = attempts;
        }
        listener.onReconnectScheduled(delay, attempt);
        main.postDelayed(reconnectRunnable, delay);
    }

    private static boolean looksAuth(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("401") || m.contains("403")
                || m.contains("unauthorized") || m.contains("authoriz");
    }

    private void dispatch(JSONObject frame) {
        String type = frame.optString("type", "");
        switch (type) {
            case "sessions_list":
                listener.onSessions(DeckSession.listFrom(frame.optJSONArray("sessions")));
                break;
            case "state_update":
                listener.onStateUpdate(frame);
                break;
            case "usage_update":
                listener.onUsageUpdate(frame);
                break;
            case "connection":
                listener.onConnectionEvent(frame);
                break;
            default:
                break; // unknown frame — ignore
        }
    }

    private boolean sendFrame(JSONObject frame) {
        WebSocketClient ws;
        synchronized (this) {
            ws = socket;
        }
        if (ws == null || !ws.isOpen()) return false;
        try {
            ws.send(frame.toString());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendRegister(WebSocketClient ws) {
        try {
            JSONObject device = new JSONObject();
            device.put("id", "itab-x40l");
            device.put("name", "ITAB Tablet");
            device.put("family", "tablet");
            device.put("kind", "tablet");
            JSONArray devices = new JSONArray();
            devices.put(device);

            JSONObject frame = new JSONObject();
            frame.put("type", "client_register");
            frame.put("clientType", "android-dashboard");
            frame.put("clientLabel", "ITAB Tablet");
            frame.put("devices", devices);

            ws.send(frame.toString());
        } catch (Exception ignored) {
        }
    }

    // ---- commands ----------------------------------------------------------

    public void focusSession(String sessionId) {
        try {
            JSONObject frame = new JSONObject();
            frame.put("type", "focus_session");
            frame.put("sessionId", sessionId);
            sendFrame(frame);
        } catch (Exception ignored) {
        }
    }

    public void selectOption(int index, String sessionId, String question) {
        try {
            JSONObject frame = new JSONObject();
            frame.put("type", "select_option");
            frame.put("index", index);
            if (sessionId != null) frame.put("sessionId", sessionId);
            if (question != null) frame.put("question", question);
            sendFrame(frame);
        } catch (Exception ignored) {
        }
    }

    public void permissionDecision(String requestId, String decision, String sessionId) {
        try {
            JSONObject frame = new JSONObject();
            frame.put("type", "permission_decision");
            frame.put("requestId", requestId);
            frame.put("decision", decision);
            if (sessionId != null) frame.put("sessionId", sessionId);
            sendFrame(frame);
        } catch (Exception ignored) {
        }
    }

    /** Session-scoped command, e.g. type "interrupt" or "escape". */
    public void sessionCommand(String sessionId, String commandType) {
        try {
            JSONObject command = new JSONObject();
            command.put("type", commandType);
            JSONObject frame = new JSONObject();
            frame.put("type", "session_command");
            frame.put("sessionId", sessionId);
            frame.put("command", command);
            sendFrame(frame);
        } catch (Exception ignored) {
        }
    }

    public void queryUsage() {
        try {
            JSONObject frame = new JSONObject();
            frame.put("type", "query_usage");
            sendFrame(frame);
        } catch (Exception ignored) {
        }
    }
}
