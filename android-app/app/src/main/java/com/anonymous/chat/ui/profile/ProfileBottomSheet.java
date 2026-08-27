package com.anonymous.chat.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anonymous.chat.R;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.BottomSheetProfileBinding;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.ui.auth.AuthActivity;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class ProfileBottomSheet extends BottomSheetDialogFragment {

    private BottomSheetProfileBinding binding;
    private PreferenceManager prefs;
    private ActivityResultLauncher<Intent> avatarPickerLauncher;
    private String pendingAvatarBase64 = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            View bottomSheet = getDialog().findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        Uri imageUri = result.getData().getData();
                        String base64 = ImageUtils.uriToBase64(requireContext(), imageUri, 400, 85);
                        if (base64 != null) {
                            pendingAvatarBase64 = base64;
                            Glide.with(this).load(imageUri).circleCrop().into(binding.ivMyAvatar);
                        }
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = PreferenceManager.getInstance(requireContext());

        UserProfile myProfile = SocketManager.getInstance().getMyProfile();
        if (myProfile != null) {
            binding.etProfileName.setText(myProfile.getName());
            GradientDrawable grad = ColorHelper.getAvatarGradient(myProfile.getColor());
            binding.ivMyAvatar.setBackground(grad);

            if (myProfile.getAvatar() != null && !myProfile.getAvatar().isEmpty()) {
                String fullUrl = ImageUtils.getFullMediaUrl(prefs.getServerBaseUrl(), myProfile.getAvatar());
                Glide.with(this).load(fullUrl).circleCrop().into(binding.ivMyAvatar);
            }
        }

        // Avatar Click
        binding.btnChangeAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            avatarPickerLauncher.launch(Intent.createChooser(intent, "Select Avatar Image"));
        });

        // Timezone selection
        String currentTz = prefs.getTimezone();
        updateTimezoneUI(currentTz);

        binding.btnTzVn.setOnClickListener(v -> {
            prefs.setTimezone("vn");
            updateTimezoneUI("vn");
        });

        binding.btnTzUtc.setOnClickListener(v -> {
            prefs.setTimezone("utc");
            updateTimezoneUI("utc");
        });

        // Sound switch
        binding.switchSound.setChecked(prefs.isSoundEnabled());
        binding.switchSound.setOnCheckedChangeListener((btn, checked) -> prefs.setSoundEnabled(checked));

        // Save / Cancel
        binding.btnProfileCancel.setOnClickListener(v -> dismiss());
        binding.btnProfileSave.setOnClickListener(v -> {
            String newName = binding.etProfileName.getText().toString().trim();
            if (!newName.isEmpty()) {
                SocketManager.getInstance().changeName(newName);
            }

            if (pendingAvatarBase64 != null) {
                SocketManager.getInstance().changeAvatar(pendingAvatarBase64);
                pendingAvatarBase64 = null;
            }

            Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
            dismiss();
        });

        // Logout
        binding.btnProfileLogout.setOnClickListener(v -> {
            SocketManager.getInstance().logout();
            dismiss();
            Intent intent = new Intent(requireActivity(), AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void updateTimezoneUI(String tz) {
        boolean isVn = "vn".equalsIgnoreCase(tz);
        if (isVn) {
            binding.btnTzVn.setBackgroundResource(R.drawable.bg_btn_primary);
            binding.btnTzVn.setTextColor(Color.parseColor("#0A0A0A"));
            binding.btnTzUtc.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTzUtc.setTextColor(Color.parseColor("#888888"));
        } else {
            binding.btnTzUtc.setBackgroundResource(R.drawable.bg_btn_primary);
            binding.btnTzUtc.setTextColor(Color.parseColor("#0A0A0A"));
            binding.btnTzVn.setBackgroundColor(Color.TRANSPARENT);
            binding.btnTzVn.setTextColor(Color.parseColor("#888888"));
        }
    }
}
