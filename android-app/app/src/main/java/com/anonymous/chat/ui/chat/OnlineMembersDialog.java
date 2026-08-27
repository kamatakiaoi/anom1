package com.anonymous.chat.ui.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.anonymous.chat.adapters.MemberOnlineAdapter;
import com.anonymous.chat.databinding.DialogOnlineMembersBinding;
import com.anonymous.chat.models.UserProfile;

import java.util.List;

public class OnlineMembersDialog extends Dialog {

    private DialogOnlineMembersBinding binding;
    private final List<UserProfile> members;
    private final MemberOnlineAdapter.OnMemberClickListener listener;

    public OnlineMembersDialog(@NonNull Context context, List<UserProfile> members, MemberOnlineAdapter.OnMemberClickListener listener) {
        super(context);
        this.members = members;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        binding = DialogOnlineMembersBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.88f);
            getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        binding.tvOnlineDialogTitle.setText("In this topic (" + (members != null ? members.size() : 0) + ")");

        binding.rvOnlineMembers.setLayoutManager(new LinearLayoutManager(getContext()));
        MemberOnlineAdapter adapter = new MemberOnlineAdapter(member -> {
            dismiss();
            if (listener != null) listener.onMemberClick(member);
        });
        binding.rvOnlineMembers.setAdapter(adapter);
        adapter.setMembers(members);

        binding.btnOnlineClose.setOnClickListener(v -> dismiss());
    }
}
