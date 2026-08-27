package com.anonymous.chat.views;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.anonymous.chat.R;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.SoundHelper;
import com.bumptech.glide.Glide;

public class InAppNotificationBanner {

    public interface OnBannerClickListener {
        void onBannerClick(Message message);
    }

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static View currentBannerView = null;
    private static Runnable dismissRunnable = null;

    public static void show(Activity activity, Message message, OnBannerClickListener listener) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || message == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            hideCurrentBanner();

            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            View banner = LayoutInflater.from(activity).inflate(R.layout.view_messenger_banner, decorView, false);

            ImageView ivAvatar = banner.findViewById(R.id.ivBannerAvatar);
            TextView tvSender = banner.findViewById(R.id.tvBannerSender);
            TextView tvMessage = banner.findViewById(R.id.tvBannerMessage);

            tvSender.setText(message.getName());
            String body = message.getText();
            if (body == null || body.isEmpty()) {
                body = !message.getImages().isEmpty() ? "[Photo]" : "[Message]";
            }
            tvMessage.setText(body);

            // Avatar & Gradient
            GradientDrawable grad = ColorHelper.getAvatarGradient(message.getColor());
            ivAvatar.setBackground(grad);
            if (message.getAvatar() != null && !message.getAvatar().isEmpty()) {
                String serverUrl = PreferenceManager.getInstance(activity).getServerBaseUrl();
                String fullUrl = ImageUtils.getFullMediaUrl(serverUrl, message.getAvatar());
                Glide.with(activity).load(fullUrl).circleCrop().into(ivAvatar);
            }

            banner.setOnClickListener(v -> {
                hideCurrentBanner();
                if (listener != null) listener.onBannerClick(message);
            });

            // Layout Params for heads-up top position
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = (int) (36 * activity.getResources().getDisplayMetrics().density);
            banner.setLayoutParams(params);

            // Audio notification
            SoundHelper.getInstance(activity).playPop(activity);

            // Animated slide-down with overshoot bounce
            banner.setTranslationY(-300f);
            banner.setAlpha(0f);
            decorView.addView(banner);
            currentBannerView = banner;

            banner.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();

            // Auto dismiss after 4.5 seconds
            dismissRunnable = () -> hideCurrentBanner();
            mainHandler.postDelayed(dismissRunnable, 4500);
        });
    }

    public static void hideCurrentBanner() {
        if (dismissRunnable != null) {
            mainHandler.removeCallbacks(dismissRunnable);
            dismissRunnable = null;
        }
        if (currentBannerView != null) {
            final View viewToDismiss = currentBannerView;
            currentBannerView = null;
            viewToDismiss.animate()
                    .translationY(-300f)
                    .alpha(0f)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (viewToDismiss.getParent() instanceof ViewGroup) {
                            ((ViewGroup) viewToDismiss.getParent()).removeView(viewToDismiss);
                        }
                    })
                    .start();
        }
    }
}
