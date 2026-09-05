package com.anonymous.chat.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.anonymous.chat.R;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.ui.chat.ChatActivity;

public class NotificationHelper {
    public static final String CHANNEL_GENERAL = "channel_general_chat_v4";
    private static int notifCounter = 1000;

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_GENERAL,
                    context.getString(R.string.channel_general_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(context.getString(R.string.channel_general_desc));
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                    .build();
            channel.setSound(defaultSoundUri, audioAttributes);
            channel.setVibrationPattern(new long[]{0, 200, 100, 200});

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showGeneralMessageNotification(Context context, Message message) {
        if (context == null || message == null) return;

        String senderName = message.getName() != null ? message.getName() : "Anon";
        String content = message.getText();
        if (content == null || content.isEmpty()) {
            if (message.getImages() != null && !message.getImages().isEmpty()) {
                content = "[Photo]";
            } else if (message.getVideo() != null) {
                content = "[Video]";
            } else if (message.getAudio() != null) {
                content = "[Audio Clip]";
            } else {
                content = "[Message]";
            }
        }

        showNotification(context, senderName, content);
    }

    public static void showGeneralTopicNotification(Context context, String senderName, String text) {
        if (context == null) return;
        showNotification(context, senderName != null ? senderName : "Anon", text != null ? text : "[New message]");
    }

    private static void showNotification(Context context, String sender, String body) {
        createNotificationChannels(context);

        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_TOPIC_NAME, "General");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int uniqueReqId = (int) System.currentTimeMillis();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                uniqueReqId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        int notifId = notifCounter++;
        if (notifCounter > 9999) notifCounter = 1000;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_GENERAL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(sender + " in General")
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setSound(defaultSoundUri)
                .setVibrate(new long[]{0, 200, 100, 200})
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent);

        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anom:notification_wake");
                wl.acquire(3000);
            }
        } catch (Exception ignored) {}

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            manager.notify(notifId, builder.build());
        } catch (SecurityException ignored) {
            // Missing POST_NOTIFICATIONS permission
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
