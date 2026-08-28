package com.anonymous.chat;

import android.app.Application;

import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.services.ChatBackgroundService;
import com.anonymous.chat.utils.NotificationHelper;
import com.anonymous.chat.utils.SoundHelper;

public class AnonymousChatApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createNotificationChannels(this);
        SoundHelper.getInstance(this);
        com.anonymous.chat.utils.AudioPlayerManager.getInstance().init(this);
        SocketManager.getInstance().init(this);
        ChatBackgroundService.start(this);
    }
}
