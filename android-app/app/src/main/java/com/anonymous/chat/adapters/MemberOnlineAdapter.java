package com.anonymous.chat.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class MemberOnlineAdapter extends RecyclerView.Adapter<MemberOnlineAdapter.MemberViewHolder> {

    public interface OnMemberClickListener {
        void onMemberClick(UserProfile member);
    }

    private final List<UserProfile> members = new ArrayList<>();
    private final OnMemberClickListener listener;

    public MemberOnlineAdapter(OnMemberClickListener listener) {
        this.listener = listener;
    }

    public void setMembers(List<UserProfile> newMembers) {
        members.clear();
        if (newMembers != null) members.addAll(newMembers);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_online_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        UserProfile p = members.get(position);
        holder.bind(p, listener);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    public static class MemberViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivAvatar;
        private final TextView tvName;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivMemberAvatar);
            tvName = itemView.findViewById(R.id.tvMemberName);
        }

        public void bind(UserProfile user, OnMemberClickListener listener) {
            Context context = itemView.getContext();
            String serverBaseUrl = PreferenceManager.getInstance(context).getServerBaseUrl();

            tvName.setText(user.getName());
            GradientDrawable grad = ColorHelper.getAvatarGradient(user.getColor());
            ivAvatar.setBackground(grad);
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String fullUrl = ImageUtils.getFullMediaUrl(serverBaseUrl, user.getAvatar());
                Glide.with(context).load(fullUrl).circleCrop().into(ivAvatar);
            } else {
                ivAvatar.setImageDrawable(null);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMemberClick(user);
            });
        }
    }
}
