package com.anonymous.chat.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.anonymous.chat.R;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.utils.PreferenceManager;

public class ChatBackgroundService extends Service {

    public static final String CHANNEL_SERVICE = "channel_chat_service";
    private static final int NOTIF_SERVICE_ID = 2001;

    public static void start(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, ChatBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, ChatBackgroundService.class);
            context.stopService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createServiceNotificationChannel();

        Notification notif = buildServiceNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_SERVICE_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_SERVICE_ID, notif);
        }

        // Ensure socket is initialized and connected
        SocketManager sm = SocketManager.getInstance();
        sm.init(getApplicationContext());
        PreferenceManager prefs = PreferenceManager.getInstance(this);
        String serverUrl = prefs.getServerBaseUrl();
        if (serverUrl != null && !serverUrl.isEmpty()) {
            sm.connect(serverUrl);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        PreferenceManager prefs = PreferenceManager.getInstance(this);
        if (prefs.getAuthKey() != null && !prefs.getAuthKey().isEmpty()) {
            try {
                Intent restartServiceIntent = new Intent(getApplicationContext(), ChatBackgroundService.class);
                restartServiceIntent.setPackage(getPackageName());
                int flags = PendingIntent.FLAG_ONE_SHOT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent restartPendingIntent = PendingIntent.getService(
                        getApplicationContext(), 1001, restartServiceIntent, flags);
                AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME,
                            SystemClock.elapsedRealtime() + 1000,
                            restartPendingIntent);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager prefs = PreferenceManager.getInstance(this);
        if (prefs.getAuthKey() != null && !prefs.getAuthKey().isEmpty()) {
            try {
                Intent restartServiceIntent = new Intent(getApplicationContext(), ChatBackgroundService.class);
                restartServiceIntent.setPackage(getPackageName());
                int flags = PendingIntent.FLAG_ONE_SHOT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent restartPendingIntent = PendingIntent.getService(
                        getApplicationContext(), 1002, restartServiceIntent, flags);
                AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    alarmManager.set(
                            AlarmManager.ELAPSED_REALTIME,
                            SystemClock.elapsedRealtime() + 1500,
                            restartPendingIntent);
                }
            } catch (Exception ignored) {}
        }
    }

    private void createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_SERVICE,
                    "Chat Background Service",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Keeps connection alive for background notifications");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildServiceNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_SERVICE)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Anonymous Chat")
                .setContentText("Connected in background")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}
