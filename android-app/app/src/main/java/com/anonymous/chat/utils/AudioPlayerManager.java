package com.anonymous.chat.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class AudioPlayerManager {
    private static AudioPlayerManager instance;
    private ExoPlayer mediaPlayer;
    private String currentPlayingUrl = null;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    private final List<OnAudioStateChangeListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private Context appContext;

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

    public void init(Context context) {
        if (context != null) {
            this.appContext = context.getApplicationContext();
        }
    }

    public void addListener(OnAudioStateChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(OnAudioStateChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void setListener(OnAudioStateChangeListener listener) {
        listeners.clear();
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void dispatchPlay(String url) {
        for (OnAudioStateChangeListener l : listeners) {
            try { l.onPlay(url); } catch (Exception ignored) {}
        }
    }

    private void dispatchPause(String url) {
        for (OnAudioStateChangeListener l : listeners) {
            try { l.onPause(url); } catch (Exception ignored) {}
        }
    }

    private void dispatchStop(String url) {
        for (OnAudioStateChangeListener l : listeners) {
            try { l.onStop(url); } catch (Exception ignored) {}
        }
    }

    private void dispatchProgress(String url, int cur, int dur) {
        for (OnAudioStateChangeListener l : listeners) {
            try { l.onProgress(url, cur, dur); } catch (Exception ignored) {}
        }
    }

    private void dispatchError(String url, String err) {
        for (OnAudioStateChangeListener l : listeners) {
            try { l.onError(url, err); } catch (Exception ignored) {}
        }
    }

    public String getCurrentPlayingUrl() {
        return currentPlayingUrl;
    }

    public boolean isPlaying(String url) {
        return mediaPlayer != null && mediaPlayer.isPlaying() && url != null && url.equals(currentPlayingUrl);
    }

    public void playOrPause(String url) {
        if (url == null || url.isEmpty()) return;
        if (appContext == null) return;

        if (isPlaying(url)) {
            pause();
            return;
        }

        if (mediaPlayer != null && url.equals(currentPlayingUrl)) {
            mediaPlayer.play();
            startProgressUpdates();
            dispatchPlay(url);
            return;
        }

        stop();

        try {
            mediaPlayer = new ExoPlayer.Builder(appContext).build();
            
            String playUrl = url;
            if (url.startsWith("data:audio/")) {
                File temp = ImageUtils.saveBase64ToCacheFile(appContext, url, "audio_", ".mp3");
                if (temp != null) {
                    playUrl = temp.getAbsolutePath();
                } else {
                    throw new IOException("Cannot decode audio data");
                }
            } else {
                if (!playUrl.startsWith("http://") && !playUrl.startsWith("https://")) {
                    String serverUrl = PreferenceManager.getInstance(appContext).getServerBaseUrl();
                    playUrl = ImageUtils.getFullMediaUrl(serverUrl, playUrl);
                }
            }
            
            currentPlayingUrl = url;
            MediaItem mediaItem = MediaItem.fromUri(playUrl);
            mediaPlayer.setMediaItem(mediaItem);

            mediaPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (playbackState == Player.STATE_READY && mediaPlayer.getPlayWhenReady()) {
                        startProgressUpdates();
                        dispatchPlay(url);
                    } else if (playbackState == Player.STATE_ENDED) {
                        stopProgressUpdates();
                        String finishedUrl = currentPlayingUrl;
                        currentPlayingUrl = null;
                        dispatchStop(finishedUrl);
                    }
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        startProgressUpdates();
                        dispatchPlay(url);
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    stopProgressUpdates();
                    String errUrl = currentPlayingUrl;
                    currentPlayingUrl = null;
                    dispatchError(errUrl, "Playback error: " + error.getMessage());
                }
            });

            mediaPlayer.prepare();
            mediaPlayer.play();
        } catch (Exception e) {
            currentPlayingUrl = null;
            dispatchError(url, e.getMessage());
        }
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            stopProgressUpdates();
            if (currentPlayingUrl != null) {
                dispatchPause(currentPlayingUrl);
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
                long dur = mediaPlayer.getDuration();
                return dur == androidx.media3.common.C.TIME_UNSET ? 0 : (int) dur;
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return (int) mediaPlayer.getCurrentPosition();
            } catch (Exception ignored) {}
        }
        return 0;
    }

    public void stop() {
        stopProgressUpdates();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        if (currentPlayingUrl != null) {
            dispatchStop(currentPlayingUrl);
        }
        currentPlayingUrl = null;
    }

    private void startProgressUpdates() {
        stopProgressUpdates();
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int cur = getCurrentPosition();
                    int dur = getDuration();
                    if (currentPlayingUrl != null) {
                        dispatchProgress(currentPlayingUrl, cur, dur);
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
