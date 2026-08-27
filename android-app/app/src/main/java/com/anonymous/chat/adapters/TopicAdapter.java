package com.anonymous.chat.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.models.Topic;

import java.util.ArrayList;
import java.util.List;

public class TopicAdapter extends RecyclerView.Adapter<TopicAdapter.TopicViewHolder> {

    public interface OnTopicClickListener {
        void onTopicClick(Topic topic);
    }

    private final List<Topic> allTopics = new ArrayList<>();
    private final List<Topic> topics = new ArrayList<>();
    private final OnTopicClickListener listener;

    public TopicAdapter(OnTopicClickListener listener) {
        this.listener = listener;
    }

    public void setTopics(List<Topic> newTopics) {
        allTopics.clear();
        topics.clear();
        if (newTopics != null) {
            allTopics.addAll(newTopics);
            topics.addAll(newTopics);
        }
        notifyDataSetChanged();
    }

    public void filter(String query) {
        topics.clear();
        if (query == null || query.trim().isEmpty()) {
            topics.addAll(allTopics);
        } else {
            String lower = query.trim().toLowerCase();
            for (Topic t : allTopics) {
                if (t.getName() != null && t.getName().toLowerCase().contains(lower)) {
                    topics.add(t);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_topic, parent, false);
        return new TopicViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {
        Topic topic = topics.get(position);
        holder.bind(topic, listener);
    }

    @Override
    public int getItemCount() {
        return topics.size();
    }

    public static class TopicViewHolder extends RecyclerView.ViewHolder {
        private final View cardView;
        private final TextView tvName;
        private final ImageView ivVerified;
        private final TextView tvRecommended;
        private final TextView tvOnlineCount;
        private final TextView tvMsgCount;
        private final TextView tvLastMsg;

        public TopicViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.topicCard);
            tvName = itemView.findViewById(R.id.tvTopicName);
            ivVerified = itemView.findViewById(R.id.ivTopicVerified);
            tvRecommended = itemView.findViewById(R.id.tvTopicRecommended);
            tvOnlineCount = itemView.findViewById(R.id.tvTopicOnline);
            tvMsgCount = itemView.findViewById(R.id.tvTopicMsgs);
            tvLastMsg = itemView.findViewById(R.id.tvTopicLastMsg);
        }

        public void bind(Topic topic, OnTopicClickListener listener) {
            tvName.setText(topic.getName());

            // Pinned system topic background
            if (topic.isSystem()) {
                cardView.setBackgroundResource(R.drawable.bg_card_pinned);
                ivVerified.setVisibility(View.VISIBLE);
            } else {
                cardView.setBackgroundResource(R.drawable.bg_card_topic);
                ivVerified.setVisibility(View.GONE);
            }

            // Recommended badge
            if (topic.isRecommended()) {
                tvRecommended.setVisibility(View.VISIBLE);
            } else {
                tvRecommended.setVisibility(View.GONE);
            }

            tvOnlineCount.setText(formatCount(topic.getOnline()));
            tvMsgCount.setText(formatCount(topic.getMsgCount()));

            // Last message preview
            if (topic.getLastMsg() != null && topic.getLastMsg().getName() != null && !topic.getLastMsg().getName().isEmpty()) {
                String sender = topic.getLastMsg().getName();
                String text = topic.getLastMsg().getText();
                tvLastMsg.setText(sender + (text != null && !text.isEmpty() ? ": " + text : ""));
            } else if ("Patch notes".equalsIgnoreCase(topic.getName())) {
                tvLastMsg.setText("Official changelog & updates");
            } else {
                tvLastMsg.setText("No messages yet");
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTopicClick(topic);
            });
        }

        private String formatCount(int n) {
            if (n < 1000) return String.valueOf(n);
            if (n < 1000000) {
                float k = n / 1000f;
                return (k == (int) k ? String.format("%d", (int) k) : String.format("%.1f", k)) + "K";
            }
            return String.format("%.1fM", n / 1000000f);
        }
    }
}
