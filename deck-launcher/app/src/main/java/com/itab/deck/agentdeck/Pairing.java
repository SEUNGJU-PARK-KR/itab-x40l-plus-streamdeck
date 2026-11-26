package com.itab.deck.agentdeck;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Pairing-code redemption against the daemon's POST /pair endpoint.
 *
 * Request:  POST http://host:port/pair  {"code":"123456","name":"ITAB Tablet","kind":"android"}
 * Success:  {"token": "<32hex>", "port": 9120}
 * Failure:  {"error": "<outcome>", "attemptsRemaining": n} with a 4xx status
 *
 * The returned token is handed back through the callback only — it is never
 * logged or written into any message string.
 */
public class Pairing {

    public interface Callback {
        /**
         * @param ok     redemption accepted
         * @param token  pairing token (only when ok)
         * @param port   daemon port echoed by the server (only when ok)
         * @param error  daemon outcome / local error description (only when !ok)
         */
        void onPairResult(boolean ok, String token, int port, String error);
    }

    public static void pair(final String host, final int port, final String code, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    URL url = new URL("http://" + host + ":" + port + "/pair");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    JSONObject body = new JSONObject();
                    body.put("code", code);
                    body.put("name", "ITAB Tablet");
                    body.put("kind", "android");
                    byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream out = conn.getOutputStream();
                    out.write(bytes);
                    out.close();

                    int status = conn.getResponseCode();
                    InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
                    String text = read(stream);
                    JSONObject resp = null;
                    try {
                        resp = new JSONObject(text);
                    } catch (Exception ignored) {
                    }

                    if (status >= 200 && status < 300 && resp != null && resp.has("token")) {
                        final String token = resp.optString("token", "");
                        final int respPort = resp.optInt("port", port);
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onPairResult(true, token, respPort, null);
                            }
                        });
                    } else {
                        String error = "HTTP " + status;
                        if (resp != null) {
                            error = resp.optString("error", error);
                            int remaining = resp.optInt("attemptsRemaining", -1);
                            if (remaining >= 0) error += " (" + remaining + "회 남음)";
                        }
                        final String err = error;
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onPairResult(false, null, port, err);
                            }
                        });
                    }
                } catch (final Exception e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onPairResult(false, null, port, String.valueOf(e.getMessage()));
                        }
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }, "agentdeck-pair").start();
    }

    private static String read(InputStream stream) {
        if (stream == null) return "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[512];
            int n;
            while ((n = reader.read(buf)) != -1 && sb.length() < 8192) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
