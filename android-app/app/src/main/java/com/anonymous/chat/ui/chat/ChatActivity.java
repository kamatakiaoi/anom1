package com.anonymous.chat.ui.chat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.adapters.MessageAdapter;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.databinding.ActivityChatBinding;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.models.Topic;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.ui.profile.UserProfileDialog;
import com.anonymous.chat.ui.viewer.LightboxActivity;
import com.anonymous.chat.utils.AudioPlayerManager;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.views.InAppNotificationBanner;
import com.anonymous.chat.views.MediaPickerBottomSheet;

import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity implements
        SocketManager.TopicListener,
        SocketManager.MessageListener,
        SocketManager.UserProfileDialogListener,
        SocketManager.GeneralMessageGlobalListener {

    public static final String EXTRA_TOPIC_ID = "extra_topic_id";
    public static final String EXTRA_TOPIC_NAME = "extra_topic_name";

    private ActivityChatBinding binding;
    private PreferenceManager prefs;
    private MessageAdapter messageAdapter;

    private int topicId = 0;
    private String topicName = "";
    private Topic currentTopic;
    private boolean isLocked = false;
    private boolean isOwner = false;
    private List<UserProfile> currentMembers = new ArrayList<>();

    // Attached Media
    private final List<String> pendingImages = new ArrayList<>();
    private String pendingVideo = null;
    private String pendingAudio = null;

    // Reply Data
    private Message replyingToMessage = null;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> videoPickerLauncher;
    private ActivityResultLauncher<Intent> audioPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = PreferenceManager.getInstance(this);

        topicId = getIntent().getIntExtra(EXTRA_TOPIC_ID, 0);
        topicName = getIntent().getStringExtra(EXTRA_TOPIC_NAME);
        if (topicName == null) topicName = "General";

        setupMediaPickers();
        setupUI();
        setupListeners();
        joinTopic();
    }

    private void setupMediaPickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        pendingVideo = null;
                        pendingAudio = null;
                        pendingImages.clear();

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
                                pendingImages.addAll(loaded);
                                updateMediaPreviewUI();
                            });
                        }).start();
                    }
                }
        );

        videoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        pendingImages.clear();
                        pendingAudio = null;
                        Uri uri = result.getData().getData();
                        new Thread(() -> {
                            String base64 = ImageUtils.videoUriToBase64(this, uri);
                            runOnUiThread(() -> {
                                if (base64 != null) {
                                    pendingVideo = base64;
                                    updateMediaPreviewUI();
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
                        pendingImages.clear();
                        pendingVideo = null;
                        Uri uri = result.getData().getData();
                        new Thread(() -> {
                            String base64 = ImageUtils.audioUriToBase64(this, uri);
                            runOnUiThread(() -> {
                                if (base64 != null) {
                                    pendingAudio = base64;
                                    updateMediaPreviewUI();
                                } else {
                                    Toast.makeText(this, "Audio too large (max 50MB)", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }).start();
                    }
                }
        );
    }

    private void updateMediaPreviewUI() {
        if (!pendingImages.isEmpty()) {
            binding.barReplyingTo.setVisibility(View.VISIBLE);
            binding.tvReplyBannerName.setText("Photos Attached");
            binding.tvReplyBannerText.setText(pendingImages.size() + " photo(s) selected");
        } else if (pendingVideo != null) {
            binding.barReplyingTo.setVisibility(View.VISIBLE);
            binding.tvReplyBannerName.setText("Video Attached");
            binding.tvReplyBannerText.setText("1 video attached (mp4/mov)");
        } else if (pendingAudio != null) {
            binding.barReplyingTo.setVisibility(View.VISIBLE);
            binding.tvReplyBannerName.setText("Audio Clip Attached");
            binding.tvReplyBannerText.setText("1 audio clip attached (mp3/wav)");
        } else if (replyingToMessage != null) {
            binding.barReplyingTo.setVisibility(View.VISIBLE);
            binding.tvReplyBannerName.setText("Replying to " + replyingToMessage.getName());
            binding.tvReplyBannerText.setText(replyingToMessage.getText() != null ? replyingToMessage.getText() : "(media)");
        } else {
            binding.barReplyingTo.setVisibility(View.GONE);
        }
    }

    private void setupUI() {
        binding.tvChatTopicTitle.setText(topicName);

        messageAdapter = new MessageAdapter(new MessageAdapter.MessageInteractionListener() {
            @Override
            public void onReply(Message message) {
                replyingToMessage = message;
                updateMediaPreviewUI();
            }

            @Override
            public void onAvatarClicked(String uid) {
                if (uid != null && !uid.isEmpty()) {
                    SocketManager.getInstance().requestUserProfile(uid);
                }
            }

            @Override
            public void onMediaClicked(String mediaUrl, boolean isVideo) {
                Intent intent = new Intent(ChatActivity.this, LightboxActivity.class);
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

            @Override
            public void onJumpToMessage(int messageId) {
                int position = messageAdapter.findMessagePositionById(messageId);
                if (position != -1) {
                    binding.rvChatMessages.smoothScrollToPosition(position);
                }
            }
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.rvChatMessages.setLayoutManager(layoutManager);
        binding.rvChatMessages.setItemViewCacheSize(30);
        binding.rvChatMessages.setHasFixedSize(false);
        if (binding.rvChatMessages.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) binding.rvChatMessages.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvChatMessages.setAdapter(messageAdapter);

        binding.rvChatMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(-1)) {
                    int oldestId = messageAdapter.getOldestMessageId();
                    if (oldestId > 0) {
                        SocketManager.getInstance().loadHistory(oldestId);
                    }
                }
            }
        });
    }

    private void setupListeners() {
        binding.btnChatBack.setOnClickListener(v -> finish());

        // Online members presence
        binding.tvChatOnlineCount.setOnClickListener(v -> {
            OnlineMembersDialog dialog = new OnlineMembersDialog(this, currentMembers, member -> {
                if (member.getUid() != null) {
                    SocketManager.getInstance().requestUserProfile(member.getUid());
                }
            });
            dialog.show();
        });

        // Cancel Reply / Media
        binding.btnCancelReply.setOnClickListener(v -> {
            replyingToMessage = null;
            pendingImages.clear();
            pendingVideo = null;
            pendingAudio = null;
            updateMediaPreviewUI();
        });

        // Attach Button (Images, Video, Audio)
        binding.btnAttachImage.setOnClickListener(v -> {
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
            picker.show(getSupportFragmentManager(), "ChatMediaPickerSheet");
        });

        // Send Message
        binding.btnSendMessage.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        if (isLocked) {
            Toast.makeText(this, "Topic is locked", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = binding.etChatMessage.getText().toString().trim();
        boolean hasMedia = !pendingImages.isEmpty() || pendingVideo != null || pendingAudio != null;

        if (text.isEmpty() && !hasMedia) {
            return;
        }

        String replyName = replyingToMessage != null ? replyingToMessage.getName() : null;
        String replyText = replyingToMessage != null ? replyingToMessage.getText() : null;
        Integer replyMsgId = replyingToMessage != null ? replyingToMessage.getMsgId() : null;

        SocketManager.getInstance().sendMessage(
                text,
                pendingImages,
                pendingVideo,
                pendingAudio,
                replyName,
                replyText,
                replyMsgId
        );

        binding.etChatMessage.setText("");
        pendingImages.clear();
        pendingVideo = null;
        pendingAudio = null;
        replyingToMessage = null;
        updateMediaPreviewUI();
    }

    private void joinTopic() {
        SocketManager sm = SocketManager.getInstance();
        sm.addTopicListener(this);
        sm.addMessageListener(this);
        sm.addUserProfileListener(this);
        sm.addGeneralGlobalListener(this);
        sm.joinTopic(topicName);
    }

    // Socket Callbacks
    @Override
    public void onTopicJoined(Topic topic, List<Message> history, List<UserProfile> members, int onlineCount, boolean hasMore) {
        currentTopic = topic;
        if (topic != null) {
            isOwner = topic.isOwner();
            isLocked = topic.isLocked();
            updateLockedUI();
            binding.ivChatVerified.setVisibility(topic.isSystem() || topic.isGeneral() ? View.VISIBLE : View.GONE);
        }
        currentMembers = members != null ? members : new ArrayList<>();
        binding.tvChatOnlineCount.setText(onlineCount + " online");
        messageAdapter.setMessages(history);
        if (messageAdapter.getItemCount() > 0) {
            binding.rvChatMessages.scrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onTopicOnlineUpdated(int onlineCount, List<UserProfile> members) {
        if (members != null) currentMembers = members;
        binding.tvChatOnlineCount.setText(onlineCount + " online");
    }

    @Override
    public void onTopicStateChanged(int topicId, boolean locked, String lockedBy) {
        this.isLocked = locked;
        updateLockedUI();
    }

    private void updateLockedUI() {
        binding.bannerTopicLocked.setVisibility(isLocked ? View.VISIBLE : View.GONE);
        binding.etChatMessage.setEnabled(!isLocked);
        binding.etChatMessage.setHint(isLocked ? "Topic is locked..." : "Write a message...");
    }

    @Override
    public void onTopicDeleted(int topicId, String name) {
        Toast.makeText(this, "Topic deleted", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onHistoryLoaded(List<Message> olderHistory, boolean hasMore) {
        messageAdapter.prependMessages(olderHistory);
    }

    @Override
    public void onNewMessage(Message message) {
        boolean isAtBottom = !binding.rvChatMessages.canScrollVertically(1);
        messageAdapter.addMessage(message);
        if (isAtBottom || (messageAdapter.getItemCount() > 0 && messageAdapter.getItemViewType(messageAdapter.getItemCount() - 1) == 1)) {
            binding.rvChatMessages.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
        }
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUserProfileReceived(UserProfile userProfile) {
        UserProfileDialog dialog = new UserProfileDialog(this, userProfile);
        dialog.show();
    }

    @Override
    public void onGeneralMessageReceived(Message message) {
        if (!"General".equalsIgnoreCase(topicName)) {
            InAppNotificationBanner.show(this, message, msg -> {
                Intent intent = new Intent(ChatActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_TOPIC_NAME, "General");
                startActivity(intent);
                finish();
            });
        }
    }

    @Override public void onTopicsUpdated(List<Topic> topics) {}
    @Override public void onTopicCreated(int id, String name) {}

    public static boolean isGeneralActive = false;

    @Override
    protected void onResume() {
        super.onResume();
        if ("General".equalsIgnoreCase(topicName)) {
            isGeneralActive = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if ("General".equalsIgnoreCase(topicName)) {
            isGeneralActive = false;
        }
        AudioPlayerManager.getInstance().pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if ("General".equalsIgnoreCase(topicName)) {
            isGeneralActive = false;
        }
        AudioPlayerManager.getInstance().stop();
        SocketManager sm = SocketManager.getInstance();
        sm.leaveTopic();
        sm.removeTopicListener(this);
        sm.removeMessageListener(this);
        sm.removeUserProfileListener(this);
        sm.removeGeneralGlobalListener(this);
        com.anonymous.chat.services.ChatBackgroundService.start(this);
    }
}
