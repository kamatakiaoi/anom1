package com.anonymous.chat.ui.viewer;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.MediaController;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.anonymous.chat.databinding.ActivityLightboxBinding;
import com.bumptech.glide.Glide;

public class LightboxActivity extends AppCompatActivity {

    public static final String EXTRA_IMAGE_URL = "extra_image_url";
    public static final String EXTRA_VIDEO_URL = "extra_video_url";

    private ActivityLightboxBinding binding;
    private boolean isVideo = false;
    private int videoWidth = 0;
    private int videoHeight = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLightboxBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String imageUrl = getIntent().getStringExtra(EXTRA_IMAGE_URL);
        String videoUrl = getIntent().getStringExtra(EXTRA_VIDEO_URL);

        if (videoUrl != null && !videoUrl.isEmpty()) {
            isVideo = true;
            binding.ivLightboxImage.setVisibility(View.GONE);
            binding.vvLightboxVideo.setVisibility(View.VISIBLE);
            binding.pbLightboxLoading.setVisibility(View.VISIBLE);
            binding.btnLightboxRotate.setVisibility(View.VISIBLE);

            MediaController mediaController = new MediaController(this);
            mediaController.setAnchorView(binding.vvLightboxVideo);
            binding.vvLightboxVideo.setMediaController(mediaController);

            try {
                binding.vvLightboxVideo.setVideoURI(Uri.parse(videoUrl));
                binding.vvLightboxVideo.setOnPreparedListener(mp -> {
                    binding.pbLightboxLoading.setVisibility(View.GONE);
                    videoWidth = mp.getVideoWidth();
                    videoHeight = mp.getVideoHeight();
                    adjustVideoSize();
                    mp.start();
                    mediaController.show(3000);
                });
                binding.vvLightboxVideo.setOnErrorListener((mp, what, extra) -> {
                    binding.pbLightboxLoading.setVisibility(View.GONE);
                    Toast.makeText(LightboxActivity.this, "Cannot play video", Toast.LENGTH_SHORT).show();
                    return true;
                });
            } catch (Exception e) {
                binding.pbLightboxLoading.setVisibility(View.GONE);
                Toast.makeText(this, "Error loading video: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            binding.btnLightboxRotate.setOnClickListener(v -> toggleOrientation());
        } else if (imageUrl != null && !imageUrl.isEmpty()) {
            binding.ivLightboxImage.setVisibility(View.VISIBLE);
            binding.vvLightboxVideo.setVisibility(View.GONE);
            binding.btnLightboxRotate.setVisibility(View.VISIBLE);
            Glide.with(this).load(imageUrl).into(binding.ivLightboxImage);
            binding.btnLightboxRotate.setOnClickListener(v -> toggleOrientation());
        }

        binding.btnLightboxClose.setOnClickListener(v -> finish());
        applyOrientationState(getResources().getConfiguration().orientation);
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
                // Video is wider than screen: fit width, compute height
                lp.width = containerWidth;
                lp.height = (int) (containerWidth / videoAspect);
            } else {
                // Video is taller than screen: fit height, compute width
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
        if (isVideo && binding.vvLightboxVideo.isPlaying()) {
            binding.vvLightboxVideo.pause();
        }
    }
}
