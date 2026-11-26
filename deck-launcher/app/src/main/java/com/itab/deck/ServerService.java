package com.itab.deck;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

public class ServerService extends Service {
    public static final int PORT = 8080;
    private static final String CHANNEL_ID = "itab_server";
    private LocalHttpServer server;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, buildNotification());
        if (server == null || !server.isRunning()) {
            server = new LocalHttpServer(this, PORT, (action, body) -> LinuxController.perform(this, action));
            try {
                server.start();
            } catch (IOException e) {
                server.log("start failed: " + e.getMessage());
            }
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "ITAB Linux Server", NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setContentTitle("ITAB Linux Server")
                .setContentText("Dashboard: http://127.0.0.1:" + PORT)
                .setSmallIcon(R.drawable.ic_server)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
