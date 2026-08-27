package com.anonymous.chat.views;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anonymous.chat.databinding.BottomSheetMediaPickerBinding;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class MediaPickerBottomSheet extends BottomSheetDialogFragment {

    public interface OnMediaTypeSelectedListener {
        void onPickImages();
        void onPickVideo();
        void onPickAudio();
    }

    private BottomSheetMediaPickerBinding binding;
    private final OnMediaTypeSelectedListener listener;

    public MediaPickerBottomSheet(OnMediaTypeSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = BottomSheetMediaPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnPickImages.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onPickImages();
        });

        binding.btnPickVideo.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onPickVideo();
        });

        binding.btnPickAudio.setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onPickAudio();
        });
    }
}
