package com.anonymous.chat.ui.profile;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.anonymous.chat.databinding.DialogUserProfileBinding;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.bumptech.glide.Glide;

public class UserProfileDialog extends Dialog {

    private DialogUserProfileBinding binding;
    private final UserProfile userProfile;

    public UserProfileDialog(@NonNull Context context, UserProfile profile) {
        super(context);
        this.userProfile = profile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        binding = DialogUserProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88f);
            getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        if (userProfile != null) {
            binding.tvUserProfName.setText(userProfile.getName());
            binding.tvUserStatMessages.setText(String.valueOf(userProfile.getMessages()));
            binding.tvUserStatMedia.setText(String.valueOf(userProfile.getMedia()));
            binding.tvUserStatDisk.setText(userProfile.getDisk() != null ? userProfile.getDisk() : "0 B");

            GradientDrawable grad = ColorHelper.getAvatarGradient(userProfile.getColor());
            binding.ivUserProfAvatar.setBackground(grad);

            if (userProfile.getAvatar() != null && !userProfile.getAvatar().isEmpty()) {
                String serverUrl = PreferenceManager.getInstance(getContext()).getServerBaseUrl();
                String fullUrl = ImageUtils.getFullMediaUrl(serverUrl, userProfile.getAvatar());
                Glide.with(getContext()).load(fullUrl).circleCrop().into(binding.ivUserProfAvatar);
            }
        }

        binding.btnUserProfClose.setOnClickListener(v -> dismiss());
    }
}
