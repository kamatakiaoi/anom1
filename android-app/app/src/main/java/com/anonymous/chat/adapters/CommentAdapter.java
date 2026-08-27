package com.anonymous.chat.adapters;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.models.Comment;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.TimeUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    public interface OnCommentReplyListener {
        void onReply(Comment comment);
    }

    private final List<Comment> comments = new ArrayList<>();
    private final OnCommentReplyListener listener;

    public CommentAdapter(OnCommentReplyListener listener) {
        this.listener = listener;
    }

    public void setComments(List<Comment> newComments) {
        comments.clear();
        if (newComments != null) comments.addAll(newComments);
        notifyDataSetChanged();
    }

    public void addComment(Comment comment) {
        if (comment == null) return;
        comments.add(comment);
        notifyItemInserted(comments.size() - 1);
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment c = comments.get(position);
        holder.bind(c, listener);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public static class CommentViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;
        private final ImageView ivAvatar;
        private final TextView tvAuthor;
        private final TextView tvReplyTo;
        private final TextView tvBody;
        private final TextView tvTime;
        private final TextView btnReply;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.commentRoot);
            ivAvatar = itemView.findViewById(R.id.ivCommentAvatar);
            tvAuthor = itemView.findViewById(R.id.tvCommentAuthor);
            tvReplyTo = itemView.findViewById(R.id.tvCommentReplyTo);
            tvBody = itemView.findViewById(R.id.tvCommentText);
            tvTime = itemView.findViewById(R.id.tvCommentTime);
            btnReply = itemView.findViewById(R.id.btnReplyComment);
        }

        public void bind(Comment comment, OnCommentReplyListener listener) {
            Context context = itemView.getContext();
            String serverBaseUrl = PreferenceManager.getInstance(context).getServerBaseUrl();

            // Nested indent if reply
            if (comment.getParentId() != null) {
                int padLeft = (int) (28 * context.getResources().getDisplayMetrics().density);
                root.setPadding(padLeft, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
            } else {
                int padLeft = (int) (4 * context.getResources().getDisplayMetrics().density);
                root.setPadding(padLeft, root.getPaddingTop(), root.getPaddingRight(), root.getPaddingBottom());
            }

            tvAuthor.setText(comment.getName());

            if (comment.getReplyName() != null && !comment.getReplyName().isEmpty()) {
                tvReplyTo.setVisibility(View.VISIBLE);
                tvReplyTo.setText("▸ " + comment.getReplyName());
            } else {
                tvReplyTo.setVisibility(View.GONE);
            }

            tvBody.setText(comment.getBody());
            tvTime.setText(TimeUtils.formatRelativeTime(comment.getTime()));

            GradientDrawable grad = ColorHelper.getAvatarGradient(comment.getColor());
            ivAvatar.setBackground(grad);
            if (comment.getAvatar() != null && !comment.getAvatar().isEmpty()) {
                String fullUrl = ImageUtils.getFullMediaUrl(serverBaseUrl, comment.getAvatar());
                Glide.with(context).load(fullUrl).circleCrop().into(ivAvatar);
            } else {
                ivAvatar.setImageDrawable(null);
            }

            btnReply.setOnClickListener(v -> {
                if (listener != null) listener.onReply(comment);
            });
        }
    }
}
