package com.anonymous.chat.utils;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

public class AudioPlayerManager {
    private static AudioPlayerManager instance;
    private MediaPlayer mediaPlayer;
    private String currentPlayingUrl = null;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private OnAudioStateChangeListener listener;

    public interface OnAudioStateChangeListener {
        void onPlay(String url);
        void onPause(String url);
        void onStop(String url);
        void onProgress(String url, int currentPositionMs, int durationMs);
        void onError(String url, String error);
    }

    private AudioPlayerManager() {}

    public static synchronized AudioPlayerManager getInstance() {
        if (instance == null) {
            instance = new AudioPlayerManager();
        }
        return instance;
    }

    public void setListener(OnAudioStateChangeListener listener) {
        this.listener = listener;
    }

    public String getCurrentPlayingUrl() {
        return currentPlayingUrl;
    }

    public boolean isPlaying(String url) {
        return mediaPlayer != null && mediaPlayer.isPlaying() && url != null && url.equals(currentPlayingUrl);
    }

    private android.content.Context appContext;

    public void init(android.content.Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
    }

    public void playOrPause(String url) {
        if (url == null || url.isEmpty()) return;

        if (isPlaying(url)) {
            pause();
            return;
        }

        if (mediaPlayer != null && url.equals(currentPlayingUrl)) {
            mediaPlayer.start();
            startProgressUpdates();
            if (listener != null) listener.onPlay(url);
            return;
        }

        stop();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );

            if (url.startsWith("data:audio/")) {
                if (appContext != null) {
                    java.io.File temp = ImageUtils.saveBase64ToCacheFile(appContext, url, "audio_", ".mp3");
                    if (temp != null) {
                        mediaPlayer.setDataSource(temp.getAbsolutePath());
                    } else {
                        throw new java.io.IOException("Cannot decode audio data");
                    }
                } else {
                    throw new java.io.IOException("Context not initialized");
                }
            } else {
                mediaPlayer.setDataSource(url);
            }
            currentPlayingUrl = url;

            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                startProgressUpdates();
                if (listener != null) listener.onPlay(url);
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                stopProgressUpdates();
                String finishedUrl = currentPlayingUrl;
                currentPlayingUrl = null;
                if (listener != null) listener.onStop(finishedUrl);
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                stopProgressUpdates();
                String errUrl = currentPlayingUrl;
                currentPlayingUrl = null;
                if (listener != null) listener.onError(errUrl, "Playback error: " + what);
                return true;
            });

            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            currentPlayingUrl = null;
            if (listener != null) listener.onError(url, e.getMessage());
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopProgressUpdates();
            if (listener != null && currentPlayingUrl != null) {
                listener.onPause(currentPlayingUrl);
            }
        }
    }

    public void seekTo(int positionMs) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.seekTo(positionMs);
            } catch (Exception ignored) {}
        }
    }

    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public void stop() {
        stopProgressUpdates();
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (listener != null && currentPlayingUrl != null) {
            listener.onStop(currentPlayingUrl);
        }
        currentPlayingUrl = null;
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int cur = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    if (listener != null && currentPlayingUrl != null) {
                        listener.onProgress(currentPlayingUrl, cur, dur);
                    }
                    progressHandler.postDelayed(this, 300);
                }
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void stopProgressUpdates() {
        if (progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressRunnable = null;
        }
    }
}
