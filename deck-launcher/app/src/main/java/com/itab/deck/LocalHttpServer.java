package com.itab.deck;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.Formatter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalHttpServer {
    public interface ActionHandler {
        String onAction(String action, String body) throws Exception;
    }

    private final Context context;
    private final int port;
    private final ActionHandler actionHandler;
    private final LinkedList<String> logs = new LinkedList<>();
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private Thread acceptThread;
    private volatile boolean running;

    public LocalHttpServer(Context context, int port, ActionHandler actionHandler) {
        this.context = context.getApplicationContext();
        this.port = port;
        this.actionHandler = actionHandler;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        serverSocket = new ServerSocket(port);
        pool = Executors.newCachedThreadPool();
        running = true;
        acceptThread = new Thread(this::acceptLoop, "itab-http-accept");
        acceptThread.start();
        log("HTTP dashboard listening on port " + port);
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {
        }
        if (pool != null) pool.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(() -> handleSocket(socket));
            } catch (IOException e) {
                if (running) log("Accept failed: " + e.getMessage());
            }
        }
    }

    private void handleSocket(Socket socket) {
        try {
            socket.setSoTimeout(8000);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            ByteArrayOutputStream headBytes = new ByteArrayOutputStream();
            int b;
            int matched = 0;
            byte[] end = new byte[]{'\r', '\n', '\r', '\n'};
            while ((b = in.read()) != -1) {
                headBytes.write(b);
                if (b == end[matched]) {
                    matched++;
                    if (matched == end.length) break;
                } else {
                    matched = b == end[0] ? 1 : 0;
                }
                if (headBytes.size() > 32768) {
                    writeResponse(out, 431, "text/plain; charset=utf-8", "request header too large");
                    return;
                }
            }

            String head = new String(headBytes.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = head.split("\r\n");
            if (lines.length == 0 || lines[0].trim().isEmpty()) return;
            String[] requestParts = lines[0].split(" ");
            if (requestParts.length < 2) return;

            String method = requestParts[0].toUpperCase(Locale.US);
            String path = requestParts[1];
            int queryIndex = path.indexOf('?');
            if (queryIndex >= 0) path = path.substring(0, queryIndex);

            int contentLength = 0;
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim();
                    String value = line.substring(colon + 1).trim();
                    if (name.equalsIgnoreCase("content-length")) {
                        try {
                            contentLength = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            byte[] bodyBytes = new byte[Math.min(contentLength, 1024 * 1024)];
            int read = 0;
            while (read < bodyBytes.length) {
                int n = in.read(bodyBytes, read, bodyBytes.length - read);
                if (n < 0) break;
                read += n;
            }
            String body = new String(bodyBytes, 0, read, StandardCharsets.UTF_8);

            route(method, path, body, out);
        } catch (Exception e) {
            try {
                writeResponse(socket.getOutputStream(), 500, "application/json; charset=utf-8", errorJson(e.getMessage()));
            } catch (Exception ignored) {
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void route(String method, String path, String body, OutputStream out) throws Exception {
        if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
            writeResponse(out, 200, "text/html; charset=utf-8", readAsset("dashboard/index.html"));
        } else if ("GET".equals(method) && "/health".equals(path)) {
            writeResponse(out, 200, "application/json; charset=utf-8", "{\"ok\":true}");
        } else if ("GET".equals(method) && "/api/status".equals(path)) {
            writeResponse(out, 200, "application/json; charset=utf-8", statusJson().toString());
        } else if ("GET".equals(method) && "/api/logs".equals(path)) {
            writeResponse(out, 200, "application/json; charset=utf-8", logsJson().toString());
        } else if ("POST".equals(method) && "/api/action".equals(path)) {
            JSONObject request = new JSONObject(body.isEmpty() ? "{}" : body);
            String action = request.optString("action", "");
            String result = actionHandler == null ? "No action handler" : actionHandler.onAction(action, body);
            log(action + " → " + result);
            JSONObject response = new JSONObject();
            response.put("ok", true);
            response.put("action", action);
            response.put("result", result);
            writeResponse(out, 200, "application/json; charset=utf-8", response.toString());
        } else {
            writeResponse(out, 404, "application/json; charset=utf-8", "{\"error\":\"not found\"}");
        }
    }

    private JSONObject statusJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("ok", true);
        root.put("server", "ITAB embedded control plane");
        root.put("port", port);
        root.put("model", Build.MODEL);
        root.put("manufacturer", Build.MANUFACTURER);
        root.put("android", Build.VERSION.RELEASE);
        root.put("sdk", Build.VERSION.SDK_INT);
        root.put("uptimeSeconds", SystemClock.elapsedRealtime() / 1000);

        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        if (am != null) {
            am.getMemoryInfo(memoryInfo);
            root.put("memoryTotalBytes", memoryInfo.totalMem);
            root.put("memoryAvailableBytes", memoryInfo.availMem);
            root.put("memoryLow", memoryInfo.lowMemory);
        }

        StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
        root.put("storageTotalBytes", statFs.getTotalBytes());
        root.put("storageAvailableBytes", statFs.getAvailableBytes());

        JSONArray ips = new JSONArray();
        String lanIp = null;
        for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!iface.isUp() || iface.isLoopback()) continue;
            for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                if (!address.isLoopbackAddress() && address instanceof Inet4Address) {
                    String host = address.getHostAddress();
                    ips.put(host);
                    if (lanIp == null) lanIp = host;
                }
            }
        }
        root.put("ips", ips);

        JSONObject services = new JSONObject();
        boolean sshOpen = portOpen(8022);
        boolean linuxOpen = portOpen(8081);
        boolean terminalOpen = portOpen(7681);
        services.put("dashboard", "running");
        services.put("linuxUserland", linuxOpen ? "running" : LinuxController.getState(context));
        services.put("termux", LinuxController.isTermuxInstalled(context) ? "installed" : "missing");
        services.put("ssh", sshOpen ? "running:8022" : "stopped");
        services.put("linuxHttp", linuxOpen ? "running:8081" : "stopped");
        services.put("terminal", terminalOpen ? "running:7681" : "stopped");
        root.put("services", services);

        if (lanIp != null) {
            JSONObject access = new JSONObject();
            access.put("ip", lanIp);
            access.put("ssh", "ssh -p 8022 u0_a176@" + lanIp);
            access.put("dashboard", "http://" + lanIp + ":" + port);
            access.put("linuxHttp", "http://" + lanIp + ":8081");
            root.put("access", access);
        } else {
            root.put("access", JSONObject.NULL);
        }
        return root;
    }

    private boolean portOpen(int targetPort) {
        try {
            Socket socket = new Socket();
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", targetPort), 250);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject logsJson() throws Exception {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        synchronized (logs) {
            for (String line : logs) array.put(line);
        }
        root.put("lines", array);
        return root;
    }

    private String readAsset(String name) throws IOException {
        InputStream in = context.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private void writeResponse(OutputStream out, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 404 ? "Not Found" : status == 431 ? "Request Header Fields Too Large" : "Internal Server Error";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.flush();
    }

    private String errorJson(String message) {
        try {
            JSONObject object = new JSONObject();
            object.put("error", message == null ? "unknown" : message);
            return object.toString();
        } catch (Exception e) {
            return "{\"error\":\"unknown\"}";
        }
    }

    public void log(String line) {
        synchronized (logs) {
            logs.addFirst(System.currentTimeMillis() + "  " + line);
            while (logs.size() > 120) logs.removeLast();
        }
    }
}
