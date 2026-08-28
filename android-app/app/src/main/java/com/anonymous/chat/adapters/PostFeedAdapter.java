package com.anonymous.chat.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.models.Post;
import com.anonymous.chat.utils.AudioPlayerManager;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.TimeUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class PostFeedAdapter extends RecyclerView.Adapter<PostFeedAdapter.PostViewHolder> {

    public interface PostInteractionListener {
        void onPostClicked(Post post);
        void onUpvote(Post post);
        void onDownvote(Post post);
        void onMediaClicked(String mediaUrl, boolean isVideo);
        void onAudioClicked(String audioUrl);
    }

    private final List<Post> posts = new ArrayList<>();
    private final PostInteractionListener listener;

    public PostFeedAdapter(PostInteractionListener listener) {
        this.listener = listener;
    }

    public void setPosts(List<Post> newPosts) {
        posts.clear();
        if (newPosts != null) {
            posts.addAll(newPosts);
        }
        notifyDataSetChanged();
    }

    public void appendPosts(List<Post> olderPosts) {
        if (olderPosts == null || olderPosts.isEmpty()) return;
        int start = posts.size();
        posts.addAll(olderPosts);
        notifyItemRangeInserted(start, olderPosts.size());
    }

    public void addPostToTop(Post post) {
        if (post == null) return;
        posts.add(0, post);
        notifyItemInserted(0);
    }

    public void updatePostScore(int postId, int upvotes, int downvotes, int score) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == postId) {
                Post p = posts.get(i);
                p.setUpvotes(upvotes);
                p.setDownvotes(downvotes);
                p.setScore(score);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updatePostVoteMe(int postId, int upvotes, int downvotes, int score, int myVote) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == postId) {
                Post p = posts.get(i);
                p.setUpvotes(upvotes);
                p.setDownvotes(downvotes);
                p.setScore(score);
                p.setMyVote(myVote);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updatePostViews(int postId, int views) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == postId) {
                posts.get(i).setViews(views);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void incrementCommentCount(int postId) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == postId) {
                posts.get(i).setComments(posts.get(i).getComments() + 1);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void setCommentCount(int postId, int count) {
        for (int i = 0; i < posts.size(); i++) {
            if (posts.get(i).getId() == postId) {
                posts.get(i).setComments(count);
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_card, parent, false);
        return new PostViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.bind(post, listener);
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvAuthor;
        final TextView tvTime;
        final TextView tvTitle;
        final TextView tvBody;
        final FrameLayout postMediaFrame;
        final ImageView ivPostMedia;
        final ImageView ivPlayIcon;
        final View postAudioPlayer;
        final ImageView btnUpvote;
        final ImageView btnDownvote;
        final TextView tvScore;
        final TextView tvCommentCount;
        final TextView tvViewCount;
        final View btnOpenComments;
        final ImageView btnPostMore;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivPostAvatar);
            tvAuthor = itemView.findViewById(R.id.tvPostAuthor);
            tvTime = itemView.findViewById(R.id.tvPostTime);
            tvTitle = itemView.findViewById(R.id.tvPostTitle);
            tvBody = itemView.findViewById(R.id.tvPostBody);
            postMediaFrame = itemView.findViewById(R.id.postMediaFrame);
            ivPostMedia = itemView.findViewById(R.id.ivPostMedia);
            ivPlayIcon = itemView.findViewById(R.id.ivPostPlayIcon);
            postAudioPlayer = itemView.findViewById(R.id.postAudioPlayer);
            btnUpvote = itemView.findViewById(R.id.btnUpvote);
            btnDownvote = itemView.findViewById(R.id.btnDownvote);
            tvScore = itemView.findViewById(R.id.tvScore);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            tvViewCount = itemView.findViewById(R.id.tvViewCount);
            btnOpenComments = itemView.findViewById(R.id.btnOpenComments);
            btnPostMore = itemView.findViewById(R.id.btnPostMore);
        }

        void bind(Post post, PostInteractionListener listener) {
            tvAuthor.setText(post.getAuthorName() != null ? post.getAuthorName() : "Anon");

            GradientDrawable grad = ColorHelper.getAvatarGradient(post.getColor());
            ivAvatar.setBackground(grad);

            String serverUrl = PreferenceManager.getInstance(itemView.getContext()).getServerBaseUrl();
            if (post.getAvatar() != null && !post.getAvatar().isEmpty()) {
                ImageUtils.loadAvatar(itemView.getContext(), post.getAvatar(), ivAvatar);
            }

            String tz = PreferenceManager.getInstance(itemView.getContext()).getTimezone();
            tvTime.setText(TimeUtils.formatMessageTime(post.getCreatedAt(), tz));

            tvTitle.setText(post.getTitle());
            tvBody.setText(post.getBody());
            tvScore.setText(String.valueOf(post.getScore()));
            tvCommentCount.setText(String.valueOf(post.getComments()));
            tvViewCount.setText(String.valueOf(post.getViews()));

            // Vote status styling (Green / Red / Neutral)
            int myVote = post.getMyVote();
            if (myVote == 1) {
                btnUpvote.setColorFilter(Color.parseColor("#22C55E"));
                btnDownvote.setColorFilter(Color.parseColor("#888888"));
                tvScore.setTextColor(Color.parseColor("#22C55E"));
            } else if (myVote == -1) {
                btnUpvote.setColorFilter(Color.parseColor("#888888"));
                btnDownvote.setColorFilter(Color.parseColor("#EF4444"));
                tvScore.setTextColor(Color.parseColor("#EF4444"));
            } else {
                btnUpvote.setColorFilter(Color.parseColor("#888888"));
                btnDownvote.setColorFilter(Color.parseColor("#888888"));
                if (post.getScore() > 0) {
                    tvScore.setTextColor(Color.parseColor("#22C55E"));
                } else if (post.getScore() < 0) {
                    tvScore.setTextColor(Color.parseColor("#EF4444"));
                } else {
                    tvScore.setTextColor(Color.parseColor("#CCCCCC"));
                }
            }

            // Video / Image Media
            if (post.getVideo() != null && !post.getVideo().isEmpty()) {
                postMediaFrame.setVisibility(View.VISIBLE);
                ivPlayIcon.setVisibility(View.VISIBLE);
                String videoUrl = post.getVideo();
                ImageUtils.loadImage(itemView.getContext(), videoUrl, ivPostMedia, 8);
                postMediaFrame.setOnClickListener(v -> {
                    if (listener != null) listener.onMediaClicked(videoUrl, true);
                });
            } else if (post.getImages() != null && !post.getImages().isEmpty()) {
                postMediaFrame.setVisibility(View.VISIBLE);
                ivPlayIcon.setVisibility(View.GONE);
                String imgUrl = post.getImages().get(0);
                ImageUtils.loadImage(itemView.getContext(), imgUrl, ivPostMedia, 8);
                postMediaFrame.setOnClickListener(v -> {
                    if (listener != null) listener.onMediaClicked(imgUrl, false);
                });
            } else {
                postMediaFrame.setVisibility(View.GONE);
            }

            // Audio Player
            if (post.getAudio() != null && !post.getAudio().isEmpty()) {
                postAudioPlayer.setVisibility(View.VISIBLE);
                String fullAudio = ImageUtils.getFullMediaUrl(serverUrl, post.getAudio());
                setupAudioPlayer(postAudioPlayer, fullAudio, listener);
            } else {
                postAudioPlayer.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onPostClicked(post);
            });

            if (btnOpenComments != null) {
                btnOpenComments.setOnClickListener(v -> {
                    if (listener != null) listener.onPostClicked(post);
                });
            }

            if (btnPostMore != null) {
                btnPostMore.setOnClickListener(v -> {
                    Context context = itemView.getContext();
                    PopupMenu popup = new PopupMenu(context, v);
                    popup.getMenu().add(0, 1, 0, "ID: " + post.getId()).setEnabled(false);
                    popup.getMenu().add(0, 2, 1, "Copy link");
                    if (post.isOwner()) {
                        popup.getMenu().add(0, 3, 2, "Delete post");
                    }
                    popup.getMenu().add(0, 4, 3, "Report");

                    popup.setOnMenuItemClickListener(item -> {
                        int itemId = item.getItemId();
                        if (itemId == 2) {
                            String url = serverUrl + "/explore/" + post.getId();
                            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                ClipData clip = ClipData.newPlainText("Post Link", url);
                                cm.setPrimaryClip(clip);
                            }
                            SocketManager.getInstance().shareExplorePost(post.getId());
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show();
                            return true;
                        } else if (itemId == 3) {
                            new androidx.appcompat.app.AlertDialog.Builder(context)
                                    .setTitle("Delete post")
                                    .setMessage("Delete this post and its media files from disk?")
                                    .setPositiveButton("Delete", (dialog, which) -> {
                                        SocketManager.getInstance().deleteExplorePost(post.getId());
                                        Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show();
                                    })
                                    .setNegativeButton("Cancel", null)
                                    .show();
                            return true;
                        } else if (itemId == 4) {
                            Toast.makeText(context, "Report submitted", Toast.LENGTH_SHORT).show();
                            return true;
                        }
                        return false;
                    });
                    popup.show();
                });
            }

            btnUpvote.setOnClickListener(v -> {
                // Optimistic UI update
                int currentVote = post.getMyVote();
                int newVote = (currentVote == 1) ? 0 : 1;
                int scoreDiff = newVote - currentVote;
                post.setMyVote(newVote);
                post.setScore(post.getScore() + scoreDiff);
                bind(post, listener);

                if (listener != null) listener.onUpvote(post);
            });

            btnDownvote.setOnClickListener(v -> {
                // Optimistic UI update
                int currentVote = post.getMyVote();
                int newVote = (currentVote == -1) ? 0 : -1;
                int scoreDiff = newVote - currentVote;
                post.setMyVote(newVote);
                post.setScore(post.getScore() + scoreDiff);
                bind(post, listener);

                if (listener != null) listener.onDownvote(post);
            });
        }
    }

    private static void setupAudioPlayer(View view, String audioUrl, PostInteractionListener listener) {
        ImageView btnPlay = view.findViewById(R.id.btnAudioPlayPause);
        SeekBar seekBar = view.findViewById(R.id.sbAudioProgress);
        TextView tvCurrent = view.findViewById(R.id.tvAudioCurrentTime);
        TextView tvDuration = view.findViewById(R.id.tvAudioDuration);

        boolean isPlaying = AudioPlayerManager.getInstance().isPlaying(audioUrl);
        btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        btnPlay.setOnClickListener(v -> {
            if (listener != null) listener.onAudioClicked(audioUrl);
            boolean nowPlaying = AudioPlayerManager.getInstance().isPlaying(audioUrl);
            btnPlay.setImageResource(nowPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && AudioPlayerManager.getInstance().isPlaying(audioUrl)) {
                    int duration = AudioPlayerManager.getInstance().getDuration();
                    if (duration > 0) {
                        int targetMs = (int) (((float) progress / 100) * duration);
                        tvCurrent.setText(formatDuration(targetMs));
                    }
                }
            }

            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                int duration = AudioPlayerManager.getInstance().getDuration();
                if (duration > 0) {
                    int targetMs = (int) (((float) sb.getProgress() / 100) * duration);
                    AudioPlayerManager.getInstance().seekTo(targetMs);
                }
            }
        });

        AudioPlayerManager.getInstance().setListener(new AudioPlayerManager.OnAudioStateChangeListener() {
            @Override
            public void onPlay(String url) {
                if (url.equals(audioUrl)) btnPlay.setImageResource(R.drawable.ic_pause);
            }

            @Override
            public void onPause(String url) {
                if (url.equals(audioUrl)) btnPlay.setImageResource(R.drawable.ic_play);
            }

            @Override
            public void onStop(String url) {
                if (url.equals(audioUrl)) {
                    btnPlay.setImageResource(R.drawable.ic_play);
                    seekBar.setProgress(0);
                    tvCurrent.setText("0:00");
                }
            }

            @Override
            public void onProgress(String url, int currentMs, int durationMs) {
                if (url.equals(audioUrl) && durationMs > 0) {
                    int progress = (int) (((float) currentMs / durationMs) * 100);
                    seekBar.setProgress(progress);
                    tvCurrent.setText(formatDuration(currentMs));
                    tvDuration.setText(formatDuration(durationMs));
                }
            }

            @Override
            public void onError(String url, String error) {
                if (url.equals(audioUrl)) {
                    btnPlay.setImageResource(R.drawable.ic_play);
                }
            }
        });
    }

    private static String formatDuration(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
