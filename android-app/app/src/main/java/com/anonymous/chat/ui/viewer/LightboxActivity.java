package com.anonymous.chat.ui.viewer;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.anonymous.chat.R;
import com.anonymous.chat.databinding.ActivityLightboxBinding;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.VideoCacheManager;

import java.io.File;
import java.util.Locale;

public class LightboxActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";
    public static final String EXTRA_VIDEO_URL = "extra_video_url";
    public static final String EXTRA_VIDEO_POSITION = "extra_video_position";

    private ActivityLightboxBinding binding;
    private boolean isVideo = false;
    private int videoWidth = 0;
    private int videoHeight = 0;

    private MediaPlayer mediaPlayer = null;
    private int videoDurationMs = 0;
    private boolean isTracking = false;
    private boolean isSeeking = false;
    private int pendingSeekMs = -1;
    private int lastTargetSeekMs = -1;
    private long lastScrubSeekTime = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean controlsVisible = true;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVideo && binding.vvLightboxVideo.isPlaying() && !isTracking && !isSeeking) {
                updateProgressUI();
            }
            handler.postDelayed(this, 250);
        }
    };

    private final Runnable autoHideRunnable = this::hideControls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLightboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);
        int startPositionMs = getIntent().getIntExtra(EXTRA_VIDEO_POSITION, 0);

        if (videoUrl != null && !videoUrl.isEmpty()) {
            setupVideoMode(videoUrl, startPositionMs);
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            setupImageMode(imageUrl);
        }

        binding.btnLightboxClose.setOnClickListener(v -> finish());
        applyOrientationState(getResources().getConfiguration().orientation);
    }

    private void setupImageMode(String imageUrl) {
        isVideo = false;
        binding.ivLightboxImage.setVisibility(View.VISIBLE);
        binding.vvLightboxVideo.setVisibility(View.GONE);
        binding.layoutVideoControls.setVisibility(View.GONE);
        binding.ivVideoCenterPlay.setVisibility(View.GONE);
        binding.btnLightboxRotate.setVisibility(View.VISIBLE);
        binding.pbLightboxLoading.setVisibility(View.GONE);

        ImageUtils.loadFullImage(this, imageUrl, binding.ivLightboxImage);

        binding.ivLightboxImage.setOnSingleTapListener(v -> toggleControls());
        binding.btnLightboxRotate.setOnClickListener(v -> toggleOrientation());
    }

    private void setupVideoMode(String videoUrl, int startPositionMs) {
        isVideo = true;
        binding.ivLightboxImage.setVisibility(View.GONE);
        binding.vvLightboxVideo.setVisibility(View.VISIBLE);
        binding.layoutVideoControls.setVisibility(View.VISIBLE);
        binding.pbLightboxLoading.setVisibility(View.VISIBLE);
        binding.btnLightboxRotate.setVisibility(View.VISIBLE);

        setupVideoListeners(startPositionMs);
        setupVideoControls();

        // 1. Check disk cache first
        File cached = VideoCacheManager.getInstance().getCachedFile(this, videoUrl);
        if (cached != null) {
            try {
                binding.vvLightboxVideo.setVideoPath(cached.getAbsolutePath());
            } catch (Exception e) {
                fallbackStreamVideo(videoUrl);
            }
        } else {
            // 2. Instant Progressive HTTP Streaming - zero wait!
            fallbackStreamVideo(videoUrl);
        }

        binding.btnLightboxRotate.setOnClickListener(v -> toggleOrientation());
        binding.btnVideoFullscreen.setOnClickListener(v -> toggleOrientation());
    }

    private void fallbackStreamVideo(String videoUrl) {
        try {
            String serverUrl = PreferenceManager.getInstance(this).getServerBaseUrl();
            String full = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
            binding.vvLightboxVideo.setVideoURI(Uri.parse(full));
        } catch (Exception ex) {
            binding.pbLightboxLoading.setVisibility(View.GONE);
            Toast.makeText(this, "Cannot play video", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupVideoListeners(int startPositionMs) {
        binding.vvLightboxVideo.setOnPreparedListener(mp -> {
            if (isFinishing() || isDestroyed()) return;
            this.mediaPlayer = mp;
            binding.pbLightboxLoading.setVisibility(View.GONE);

            videoWidth = mp.getVideoWidth();
            videoHeight = mp.getVideoHeight();
            videoDurationMs = mp.getDuration();
            if (videoDurationMs < 0) videoDurationMs = 0;

            binding.tvVideoDuration.setText(formatTime(videoDurationMs));
            adjustVideoSize();

            mp.setOnInfoListener((player, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    binding.pbLightboxLoading.setVisibility(View.VISIBLE);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    binding.pbLightboxLoading.setVisibility(View.GONE);
                }
                return false;
            });

            mp.setOnSeekCompleteListener(player -> {
                isSeeking = false;
                if (pendingSeekMs >= 0) {
                    int next = pendingSeekMs;
                    pendingSeekMs = -1;
                    accurateSeekTo(next);
                } else {
                    updateProgressUI();
                }
            });

            if (startPositionMs > 0 && startPositionMs < videoDurationMs) {
                accurateSeekTo(startPositionMs);
            }

            mp.start();
            updatePlayPauseButtons(true);
            showControls();
            scheduleAutoHide();
            handler.post(progressRunnable);
        });

        binding.vvLightboxVideo.setOnCompletionListener(mp -> {
            updatePlayPauseButtons(false);
            binding.sbVideoProgress.setProgress(1000);
            binding.tvVideoCurrentTime.setText(formatTime(videoDurationMs));
            showControls();
            cancelAutoHide();
        });

        binding.vvLightboxVideo.setOnErrorListener((mp, what, extra) -> {
            binding.pbLightboxLoading.setVisibility(View.GONE);
            Toast.makeText(LightboxActivity.this, "Video playback error", Toast.LENGTH_SHORT).show();
            return true;
        });

        // Click on video surface or root toggles controls HUD
        View.OnClickListener clickListener = v -> toggleControls();
        binding.vvLightboxVideo.setOnClickListener(clickListener);
        binding.getRoot().setOnClickListener(clickListener);
    }

    private void setupVideoControls() {
        binding.btnVideoPlayPause.setOnClickListener(v -> togglePlayPause());
        binding.ivVideoCenterPlay.setOnClickListener(v -> togglePlayPause());

        binding.sbVideoProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && videoDurationMs > 0) {
                    int curMs = (int) (((long) progress * videoDurationMs) / 1000L);
                    binding.tvVideoCurrentTime.setText(formatTime(curMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isTracking = true;
                cancelAutoHide();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isTracking = false;
                if (videoDurationMs > 0) {
                    int finalMs = (int) (((long) seekBar.getProgress() * videoDurationMs) / 1000L);
                    accurateSeekTo(finalMs);
                }
                scheduleAutoHide();
            }
        });
    }

    private void accurateSeekTo(int targetMs) {
        if (targetMs < 0) targetMs = 0;
        if (videoDurationMs > 0 && targetMs > videoDurationMs) targetMs = videoDurationMs;

        if (isSeeking) {
            pendingSeekMs = targetMs;
            return;
        }

        isSeeking = true;
        lastTargetSeekMs = targetMs;

        if (mediaPlayer != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // SEEK_CLOSEST_SYNC guarantees immediate responsive seek on HTTP Range stream without frame drop
                    mediaPlayer.seekTo((long) targetMs, MediaPlayer.SEEK_CLOSEST_SYNC);
                } catch (Exception e) {
                    try {
                        mediaPlayer.seekTo(targetMs);
                    } catch (Exception ignored) {}
                }
            } else {
                mediaPlayer.seekTo(targetMs);
            }
        } else {
            binding.vvLightboxVideo.seekTo(targetMs);
        }

        final int capturedTarget = targetMs;
        handler.postDelayed(() -> {
            if (isSeeking && lastTargetSeekMs == capturedTarget) {
                isSeeking = false;
                updateProgressUI();
            }
        }, 1500);
    }

    private void updateProgressUI() {
        if (videoDurationMs <= 0 || isTracking) return;
        int currentPos;
        if (mediaPlayer != null) {
            try {
                currentPos = mediaPlayer.getCurrentPosition();
            } catch (Exception e) {
                currentPos = binding.vvLightboxVideo.getCurrentPosition();
            }
        } else {
            currentPos = binding.vvLightboxVideo.getCurrentPosition();
        }

        if (currentPos < 0) currentPos = 0;
        if (currentPos > videoDurationMs) currentPos = videoDurationMs;

        int progress = (int) (((long) currentPos * 1000L) / videoDurationMs);
        binding.sbVideoProgress.setProgress(progress);
        binding.tvVideoCurrentTime.setText(formatTime(currentPos));
    }

    private void togglePlayPause() {
        if (binding.vvLightboxVideo.isPlaying()) {
            binding.vvLightboxVideo.pause();
            updatePlayPauseButtons(false);
            cancelAutoHide();
            showCenterFlash(false);
        } else {
            binding.vvLightboxVideo.start();
            updatePlayPauseButtons(true);
            scheduleAutoHide();
            showCenterFlash(true);
        }
    }

    private void updatePlayPauseButtons(boolean isPlaying) {
        binding.btnVideoPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        binding.ivVideoCenterPlay.setImageResource(isPlaying ? R.drawable.ic_play : R.drawable.ic_pause);
    }

    private void showCenterFlash(boolean isPlay) {
        binding.ivVideoCenterPlay.setImageResource(isPlay ? R.drawable.ic_play : R.drawable.ic_pause);
        binding.ivVideoCenterPlay.setAlpha(1.0f);
        binding.ivVideoCenterPlay.setVisibility(View.VISIBLE);
        binding.ivVideoCenterPlay.animate()
                .alpha(0f)
                .setDuration(500)
                .withEndAction(() -> {
                    if (binding.vvLightboxVideo.isPlaying()) {
                        binding.ivVideoCenterPlay.setVisibility(View.GONE);
                    } else {
                        binding.ivVideoCenterPlay.setAlpha(0.8f);
                        binding.ivVideoCenterPlay.setVisibility(View.VISIBLE);
                        binding.ivVideoCenterPlay.setImageResource(R.drawable.ic_play);
                    }
                })
                .start();
    }

    private void toggleControls() {
        if (controlsVisible) {
            hideControls();
        } else {
            showControls();
            scheduleAutoHide();
        }
    }

    private void showControls() {
        controlsVisible = true;
        binding.layoutTopControls.setVisibility(View.VISIBLE);
        if (isVideo) {
            binding.layoutVideoControls.setVisibility(View.VISIBLE);
        }
    }

    private void hideControls() {
        if (isVideo && !binding.vvLightboxVideo.isPlaying()) {
            // Keep controls visible while paused for convenient resuming
            return;
        }
        controlsVisible = false;
        binding.layoutTopControls.setVisibility(View.GONE);
        if (isVideo) {
            binding.layoutVideoControls.setVisibility(View.GONE);
        }
    }

    private void scheduleAutoHide() {
        cancelAutoHide();
        handler.postDelayed(autoHideRunnable, 3500);
    }

    private void cancelAutoHide() {
        handler.removeCallbacks(autoHideRunnable);
    }

    private String formatTime(int ms) {
        if (ms < 0) ms = 0;
        int totalSeconds = ms / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    private void adjustVideoSize() {
        if (!isVideo || videoWidth <= 0 || videoHeight <= 0) return;
        binding.getRoot().post(() -> {
            int containerWidth = binding.getRoot().getWidth();
            int containerHeight = binding.getRoot().getHeight();
            if (containerWidth <= 0 || containerHeight <= 0) return;

            float videoAspect = (float) videoWidth / (float) videoHeight;
            float containerAspect = (float) containerWidth / (float) containerHeight;

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) binding.vvLightboxVideo.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }

            if (videoAspect > containerAspect) {
                lp.width = containerWidth;
                lp.height = (int) (containerWidth / videoAspect);
            } else {
                lp.height = containerHeight;
                lp.width = (int) (containerHeight * videoAspect);
            }
            lp.gravity = Gravity.CENTER;
            binding.vvLightboxVideo.setLayoutParams(lp);
        });
    }

    private void toggleOrientation() {
        int currentOrientation = getResources().getConfiguration().orientation;
        if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationState(newConfig.orientation);
        adjustVideoSize();
        if (!isVideo) {
            binding.ivLightboxImage.resetZoom();
        }
    }

    private void applyOrientationState(int orientation) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cancelAutoHide();
        handler.removeCallbacks(progressRunnable);
        if (isVideo && binding.vvLightboxVideo.isPlaying()) {
            binding.vvLightboxVideo.pause();
            updatePlayPauseButtons(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelAutoHide();
        handler.removeCallbacksAndMessages(null);
        if (isVideo) {
            try {
                binding.vvLightboxVideo.stopPlayback();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
    }
}
