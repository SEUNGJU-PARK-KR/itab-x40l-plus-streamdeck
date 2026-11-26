package com.itab.deck.agentdeck;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.URI;

/**
 * Persisted AgentDeck bridge endpoint + pairing token.
 * The token is stored here and never rendered or logged elsewhere.
 */
public class DeckPrefs {

    public static final int DEFAULT_PORT = 9120;

    private static final String PREFS = "agentdeck_bridge";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    public DeckPrefs(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String host() {
        return prefs.getString(KEY_HOST, "");
    }

    public int port() {
        return prefs.getInt(KEY_PORT, DEFAULT_PORT);
    }

    public String token() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public boolean configured() {
        return !host().isEmpty();
    }

    /** host:port label — never includes the token. */
    public String endpointLabel() {
        if (!configured()) return "";
        return host() + ":" + port();
    }

    public URI wsUri() throws Exception {
        StringBuilder sb = new StringBuilder("ws://").append(host()).append(':').append(port());
        String token = token();
        if (!token.isEmpty()) {
            sb.append("?token=").append(token);
        }
        return new URI(sb.toString());
    }

    public void save(String host, int port, String token) {
        prefs.edit()
                .putString(KEY_HOST, host == null ? "" : host.trim())
                .putInt(KEY_PORT, port > 0 ? port : DEFAULT_PORT)
                .putString(KEY_TOKEN, token == null ? "" : token.trim())
                .apply();
    }

    public void saveEndpoint(String host, int port) {
        prefs.edit()
                .putString(KEY_HOST, host == null ? "" : host.trim())
                .putInt(KEY_PORT, port > 0 ? port : DEFAULT_PORT)
                .apply();
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token == null ? "" : token.trim()).apply();
    }

    /** Parsed bridge endpoint from free-form user input. */
    public static class Endpoint {
        public final String host;
        public final int port;

        Endpoint(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    /**
     * Accepts "host", "host:port", or any ws(s)/http(s)://host[:port][/path] form.
     * Returns null when the input cannot yield a host.
     */
    public static Endpoint parseEndpoint(String raw) {
        if (raw == null) return null;
        String text = raw.trim();
        if (text.isEmpty()) return null;
        int scheme = text.indexOf("://");
        if (scheme >= 0) text = text.substring(scheme + 3);
        int slash = text.indexOf('/');
        if (slash >= 0) text = text.substring(0, slash);
        int query = text.indexOf('?');
        if (query >= 0) text = text.substring(0, query);
        if (text.isEmpty()) return null;
        String host = text;
        int port = DEFAULT_PORT;
        int colon = text.lastIndexOf(':');
        if (colon > 0 && colon < text.length() - 1) {
            String portPart = text.substring(colon + 1);
            try {
                port = Integer.parseInt(portPart);
                host = text.substring(0, colon);
            } catch (NumberFormatException e) {
                // Not a port — treat whole string as host.
                host = text;
                port = DEFAULT_PORT;
            }
        }
        host = host.trim();
        if (host.isEmpty()) return null;
        if (port <= 0 || port > 65535) port = DEFAULT_PORT;
        return new Endpoint(host, port);
    }
}
