package com.anonymous.chat.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import com.anonymous.chat.R;

public class SoundHelper {
    private static SoundHelper instance;
    private final SoundPool soundPool;
    private final int popSoundId;
    private final int clickSoundId;

    private SoundHelper(Context context) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(audioAttributes)
                .build();

        popSoundId = soundPool.load(context, R.raw.pop, 1);
        clickSoundId = soundPool.load(context, R.raw.click, 1);
    }

    public static synchronized SoundHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SoundHelper(context.getApplicationContext());
        }
        return instance;
    }

    public static void playPop(Context context) {
        if (context == null) return;
        getInstance(context).playPopSound(context);
    }

    public static void playClick(Context context) {
        if (context == null) return;
        getInstance(context).playClickSound(context);
    }

    private void playPopSound(Context context) {
        if (PreferenceManager.getInstance(context).isSoundEnabled()) {
            soundPool.play(popSoundId, 0.9f, 0.9f, 1, 0, 1.0f);
        }
    }

    private void playClickSound(Context context) {
        if (PreferenceManager.getInstance(context).isSoundEnabled()) {
            soundPool.play(clickSoundId, 0.6f, 0.6f, 1, 0, 1.0f);
        }
    }
}
