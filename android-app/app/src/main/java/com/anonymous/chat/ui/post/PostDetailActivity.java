package com.anonymous.chat.ui.post;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.anonymous.chat.R;
import com.anonymous.chat.adapters.CommentAdapter;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.ActivityPostDetailBinding;
import com.anonymous.chat.models.Comment;
import com.anonymous.chat.models.Post;
import com.anonymous.chat.ui.viewer.LightboxActivity;
import com.anonymous.chat.utils.AudioPlayerManager;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.SoundHelper;
import com.anonymous.chat.utils.TimeUtils;
import com.bumptech.glide.Glide;

import java.util.List;

public class PostDetailActivity extends AppCompatActivity implements
        SocketManager.ExploreListener,
        SocketManager.UserProfileDialogListener {

    public static final String EXTRA_POST_ID = "extra_post_id";

    private ActivityPostDetailBinding binding;
    private PreferenceManager prefs;
    private CommentAdapter commentAdapter;

    private int postId = 0;
    private Post currentPost;
    private Comment replyingToComment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPostDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = PreferenceManager.getInstance(this);
        postId = getIntent().getIntExtra(EXTRA_POST_ID, 0);

        setupUI();
        setupListeners();
        loadPostData();
    }

    private void setupUI() {
        commentAdapter = new CommentAdapter(comment -> {
            replyingToComment = comment;
            binding.barCommentReplyingTo.setVisibility(View.VISIBLE);
            binding.tvCommentReplyTarget.setText("Replying to " + comment.getName() + ": " + comment.getBody());
        });

        binding.rvPostComments.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPostComments.setItemViewCacheSize(25);
        if (binding.rvPostComments.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) binding.rvPostComments.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvPostComments.setAdapter(commentAdapter);
    }

    private void setupListeners() {
        binding.btnPostDetailBack.setOnClickListener(v -> finish());

        binding.btnPostDetailMore.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "ID: " + postId).setEnabled(false);
            popup.getMenu().add(0, 2, 1, "Copy link");
            if (currentPost != null && currentPost.isOwner()) {
                popup.getMenu().add(0, 3, 2, "Delete post");
            }
            popup.getMenu().add(0, 4, 3, "Report");

            popup.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == 2) {
                    String serverUrl = PreferenceManager.getInstance(this).getServerBaseUrl();
                    String url = serverUrl + "/explore/" + postId;
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        ClipData clip = ClipData.newPlainText("Post Link", url);
                        cm.setPrimaryClip(clip);
                    }
                    SocketManager.getInstance().shareExplorePost(postId);
                    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show();
                    return true;
                } else if (itemId == 3) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Delete post")
                            .setMessage("Delete this post and its media files from disk?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                SocketManager.getInstance().deleteExplorePost(postId);
                                Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                } else if (itemId == 4) {
                    Toast.makeText(this, "Report submitted", Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            popup.show();
        });

        binding.btnCancelCommentReply.setOnClickListener(v -> {
            replyingToComment = null;
            binding.barCommentReplyingTo.setVisibility(View.GONE);
        });

        binding.btnDetailUpvote.setOnClickListener(v -> {
            if (currentPost != null) {
                int cur = currentPost.getMyVote();
                int next = (cur == 1) ? 0 : 1;
                currentPost.setMyVote(next);
                currentPost.setScore(currentPost.getScore() + (next - cur));
                bindPost(currentPost);
                SocketManager.getInstance().voteExplorePost(postId, next);
            }
        });

        binding.btnDetailDownvote.setOnClickListener(v -> {
            if (currentPost != null) {
                int cur = currentPost.getMyVote();
                int next = (cur == -1) ? 0 : -1;
                currentPost.setMyVote(next);
                currentPost.setScore(currentPost.getScore() + (next - cur));
                bindPost(currentPost);
                SocketManager.getInstance().voteExplorePost(postId, next);
            }
        });

        binding.btnSendComment.setOnClickListener(v -> submitComment());
        binding.etCommentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                submitComment();
                return true;
            }
            return false;
        });
    }

    private void submitComment() {
        String body = binding.etCommentInput.getText().toString().trim();
        if (body.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer parentId = replyingToComment != null ? replyingToComment.getId() : null;
        String replyName = replyingToComment != null ? replyingToComment.getAuthorName() : null;
        String replyText = replyingToComment != null ? replyingToComment.getBody() : null;

        SocketManager.getInstance().commentExplorePost(postId, body, parentId, replyName, replyText);

        binding.etCommentInput.setText("");
        replyingToComment = null;
        binding.barCommentReplyingTo.setVisibility(View.GONE);
    }

    private void loadPostData() {
        SocketManager.getInstance().addExploreListener(this);
        SocketManager.getInstance().addUserProfileListener(this);
        SocketManager.getInstance().getExplorePost(postId);
        SocketManager.getInstance().viewExplorePost(postId);
        SocketManager.getInstance().loadExploreComments(postId);
    }

    private void bindPost(Post post) {
        this.currentPost = post;
        binding.tvDetailAuthor.setText(post.getAuthorName() != null ? post.getAuthorName() : "Anon");

        GradientDrawable grad = ColorHelper.getAvatarGradient(post.getColor());
        binding.ivDetailAvatar.setBackground(grad);

        View.OnClickListener authorClick = v -> {
            if (post != null) {
                SocketManager.getInstance().requestUserProfile(post.getAuthorUid(), post.getAuthorName(), post.getAuthorId());
            }
        };
        binding.ivDetailAvatar.setOnClickListener(authorClick);
        binding.tvDetailAuthor.setOnClickListener(authorClick);

        String serverUrl = PreferenceManager.getInstance(this).getServerBaseUrl();
        if (post.getAvatar() != null && !post.getAvatar().isEmpty()) {
            ImageUtils.loadAvatar(this, post.getAvatar(), binding.ivDetailAvatar);
        }

        String tz = PreferenceManager.getInstance(this).getTimezone();
        binding.tvDetailTime.setText(TimeUtils.formatMessageTime(post.getCreatedAt(), tz));

        binding.tvDetailTitle.setText(post.getTitle());
        binding.tvDetailBody.setText(post.getBody());
        binding.tvDetailScore.setText(String.valueOf(post.getScore()));
        binding.tvDetailViews.setText(post.getViews() + " views");

        // Vote status styling (Green / Red / Neutral)
        int myVote = post.getMyVote();
        if (myVote == 1) {
            binding.btnDetailUpvote.setColorFilter(Color.parseColor("#22C55E"));
            binding.btnDetailDownvote.setColorFilter(Color.parseColor("#888888"));
            binding.tvDetailScore.setTextColor(Color.parseColor("#22C55E"));
        } else if (myVote == -1) {
            binding.btnDetailUpvote.setColorFilter(Color.parseColor("#888888"));
            binding.btnDetailDownvote.setColorFilter(Color.parseColor("#EF4444"));
            binding.tvDetailScore.setTextColor(Color.parseColor("#EF4444"));
        } else {
            binding.btnDetailUpvote.setColorFilter(Color.parseColor("#888888"));
            binding.btnDetailDownvote.setColorFilter(Color.parseColor("#888888"));
            if (post.getScore() > 0) {
                binding.tvDetailScore.setTextColor(Color.parseColor("#22C55E"));
            } else if (post.getScore() < 0) {
                binding.tvDetailScore.setTextColor(Color.parseColor("#EF4444"));
            } else {
                binding.tvDetailScore.setTextColor(Color.parseColor("#CCCCCC"));
            }
        }

        // Media images and video
        binding.detailMediaContainer.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int heightPx = (int) (220 * density);

        if (post.getVideo() != null && !post.getVideo().isEmpty()) {
            String videoUrl = post.getVideo();
            ImageView videoThumb = new ImageView(this);
            videoThumb.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
            videoThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            ImageUtils.loadImage(this, videoUrl, videoThumb, 10);
            videoThumb.setOnClickListener(v -> {
                Intent intent = new Intent(PostDetailActivity.this, LightboxActivity.class);
                intent.putExtra(LightboxActivity.EXTRA_VIDEO_URL, videoUrl);
                startActivity(intent);
            });
            binding.detailMediaContainer.addView(videoThumb);
        } else if (post.getImages() != null && !post.getImages().isEmpty()) {
            for (String img : post.getImages()) {
                ImageView iv = new ImageView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
                lp.setMargins(0, (int) (4 * density), 0, (int) (4 * density));
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ImageUtils.loadImage(this, img, iv, 10);
                iv.setOnClickListener(v -> {
                    Intent intent = new Intent(PostDetailActivity.this, LightboxActivity.class);
                    intent.putExtra(LightboxActivity.EXTRA_IMAGE_URL, img);
                    startActivity(intent);
                });
                binding.detailMediaContainer.addView(iv);
            }
        }

        // Audio Player
        if (post.getAudio() != null && !post.getAudio().isEmpty()) {
            binding.detailAudioPlayer.audioPlayerLayout.setVisibility(View.VISIBLE);
            String fullAudio = ImageUtils.getFullMediaUrl(serverUrl, post.getAudio());
            setupAudioPlayer(binding.detailAudioPlayer.getRoot(), fullAudio);
        } else {
            binding.detailAudioPlayer.audioPlayerLayout.setVisibility(View.GONE);
        }
    }

    private void setupAudioPlayer(View view, String audioUrl) {
        ImageView btnPlay = view.findViewById(R.id.btnAudioPlayPause);
        SeekBar seekBar = view.findViewById(R.id.sbAudioProgress);
        TextView tvCurrent = view.findViewById(R.id.tvAudioCurrentTime);
        TextView tvDuration = view.findViewById(R.id.tvAudioDuration);

        boolean isPlaying = AudioPlayerManager.getInstance().isPlaying(audioUrl);
        btnPlay.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);

        btnPlay.setOnClickListener(v -> {
            AudioPlayerManager.getInstance().playOrPause(audioUrl);
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

            @Override
            public void onStartTrackingTouch(SeekBar sb) {}

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
            public void onProgress(String url, int currentPositionMs, int durationMs) {
                if (url.equals(audioUrl)) {
                    if (durationMs > 0) {
                        int progress = (int) (((float) currentPositionMs / durationMs) * 100);
                        seekBar.setProgress(progress);
                    }
                    tvCurrent.setText(formatDuration(currentPositionMs));
                    tvDuration.setText(formatDuration(durationMs));
                }
            }

            @Override
            public void onError(String url, String error) {
                if (url.equals(audioUrl)) btnPlay.setImageResource(R.drawable.ic_play);
            }
        });
    }

    private static String formatDuration(int ms) {
        int totalSeconds = ms / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public void onPostDetailLoaded(Post post) {
        if (post != null && post.getId() == postId) {
            bindPost(post);
        }
    }

    @Override
    public void onCommentsLoaded(int postId, List<Comment> comments) {
        if (this.postId == postId) {
            commentAdapter.setComments(comments);
            binding.tvCommentsHeader.setText("Comments (" + comments.size() + ")");
        }
    }

    @Override
    public void onNewComment(Comment comment) {
        if (comment != null && comment.getPostId() == postId) {
            commentAdapter.addComment(comment);
            binding.tvCommentsHeader.setText("Comments (" + commentAdapter.getItemCount() + ")");
        }
    }

    @Override
    public void onPostVoteUpdated(int postId, int upvotes, int downvotes, int score) {
        if (this.postId == postId && currentPost != null) {
            currentPost.setScore(score);
            binding.tvDetailScore.setText(String.valueOf(score));
        }
    }

    @Override
    public void onPostVoteMeUpdated(int postId, int upvotes, int downvotes, int score, int myVote) {
        if (this.postId == postId && currentPost != null) {
            currentPost.setUpvotes(upvotes);
            currentPost.setDownvotes(downvotes);
            currentPost.setScore(score);
            currentPost.setMyVote(myVote);
            bindPost(currentPost);
        }
    }

    @Override
    public void onPostViewsUpdated(int postId, int views) {
        if (this.postId == postId) {
            binding.tvDetailViews.setText(views + " views");
        }
    }

    @Override public void onFeedLoaded(int page, int totalPages, int total, List<Post> posts, boolean hasMore) {}
    @Override public void onPostCreated(Post post) {}
    @Override public void onPostSharesUpdated(int postId, int shares) {}
    @Override public void onCommentCountUpdated(int postId, int commentsCount) {}
    @Override public void onPostDeleted(int postId) {
        if (this.postId == postId) {
            Toast.makeText(this, "This post has been deleted", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onUserProfileReceived(com.anonymous.chat.models.UserProfile userProfile) {
        if (!isFinishing() && !isDestroyed() && userProfile != null) {
            com.anonymous.chat.ui.profile.UserProfileDialog dialog = new com.anonymous.chat.ui.profile.UserProfileDialog(this, userProfile);
            dialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        AudioPlayerManager.getInstance().pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AudioPlayerManager.getInstance().stop();
        SocketManager.getInstance().removeExploreListener(this);
        SocketManager.getInstance().removeUserProfileListener(this);
    }
}
