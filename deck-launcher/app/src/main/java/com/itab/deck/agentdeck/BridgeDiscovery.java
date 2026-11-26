package com.itab.deck.agentdeck;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Listens for the AgentDeck daemon's UDP discovery beacon on port 9121.
 * Beacon shape: {"v":1,"ip":"...","port":9120,"project":"...","agent":"..."}.
 * The beacon intentionally contains no credential; it only seeds host/port.
 */
public class BridgeDiscovery {
    public static final int BEACON_PORT = 9121;

    public interface Callback {
        void onResult(boolean found, String host, int port, String label, String error);
    }

    public static void discoverOnce(final int timeoutMs, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket(null);
                    socket.setReuseAddress(true);
                    socket.bind(new InetSocketAddress(BEACON_PORT));
                    socket.setSoTimeout(500);

                    byte[] buffer = new byte[2048];
                    long deadline = System.currentTimeMillis() + timeoutMs;
                    while (System.currentTimeMillis() < deadline) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        try {
                            socket.receive(packet);
                        } catch (java.net.SocketTimeoutException timeout) {
                            continue;
                        }
                        String text = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                        JSONObject beacon;
                        try {
                            beacon = new JSONObject(text);
                        } catch (Exception parseError) {
                            continue;
                        }
                        String host = nullableString(beacon, "ip");
                        if (host == null || host.isEmpty()) {
                            host = packet.getAddress() == null ? null : packet.getAddress().getHostAddress();
                        }
                        int port = beacon.optInt("port", DeckPrefs.DEFAULT_PORT);
                        if (host == null || host.isEmpty() || port <= 0) continue;

                        String project = nullableString(beacon, "project");
                        String agent = nullableString(beacon, "agent");
                        String label = host + ":" + port;
                        if (project != null && !project.isEmpty()) label = project + " · " + label;
                        else if (agent != null && !agent.isEmpty()) label = agent + " · " + label;

                        final String resultHost = host;
                        final int resultPort = port;
                        final String resultLabel = label;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(true, resultHost, resultPort, resultLabel, null);
                            }
                        });
                        return;
                    }
                    postFailure(main, callback, "timeout");
                } catch (final Exception e) {
                    postFailure(main, callback, String.valueOf(e.getMessage()));
                } finally {
                    if (socket != null) socket.close();
                }
            }
        }, "agentdeck-discovery").start();
    }

    private static void postFailure(Handler main, final Callback callback, final String error) {
        main.post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(false, null, DeckPrefs.DEFAULT_PORT, null, error);
            }
        });
    }

    private static String nullableString(JSONObject object, String key) {
        if (object == null || !object.has(key) || object.isNull(key)) return null;
        String value = object.optString(key, null);
        return "null".equals(value) ? null : value;
    }
}
