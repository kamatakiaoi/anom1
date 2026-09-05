package com.anonymous.chat.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.anonymous.chat.R;
import com.anonymous.chat.adapters.PostFeedAdapter;
import com.anonymous.chat.adapters.TopicAdapter;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.ActivityMainBinding;
import com.anonymous.chat.models.Comment;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.models.Post;
import com.anonymous.chat.models.ServerStats;
import com.anonymous.chat.models.Topic;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.ui.auth.AuthActivity;
import com.anonymous.chat.ui.chat.ChatActivity;
import com.anonymous.chat.ui.patchnotes.PatchNotesActivity;
import com.anonymous.chat.ui.post.PostDetailActivity;
import com.anonymous.chat.ui.profile.ProfileBottomSheet;
import com.anonymous.chat.ui.viewer.LightboxActivity;
import com.anonymous.chat.utils.AudioPlayerManager;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.TimeUtils;
import com.anonymous.chat.views.InAppNotificationBanner;
import com.anonymous.chat.views.MediaPickerBottomSheet;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        SocketManager.ConnectionListener,
        SocketManager.TopicListener,
        SocketManager.ExploreListener,
        SocketManager.ProfileListener,
        SocketManager.UserProfileDialogListener,
        SocketManager.GeneralMessageGlobalListener {

    private ActivityMainBinding binding;
    private PreferenceManager prefs;

    private TopicAdapter topicAdapter;
    private PostFeedAdapter postAdapter;

    private boolean isToolbarOpen = false;
    private String currentLobbyMode = "topics"; // "topics" or "explore"
    private String currentExploreSort = "latest"; // Default: "latest"
    private int currentExplorePage = 1;
    private int totalExplorePages = 1;

    // Explore creation media attachments
    private final List<String> pendingPostImages = new ArrayList<>();
    private String pendingPostVideo = null;
    private String pendingPostAudio = null;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> videoPickerLauncher;
    private ActivityResultLauncher<Intent> audioPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = PreferenceManager.getInstance(this);

        // Check if user is authenticated
        String savedKey = prefs.getAuthKey();
        if (savedKey == null || savedKey.isEmpty()) {
            startActivity(new Intent(this, AuthActivity.class));
            finish();
            return;
        }

        setupMediaPickers();
        setupUI();
        setupListeners();
        setupSocket();
        checkNotificationPermission();
        com.anonymous.chat.services.ChatBackgroundService.start(this);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void setupMediaPickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        pendingPostVideo = null;
                        pendingPostAudio = null;
                        pendingPostImages.clear();

                        Intent data = result.getData();
                        new Thread(() -> {
                            List<String> loaded = new java.util.ArrayList<>();
                            if (data.getClipData() != null) {
                                int count = Math.min(5, data.getClipData().getItemCount());
                                for (int i = 0; i < count; i++) {
                                    Uri uri = data.getClipData().getItemAt(i).getUri();
                                    String base64 = ImageUtils.uriToBase64(this, uri, 1080, 75);
                                    if (base64 != null) loaded.add(base64);
                                }
                            } else if (data.getData() != null) {
                                Uri uri = data.getData();
                                String base64 = ImageUtils.uriToBase64(this, uri, 1080, 75);
                                if (base64 != null) loaded.add(base64);
                            }
                            runOnUiThread(() -> {
                                pendingPostImages.addAll(loaded);
                                updatePostMediaStatusUI();
                            });
                        }).start();
                    }
                }
        );

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        pendingPostImages.clear();
                        pendingPostAudio = null;
                        Uri uri = result.getData().getData();
                        new Thread(() -> {
                            String base64 = ImageUtils.videoUriToBase64(this, uri);
                            runOnUiThread(() -> {
                                if (base64 != null) {
                                    pendingPostVideo = base64;
                                    updatePostMediaStatusUI();
                                } else {
                                    Toast.makeText(this, "Video too large (max 50MB)", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                }
        );

        audioPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        pendingPostImages.clear();
                        pendingPostVideo = null;
                        Uri uri = result.getData().getData();
                        new Thread(() -> {
                            String base64 = ImageUtils.audioUriToBase64(this, uri);
                            runOnUiThread(() -> {
                                if (base64 != null) {
                                    pendingPostAudio = base64;
                                    updatePostMediaStatusUI();
                                } else {
                                    Toast.makeText(this, "Audio too large (max 50MB)", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                }
        );
    }

    private void updatePostMediaStatusUI() {
        if (!pendingPostImages.isEmpty()) {
            binding.tvPostMediaStatus.setText(pendingPostImages.size() + " photo(s)");
        } else if (pendingPostVideo != null) {
            binding.tvPostMediaStatus.setText("1 video attached");
        } else if (pendingPostAudio != null) {
            binding.tvPostMediaStatus.setText("1 audio clip attached");
        } else {
            binding.tvPostMediaStatus.setText("");
        }
    }

    private long lastNavigationClickTime = 0;
    private boolean canNavigate() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastNavigationClickTime < 500) {
            return false;
        }
        lastNavigationClickTime = now;
        return true;
    }

    private void setupUI() {
        // Topics Adapter
        topicAdapter = new TopicAdapter(topic -> {
            if (!canNavigate()) return;
            if ("patch notes".equalsIgnoreCase(topic.getName())) {
                startActivity(new Intent(MainActivity.this, PatchNotesActivity.class));
            } else {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_TOPIC_ID, topic.getId());
                intent.putExtra(ChatActivity.EXTRA_TOPIC_NAME, topic.getName());
                startActivity(intent);
            }
        });
        binding.rvTopics.setLayoutManager(new LinearLayoutManager(this));
        binding.rvTopics.setItemViewCacheSize(25);
        if (binding.rvTopics.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) binding.rvTopics.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvTopics.setAdapter(topicAdapter);

        // Explore Feed Adapter
        postAdapter = new PostFeedAdapter(new PostFeedAdapter.PostInteractionListener() {
            @Override
            public void onPostClicked(Post post) {
                if (!canNavigate()) return;
                Intent intent = new Intent(MainActivity.this, PostDetailActivity.class);
                intent.putExtra(PostDetailActivity.EXTRA_POST_ID, post.getId());
                startActivity(intent);
            }

            @Override
            public void onUpvote(Post post) {
                SocketManager.getInstance().voteExplorePost(post.getId(), 1);
            }

            @Override
            public void onDownvote(Post post) {
                SocketManager.getInstance().voteExplorePost(post.getId(), -1);
            }

            @Override
            public void onMediaClicked(String mediaUrl, boolean isVideo) {
                if (!canNavigate()) return;
                Intent intent = new Intent(MainActivity.this, LightboxActivity.class);
                if (isVideo) {
                    intent.putExtra(LightboxActivity.EXTRA_VIDEO_URL, mediaUrl);
                } else {
                    intent.putExtra(LightboxActivity.EXTRA_IMAGE_URL, mediaUrl);
                }
                startActivity(intent);
            }

            @Override
            public void onAudioClicked(String audioUrl) {
                AudioPlayerManager.getInstance().playOrPause(audioUrl);
            }
        });
        binding.rvExploreFeed.setLayoutManager(new LinearLayoutManager(this));
        binding.rvExploreFeed.setItemViewCacheSize(25);
        if (binding.rvExploreFeed.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) binding.rvExploreFeed.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvExploreFeed.setAdapter(postAdapter);
    }

    private void setupListeners() {
        // Profile dialog
        binding.btnMainProfile.setOnClickListener(v -> {
            ProfileBottomSheet sheet = new ProfileBottomSheet();
            sheet.show(getSupportFragmentManager(), "ProfileBottomSheet");
        });

        // Mode Switching: Topics vs Explore
        binding.btnTabTopics.setOnClickListener(v -> switchLobbyMode("topics"));
        binding.btnTabExplore.setOnClickListener(v -> switchLobbyMode("explore"));

        // Collapsible Toolbar Toggle
        binding.btnToggleToolbar.setOnClickListener(v -> {
            isToolbarOpen = !isToolbarOpen;
            binding.layoutCollapsibleToolbar.setVisibility(isToolbarOpen ? View.VISIBLE : View.GONE);
            binding.btnToggleToolbar.setImageResource(isToolbarOpen ? android.R.drawable.arrow_up_float : android.R.drawable.arrow_down_float);
        });

        // Search Topics live filter
        binding.etSearchTopics.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                topicAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Create Topic
        binding.btnCreateTopic.setOnClickListener(v -> {
            String name = binding.etNewTopicName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter topic name", Toast.LENGTH_SHORT).show();
                return;
            }
            SocketManager.getInstance().createTopic(name);
            binding.etNewTopicName.setText("");
        });

        // Explore Sub-tab: Search vs Post
        binding.btnExSubSearch.setOnClickListener(v -> {
            setActiveButton(binding.btnExSubSearch, binding.btnExSubPost);
            binding.panelExSearch.setVisibility(View.VISIBLE);
            binding.panelExPost.setVisibility(View.GONE);
        });

        binding.btnExSubPost.setOnClickListener(v -> {
            setActiveButton(binding.btnExSubPost, binding.btnExSubSearch);
            binding.panelExSearch.setVisibility(View.GONE);
            binding.panelExPost.setVisibility(View.VISIBLE);
        });

        // Sort buttons
        binding.btnSortHot.setOnClickListener(v -> switchSort("hot"));
        binding.btnSortLatest.setOnClickListener(v -> switchSort("latest"));
        binding.btnSortOldest.setOnClickListener(v -> switchSort("oldest"));

        // Live Search Posts
        binding.etSearchPosts.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentExplorePage = 1;
                SocketManager.getInstance().loadExploreFeed(currentExplorePage, currentExploreSort, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Markdown Format Buttons
        binding.btnPostFmtBold.setOnClickListener(v -> {
            int start = binding.etPostBody.getSelectionStart();
            int end = binding.etPostBody.getSelectionEnd();
            Editable text = binding.etPostBody.getText();
            if (start != end) {
                text.insert(start, "**");
                text.insert(end + 2, "**");
            } else {
                text.insert(start, "****");
                binding.etPostBody.setSelection(start + 2);
            }
        });

        binding.btnPostFmtStrike.setOnClickListener(v -> {
            int start = binding.etPostBody.getSelectionStart();
            int end = binding.etPostBody.getSelectionEnd();
            Editable text = binding.etPostBody.getText();
            if (start != end) {
                text.insert(start, "~~");
                text.insert(end + 2, "~~");
            } else {
                text.insert(start, "~~~~");
                binding.etPostBody.setSelection(start + 2);
            }
        });

        // Attach Media Button
        binding.btnPostPickMedia.setOnClickListener(v -> {
            MediaPickerBottomSheet picker = new MediaPickerBottomSheet(new MediaPickerBottomSheet.OnMediaTypeSelectedListener() {
                @Override
                public void onPickImages() {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    imagePickerLauncher.launch(Intent.createChooser(intent, "Select Images (up to 5)"));
                }

                @Override
                public void onPickVideo() {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("video/*");
                    videoPickerLauncher.launch(Intent.createChooser(intent, "Select Video (max 50MB)"));
                }

                @Override
                public void onPickAudio() {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("audio/*");
                    audioPickerLauncher.launch(Intent.createChooser(intent, "Select Audio (max 50MB)"));
                }
            });
            picker.show(getSupportFragmentManager(), "MediaPickerSheet");
        });

        // Submit Post
        binding.btnSubmitPost.setOnClickListener(v -> {
            String title = binding.etPostTitle.getText().toString().trim();
            String body = binding.etPostBody.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "Title is required", Toast.LENGTH_SHORT).show();
                return;
            }

            SocketManager.getInstance().createExplorePost(
                    title,
                    body,
                    new ArrayList<>(),
                    pendingPostImages,
                    pendingPostVideo,
                    pendingPostAudio
            );

            binding.etPostTitle.setText("");
            binding.etPostBody.setText("");
            pendingPostImages.clear();
            pendingPostVideo = null;
            pendingPostAudio = null;
            updatePostMediaStatusUI();
            Toast.makeText(this, "Post submitted!", Toast.LENGTH_SHORT).show();
        });

        // Pagination Prev / Next Buttons
        binding.btnExPagePrev.setOnClickListener(v -> {
            if (currentExplorePage > 1) {
                goToExplorePage(currentExplorePage - 1);
            }
        });

        binding.btnExPageNext.setOnClickListener(v -> {
            if (currentExplorePage < totalExplorePages) {
                goToExplorePage(currentExplorePage + 1);
            }
        });

        // Swipe to refresh
        binding.swipeTopics.setOnRefreshListener(() -> {
            SocketManager.getInstance().connect(prefs.getServerBaseUrl());
            binding.swipeTopics.setRefreshing(false);
        });

        binding.swipeExplore.setOnRefreshListener(() -> {
            currentExplorePage = 1;
            SocketManager.getInstance().loadExploreFeed(currentExplorePage, currentExploreSort, binding.etSearchPosts.getText().toString());
            binding.swipeExplore.setRefreshing(false);
        });
    }

    private void setActiveButton(TextView active, TextView inactive) {
        active.setBackgroundResource(R.drawable.bg_btn_primary);
        active.setTextColor(Color.parseColor("#0A0A0A"));
        inactive.setBackgroundColor(Color.TRANSPARENT);
        inactive.setTextColor(Color.parseColor("#888888"));
    }

    private void switchLobbyMode(String mode) {
        if (mode.equals(currentLobbyMode)) return;
        currentLobbyMode = mode;
        if ("topics".equals(mode)) {
            setActiveButton(binding.btnTabTopics, binding.btnTabExplore);
            binding.swipeTopics.setVisibility(View.VISIBLE);
            binding.swipeExplore.setVisibility(View.GONE);
            binding.toolbarTopics.setVisibility(View.VISIBLE);
            binding.toolbarExplore.setVisibility(View.GONE);
        } else {
            setActiveButton(binding.btnTabExplore, binding.btnTabTopics);
            binding.swipeTopics.setVisibility(View.GONE);
            binding.swipeExplore.setVisibility(View.VISIBLE);
            binding.toolbarTopics.setVisibility(View.GONE);
            binding.toolbarExplore.setVisibility(View.VISIBLE);
            if (postAdapter.getItemCount() == 0) {
                switchSort("latest");
            }
        }
    }

    private void switchSort(String sort) {
        if (sort.equals(currentExploreSort) && postAdapter.getItemCount() > 0) {
            return;
        }
        currentExploreSort = sort;
        if ("hot".equals(sort)) {
            binding.btnSortHot.setBackgroundResource(R.drawable.bg_btn_primary);
            binding.btnSortHot.setTextColor(Color.parseColor("#0A0A0A"));
            binding.btnSortLatest.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortLatest.setTextColor(Color.parseColor("#888888"));
            binding.btnSortOldest.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortOldest.setTextColor(Color.parseColor("#888888"));
        } else if ("latest".equals(sort)) {
            binding.btnSortLatest.setBackgroundResource(R.drawable.bg_btn_primary);
            binding.btnSortLatest.setTextColor(Color.parseColor("#0A0A0A"));
            binding.btnSortHot.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortHot.setTextColor(Color.parseColor("#888888"));
            binding.btnSortOldest.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortOldest.setTextColor(Color.parseColor("#888888"));
        } else {
            binding.btnSortOldest.setBackgroundResource(R.drawable.bg_btn_primary);
            binding.btnSortOldest.setTextColor(Color.parseColor("#0A0A0A"));
            binding.btnSortHot.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortHot.setTextColor(Color.parseColor("#888888"));
            binding.btnSortLatest.setBackgroundColor(Color.TRANSPARENT);
            binding.btnSortLatest.setTextColor(Color.parseColor("#888888"));
        }
        currentExplorePage = 1;
        SocketManager.getInstance().loadExploreFeed(currentExplorePage, currentExploreSort, binding.etSearchPosts.getText().toString());
    }

    private void goToExplorePage(int page) {
        currentExplorePage = page;
        SocketManager.getInstance().loadExploreFeed(currentExplorePage, currentExploreSort, binding.etSearchPosts.getText().toString().trim());
        binding.rvExploreFeed.smoothScrollToPosition(0);
    }

    private void renderExplorePagination(int currentPage, int totalPages, int totalPosts) {
        this.totalExplorePages = totalPages;
        this.currentExplorePage = currentPage;

        if (totalPosts == 0 || totalPages <= 1) {
            binding.layoutExplorePagination.setVisibility(View.GONE);
            return;
        }

        binding.layoutExplorePagination.setVisibility(View.VISIBLE);

        // Prev Button
        binding.btnExPagePrev.setVisibility(currentPage > 1 ? View.VISIBLE : View.GONE);

        // Next Button
        binding.btnExPageNext.setVisibility(currentPage < totalPages ? View.VISIBLE : View.GONE);

        // Page Numbers Container
        binding.layoutExPageNums.removeAllViews();
        List<Object> pages = generatePaginationPages(currentPage, totalPages);

        int padH = (int) (12 * getResources().getDisplayMetrics().density);
        int padV = (int) (6 * getResources().getDisplayMetrics().density);

        for (Object p : pages) {
            if ("...".equals(p)) {
                TextView dots = new TextView(this);
                dots.setText("...");
                dots.setTextColor(Color.parseColor("#666666"));
                dots.setTextSize(12);
                dots.setPadding(padH / 2, padV, padH / 2, padV);
                dots.setGravity(Gravity.CENTER);
                binding.layoutExPageNums.addView(dots);
            } else {
                int pageNum = (int) p;
                TextView numView = new TextView(this);
                numView.setText(String.valueOf(pageNum));
                numView.setTextSize(13);
                numView.setGravity(Gravity.CENTER);
                numView.setPadding(padH, padV, padH, padV);

                if (pageNum == currentPage) {
                    numView.setBackgroundResource(R.drawable.bg_pagination_active);
                    numView.setTextColor(Color.parseColor("#F59E0B"));
                    numView.setTypeface(null, Typeface.BOLD);
                } else {
                    numView.setBackgroundColor(Color.TRANSPARENT);
                    numView.setTextColor(Color.parseColor("#888888"));
                    numView.setTypeface(null, Typeface.NORMAL);
                    numView.setOnClickListener(v -> goToExplorePage(pageNum));
                }
                binding.layoutExPageNums.addView(numView);
            }
        }
    }

    private List<Object> generatePaginationPages(int current, int total) {
        List<Object> arr = new ArrayList<>();
        if (total <= 7) {
            for (int i = 1; i <= total; i++) arr.add(i);
            return arr;
        }
        if (current <= 4) {
            arr.add(1); arr.add(2); arr.add(3); arr.add(4); arr.add(5);
            arr.add("..."); arr.add(total);
            return arr;
        }
        if (current >= total - 3) {
            arr.add(1); arr.add("...");
            arr.add(total - 4); arr.add(total - 3); arr.add(total - 2); arr.add(total - 1); arr.add(total);
            return arr;
        }
        arr.add(1); arr.add("...");
        arr.add(current - 1); arr.add(current); arr.add(current + 1);
        arr.add("..."); arr.add(total);
        return arr;
    }

    private void setupSocket() {
        SocketManager sm = SocketManager.getInstance();
        sm.init(this);
        sm.addConnectionListener(this);
        sm.addTopicListener(this);
        sm.addExploreListener(this);
        sm.addProfileListener(this);
        sm.addUserProfileListener(this);
        sm.addGeneralGlobalListener(this);

        sm.connect(prefs.getServerBaseUrl());
    }

    // General Chat In-App Notification (Messenger style)
    @Override
    public void onGeneralMessageReceived(Message message) {
        if (topicAdapter != null && message != null) {
            topicAdapter.updateGeneralLastMessage(message.getName(), message.getText());
        }
        InAppNotificationBanner.show(this, message, msg -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_TOPIC_NAME, "General");
            startActivity(intent);
        });
    }

    // Socket Topics
    @Override
    public void onTopicsUpdated(List<Topic> topics) {
        topicAdapter.setTopics(topics);
    }

    @Override
    public void onTopicCreated(int id, String name) {
        Toast.makeText(this, "Topic created: " + name, Toast.LENGTH_SHORT).show();
    }

    @Override public void onTopicJoined(Topic topic, List<Message> history, List<UserProfile> members, int onlineCount, boolean hasMore) {}
    @Override public void onTopicOnlineUpdated(int onlineCount, List<UserProfile> members) {}
    @Override public void onTopicStateChanged(int topicId, boolean locked, String lockedBy) {}
    @Override public void onTopicDeleted(int topicId, String name) {}
    @Override public void onHistoryLoaded(List<Message> olderHistory, boolean hasMore) {}

    // Socket Explore
    @Override
    public void onFeedLoaded(int page, int totalPages, int total, List<Post> posts, boolean hasMore) {
        postAdapter.setPosts(posts);
        renderExplorePagination(page, totalPages, total);
    }

    @Override
    public void onPostCreated(Post post) {
        postAdapter.addPostToTop(post);
        binding.rvExploreFeed.smoothScrollToPosition(0);
        switchLobbyMode("explore");
        setActiveButton(binding.btnExSubSearch, binding.btnExSubPost);
        binding.panelExSearch.setVisibility(View.VISIBLE);
        binding.panelExPost.setVisibility(View.GONE);
    }

    @Override public void onPostDetailLoaded(Post post) {}
    @Override public void onCommentsLoaded(int postId, List<Comment> comments) {}
    @Override public void onNewComment(Comment comment) {}
    @Override public void onCommentCountUpdated(int postId, int commentsCount) {}

    @Override
    public void onPostVoteUpdated(int postId, int upvotes, int downvotes, int score) {
        postAdapter.updatePostScore(postId, upvotes, downvotes, score);
    }

    @Override
    public void onPostVoteMeUpdated(int postId, int upvotes, int downvotes, int score, int myVote) {
        postAdapter.updatePostVoteMe(postId, upvotes, downvotes, score, myVote);
    }

    @Override public void onPostViewsUpdated(int postId, int views) {}
    @Override public void onPostSharesUpdated(int postId, int shares) {}
    @Override public void onPostDeleted(int postId) {}

    // Socket Connection
    @Override
    public void onConnected() {
        SocketManager.getInstance().joinTopic("General");
    }

    @Override public void onDisconnected() {}
    @Override public void onConnectionError(String error) {}
    @Override public void onPingUpdated(long latencyMs) {}

    @Override
    public void onStatsUpdated(ServerStats stats) {
        if (stats != null) {
            binding.tvGlobalOnline.setText("Global online (" + stats.getOnline() + ")");
            binding.tvServerUptime.setText("Uptime " + TimeUtils.formatUptime(stats.getUptime()));
        }
    }

    // Socket Profile
    @Override public void onProfileLoaded(UserProfile profile) {}
    @Override public void onNameChanged(String newName) {}
    @Override public void onAvatarChanged(String newAvatarUrl) {}

    private com.anonymous.chat.ui.profile.UserProfileDialog activeProfileDialog;

    @Override
    public void onUserProfileReceived(UserProfile userProfile) {
        if (!isFinishing() && !isDestroyed() && userProfile != null) {
            if (activeProfileDialog != null && activeProfileDialog.isShowing()) {
                activeProfileDialog.updateProfile(userProfile);
            } else {
                activeProfileDialog = new com.anonymous.chat.ui.profile.UserProfileDialog(this, userProfile);
                activeProfileDialog.show();
            }
        }
    }

    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AudioPlayerManager.getInstance().stop();
        SocketManager sm = SocketManager.getInstance();
        sm.removeConnectionListener(this);
        sm.removeTopicListener(this);
        sm.removeExploreListener(this);
        sm.removeProfileListener(this);
        sm.removeUserProfileListener(this);
        sm.removeGeneralGlobalListener(this);
    }
}
