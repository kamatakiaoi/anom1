package com.anonymous.chat;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.services.ChatBackgroundService;
import com.anonymous.chat.utils.NotificationHelper;
import com.anonymous.chat.utils.SoundHelper;

public class AnonymousChatApp extends Application {
    private int activityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannels(this);
        SoundHelper.getInstance(this);
        com.anonymous.chat.utils.AudioPlayerManager.getInstance().init(this);
        SocketManager.getInstance().init(this);
        ChatBackgroundService.start(this);

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                activityCount++;
                if (activityCount == 1) {
                    SocketManager.getInstance().setAppForeground(true);
                }
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                activityCount = Math.max(0, activityCount - 1);
                if (activityCount == 0) {
                    SocketManager.getInstance().setAppForeground(false);
                }
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }
}
