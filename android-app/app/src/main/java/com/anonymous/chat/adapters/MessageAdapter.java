package com.anonymous.chat.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.anonymous.chat.R;
import com.anonymous.chat.api.SocketManager;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.utils.AudioPlayerManager;
import com.anonymous.chat.utils.ColorHelper;
import com.anonymous.chat.utils.ImageUtils;
import com.anonymous.chat.utils.PreferenceManager;
import com.anonymous.chat.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ME = 1;
    private static final int VIEW_TYPE_OTHER = 2;

    public interface MessageInteractionListener {
        void onReply(Message message);
        void onAvatarClicked(String uid, String name, String id);
        void onMediaClicked(String mediaUrl, boolean isVideo);
        void onAudioClicked(String audioUrl);
        void onJumpToMessage(int messageId);
    }

    private final List<Message> messages = new ArrayList<>();
    private final MessageInteractionListener listener;

    public MessageAdapter(MessageInteractionListener listener) {
        this.listener = listener;
        setHasStableIds(false);
    }

    public void setMessages(List<Message> newMessages) {
        if (newMessages == null) {
            messages.clear();
            notifyDataSetChanged();
            return;
        }

        final List<Message> oldList = new ArrayList<>(messages);
        final List<Message> newList = new ArrayList<>(newMessages);

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return oldList.size(); }

            @Override
            public int getNewListSize() { return newList.size(); }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                Message o = oldList.get(oldItemPosition);
                Message n = newList.get(newItemPosition);
                if (o.getMsgId() > 0 && n.getMsgId() > 0) {
                    return o.getMsgId() == n.getMsgId();
                }
                return o.getId() != null && o.getId().equals(n.getId()) && o.getTime() != null && o.getTime().equals(n.getTime());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Message o = oldList.get(oldItemPosition);
                Message n = newList.get(newItemPosition);
                return (o.getText() != null && o.getText().equals(n.getText())) &&
                       (o.getTime() != null && o.getTime().equals(n.getTime())) &&
                       (o.getImages() != null && o.getImages().equals(n.getImages()));
            }
        });

        messages.clear();
        messages.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    public void addMessage(Message message) {
        if (message == null) return;
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void prependMessages(List<Message> olderMessages) {
        if (olderMessages == null || olderMessages.isEmpty()) return;
        messages.addAll(0, olderMessages);
        notifyItemRangeInserted(0, olderMessages.size());
    }

    public int getOldestMessageId() {
        if (messages.isEmpty()) return 0;
        return messages.get(0).getMsgId();
    }

    public int findMessagePositionById(int messageId) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getMsgId() == messageId) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = messages.get(position);
        if (msg == null) return VIEW_TYPE_OTHER;

        UserProfile myProfile = SocketManager.getInstance().getMyProfile();
        Context ctx = SocketManager.getInstance().getAppContext();
        PreferenceManager prefs = ctx != null ? PreferenceManager.getInstance(ctx) : null;

        String myUid = myProfile != null && myProfile.getUid() != null ? myProfile.getUid() : (prefs != null ? prefs.getMyUid() : null);
        String myId = myProfile != null && myProfile.getId() != null ? myProfile.getId() : (prefs != null ? prefs.getMyId() : null);
        String myName = myProfile != null && myProfile.getName() != null ? myProfile.getName() : (prefs != null ? prefs.getMyName() : null);

        String msgUid = msg.getUid();
        String msgId = msg.getId();
        String msgName = msg.getName();

        // 1. Primary check: Unique UID
        if (myUid != null && !myUid.isEmpty() && msgUid != null && !msgUid.isEmpty()) {
            return myUid.equals(msgUid) ? VIEW_TYPE_ME : VIEW_TYPE_OTHER;
        }

        // 2. Secondary check: Session Socket ID
        if (myId != null && !myId.isEmpty() && msgId != null && !msgId.isEmpty()) {
            return myId.equals(msgId) ? VIEW_TYPE_ME : VIEW_TYPE_OTHER;
        }

        // 3. Fallback check: Custom unique name (never match generic Anon)
        if (myName != null && !myName.isEmpty() && !"Anon".equalsIgnoreCase(myName) && !"Anonymous".equalsIgnoreCase(myName)) {
            if (msgName != null && myName.equalsIgnoreCase(msgName)) {
                return VIEW_TYPE_ME;
            }
        }

        return VIEW_TYPE_OTHER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_ME) {
            View v = inflater.inflate(R.layout.item_message_me, parent, false);
            return new MeViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_message_other, parent, false);
            return new OtherViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        boolean isMe = getItemViewType(position) == VIEW_TYPE_ME;

        StreakPosition streak = calculateStreak(position);
        int bubbleBg = ColorHelper.getBubbleDrawable(isMe, streak.name().toLowerCase());

        if (holder instanceof MeViewHolder) {
            ((MeViewHolder) holder).bind(msg, streak, bubbleBg, listener);
        } else if (holder instanceof OtherViewHolder) {
            ((OtherViewHolder) holder).bind(msg, streak, bubbleBg, listener);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private enum StreakPosition { ONLY, FIRST, MID, LAST }

    private StreakPosition calculateStreak(int position) {
        Message cur = messages.get(position);
        boolean prevSame = false;
        boolean nextSame = false;

        if (position > 0) {
            Message prev = messages.get(position - 1);
            if (isSameSender(prev, cur) && isWithin5Minutes(prev, cur)) {
                prevSame = true;
            }
        }

        if (position < messages.size() - 1) {
            Message next = messages.get(position + 1);
            if (isSameSender(cur, next) && isWithin5Minutes(cur, next)) {
                nextSame = true;
            }
        }

        if (prevSame && nextSame) return StreakPosition.MID;
        if (prevSame) return StreakPosition.LAST;
        if (nextSame) return StreakPosition.FIRST;
        return StreakPosition.ONLY;
    }

    private boolean isSameSender(Message a, Message b) {
        if (a == null || b == null) return false;
        if (a.getUid() != null && b.getUid() != null && !a.getUid().isEmpty()) {
            return a.getUid().equals(b.getUid());
        }
        if (a.getId() != null && b.getId() != null && !a.getId().isEmpty()) {
            return a.getId().equals(b.getId());
        }
        return a.getName() != null && a.getName().equalsIgnoreCase(b.getName());
    }

    private boolean isWithin5Minutes(Message a, Message b) {
        long tA = TimeUtils.parseIsoToMillis(a.getTime());
        long tB = TimeUtils.parseIsoToMillis(b.getTime());
        return Math.abs(tB - tA) <= 5 * 60 * 1000;
    }

    // ViewHolders
    static class MeViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout bubbleLayout;
        final TextView tvMsgBody;
        final TextView tvMsgTime;
        final ImageView btnReply;
        final LinearLayout replyQuoteBox;
        final TextView tvQuoteName;
        final TextView tvQuoteText;
        final LinearLayout mediaContainer;
        final CardView cardMsgVideo;
        final ImageView ivMsgVideoThumb;
        final VideoView vvMsgVideoInline;
        final ProgressBar pbVideoLoading;
        final ImageView btnPlayVideo;
        final ImageView btnFullscreenVideo;
        final View audioPlayerView;

        MeViewHolder(@NonNull View itemView) {
            super(itemView);
            bubbleLayout = itemView.findViewById(R.id.bubbleLayout);
            tvMsgBody = itemView.findViewById(R.id.tvMsgBody);
            tvMsgTime = itemView.findViewById(R.id.tvMsgTime);
            btnReply = itemView.findViewById(R.id.btnReplyMsg);
            replyQuoteBox = itemView.findViewById(R.id.replyQuoteBox);
            tvQuoteName = itemView.findViewById(R.id.tvQuoteName);
            tvQuoteText = itemView.findViewById(R.id.tvQuoteText);
            mediaContainer = itemView.findViewById(R.id.mediaContainer);
            cardMsgVideo = itemView.findViewById(R.id.cardMsgVideo);
            ivMsgVideoThumb = itemView.findViewById(R.id.ivMsgVideoThumb);
            vvMsgVideoInline = itemView.findViewById(R.id.vvMsgVideoInline);
            pbVideoLoading = itemView.findViewById(R.id.pbVideoLoading);
            btnPlayVideo = itemView.findViewById(R.id.btnPlayVideo);
            btnFullscreenVideo = itemView.findViewById(R.id.btnFullscreenVideo);
            audioPlayerView = itemView.findViewById(R.id.audioPlayerView);
        }

        void bind(Message msg, StreakPosition streak, int bubbleBgRes, MessageInteractionListener listener) {
            bubbleLayout.setBackgroundResource(bubbleBgRes);

            // Message text
            if (msg.getText() != null && !msg.getText().isEmpty()) {
                tvMsgBody.setVisibility(View.VISIBLE);
                tvMsgBody.setText(msg.getText());
            } else {
                tvMsgBody.setVisibility(View.GONE);
            }

            // Reply Quote Box
            if (msg.getReplyName() != null && !msg.getReplyName().isEmpty()) {
                replyQuoteBox.setVisibility(View.VISIBLE);
                tvQuoteName.setText(msg.getReplyName());
                tvQuoteText.setText(msg.getReplyText() != null ? msg.getReplyText() : "");
                replyQuoteBox.setOnClickListener(v -> {
                    if (listener != null && msg.getReplyMsgId() != null) {
                        listener.onJumpToMessage(msg.getReplyMsgId());
                    }
                });
            } else {
                replyQuoteBox.setVisibility(View.GONE);
            }

            // Media Images
            mediaContainer.removeAllViews();
            List<String> images = msg.getImages();
            if (images != null && !images.isEmpty()) {
                mediaContainer.setVisibility(View.VISIBLE);
                float density = itemView.getContext().getResources().getDisplayMetrics().density;
                for (String imgUrl : images) {
                    ImageView imgView = new ImageView(itemView.getContext());
                    int heightPx = (int) (180 * density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
                    lp.setMargins(0, (int) (3 * density), 0, (int) (3 * density));
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                    ImageUtils.loadImage(itemView.getContext(), imgUrl, imgView, 8);
                    imgView.setOnClickListener(v -> {
                        if (listener != null) listener.onMediaClicked(imgUrl, false);
                    });
                    mediaContainer.addView(imgView);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }

            // Video Inline Player
            if (msg.getVideo() != null && !msg.getVideo().isEmpty()) {
                cardMsgVideo.setVisibility(View.VISIBLE);
                String videoUrl = msg.getVideo();
                ImageUtils.loadImage(itemView.getContext(), videoUrl, ivMsgVideoThumb, 10);

                vvMsgVideoInline.setVisibility(View.GONE);
                ivMsgVideoThumb.setVisibility(View.VISIBLE);
                btnPlayVideo.setVisibility(View.VISIBLE);
                btnPlayVideo.setImageResource(R.drawable.ic_play);
                btnFullscreenVideo.setVisibility(View.GONE);
                pbVideoLoading.setVisibility(View.GONE);

                View.OnClickListener videoClickListener = v -> {
                    if (vvMsgVideoInline.isPlaying()) {
                        // 2nd Click -> Launch Fullscreen
                        if (listener != null) {
                            listener.onMediaClicked(videoUrl, true);
                        }
                    } else {
                        // 1st Click -> Play Inline
                        pbVideoLoading.setVisibility(View.VISIBLE);
                        btnPlayVideo.setVisibility(View.GONE);

                        try {
                            if (videoUrl.startsWith("data:video/")) {
                                java.io.File temp = ImageUtils.saveBase64ToCacheFile(itemView.getContext(), videoUrl, "vid_", ".mp4");
                                if (temp != null) {
                                    vvMsgVideoInline.setVideoPath(temp.getAbsolutePath());
                                } else {
                                    vvMsgVideoInline.setVideoURI(Uri.parse(videoUrl));
                                }
                            } else {
                                String serverUrl = PreferenceManager.getInstance(itemView.getContext()).getServerBaseUrl();
                                String full = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
                                vvMsgVideoInline.setVideoURI(Uri.parse(full));
                            }

                            vvMsgVideoInline.setOnPreparedListener(mp -> {
                                pbVideoLoading.setVisibility(View.GONE);
                                ivMsgVideoThumb.setVisibility(View.GONE);
                                vvMsgVideoInline.setVisibility(View.VISIBLE);
                                btnFullscreenVideo.setVisibility(View.VISIBLE);
                                mp.setLooping(true);
                                mp.start();
                            });

                            vvMsgVideoInline.setOnErrorListener((mp, what, extra) -> {
                                pbVideoLoading.setVisibility(View.GONE);
                                btnPlayVideo.setVisibility(View.VISIBLE);
                                ivMsgVideoThumb.setVisibility(View.VISIBLE);
                                vvMsgVideoInline.setVisibility(View.GONE);
                                if (listener != null) listener.onMediaClicked(videoUrl, true);
                                return true;
                            });
                        } catch (Exception e) {
                            pbVideoLoading.setVisibility(View.GONE);
                            btnPlayVideo.setVisibility(View.VISIBLE);
                        }
                    }
                };

                btnPlayVideo.setOnClickListener(videoClickListener);
                ivMsgVideoThumb.setOnClickListener(videoClickListener);
                vvMsgVideoInline.setOnClickListener(videoClickListener);

                btnFullscreenVideo.setOnClickListener(v -> {
                    if (listener != null) listener.onMediaClicked(videoUrl, true);
                });
            } else {
                cardMsgVideo.setVisibility(View.GONE);
            }

            // Audio
            if (msg.getAudio() != null && !msg.getAudio().isEmpty()) {
                audioPlayerView.setVisibility(View.VISIBLE);
                setupAudioPlayer(audioPlayerView, msg.getAudio(), listener);
            } else {
                audioPlayerView.setVisibility(View.GONE);
            }

            // Timestamp
            String tz = PreferenceManager.getInstance(itemView.getContext()).getTimezone();
            tvMsgTime.setText(TimeUtils.formatMessageTime(msg.getTime(), tz));
            tvMsgTime.setVisibility((streak == StreakPosition.ONLY || streak == StreakPosition.LAST) ? View.VISIBLE : View.GONE);

            // Reply button
            btnReply.setOnClickListener(v -> {
                if (listener != null) listener.onReply(msg);
            });
        }
    }

    static class OtherViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final LinearLayout bubbleLayout;
        final TextView tvMsgBody;
        final TextView tvMsgTime;
        final ImageView btnReply;
        final LinearLayout replyQuoteBox;
        final TextView tvQuoteName;
        final TextView tvQuoteText;
        final LinearLayout mediaContainer;
        final CardView cardMsgVideo;
        final ImageView ivMsgVideoThumb;
        final VideoView vvMsgVideoInline;
        final ProgressBar pbVideoLoading;
        final ImageView btnPlayVideo;
        final ImageView btnFullscreenVideo;
        final View audioPlayerView;

        OtherViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivMsgAvatar);
            tvName = itemView.findViewById(R.id.tvMsgName);
            bubbleLayout = itemView.findViewById(R.id.bubbleLayout);
            tvMsgBody = itemView.findViewById(R.id.tvMsgBody);
            tvMsgTime = itemView.findViewById(R.id.tvMsgTime);
            btnReply = itemView.findViewById(R.id.btnReplyMsg);
            replyQuoteBox = itemView.findViewById(R.id.replyQuoteBox);
            tvQuoteName = itemView.findViewById(R.id.tvQuoteName);
            tvQuoteText = itemView.findViewById(R.id.tvQuoteText);
            mediaContainer = itemView.findViewById(R.id.mediaContainer);
            cardMsgVideo = itemView.findViewById(R.id.cardMsgVideo);
            ivMsgVideoThumb = itemView.findViewById(R.id.ivMsgVideoThumb);
            vvMsgVideoInline = itemView.findViewById(R.id.vvMsgVideoInline);
            pbVideoLoading = itemView.findViewById(R.id.pbVideoLoading);
            btnPlayVideo = itemView.findViewById(R.id.btnPlayVideo);
            btnFullscreenVideo = itemView.findViewById(R.id.btnFullscreenVideo);
            audioPlayerView = itemView.findViewById(R.id.audioPlayerView);
        }

        void bind(Message msg, StreakPosition streak, int bubbleBgRes, MessageInteractionListener listener) {
            bubbleLayout.setBackgroundResource(bubbleBgRes);

            // Hide/Show Name and Avatar based on streak
            if (streak == StreakPosition.ONLY || streak == StreakPosition.FIRST) {
                tvName.setVisibility(View.VISIBLE);
                tvName.setText(msg.getName() != null ? msg.getName() : "Anon");
                try {
                    if (msg.getColor() != null && msg.getColor().startsWith("#")) {
                        tvName.setTextColor(Color.parseColor(msg.getColor()));
                    } else {
                        tvName.setTextColor(itemView.getContext().getResources().getColor(R.color.text_muted));
                    }
                } catch (Exception ignored) {}
                ivAvatar.setVisibility(View.VISIBLE);
            } else {
                tvName.setVisibility(View.GONE);
                ivAvatar.setVisibility(View.INVISIBLE);
            }

            // Avatar Gradient & Image
            GradientDrawable grad = ColorHelper.getAvatarGradient(msg.getColor());
            ivAvatar.setBackground(grad);
            if (msg.getAvatar() != null && !msg.getAvatar().isEmpty()) {
                ImageUtils.loadAvatar(itemView.getContext(), msg.getAvatar(), ivAvatar);
            }

            View.OnClickListener profileClick = v -> {
                if (listener != null) listener.onAvatarClicked(msg.getUid(), msg.getName(), msg.getId());
            };
            ivAvatar.setOnClickListener(profileClick);
            tvName.setOnClickListener(profileClick);

            // Message text
            if (msg.getText() != null && !msg.getText().isEmpty()) {
                tvMsgBody.setVisibility(View.VISIBLE);
                tvMsgBody.setText(msg.getText());
            } else {
                tvMsgBody.setVisibility(View.GONE);
            }

            // Reply Quote Box
            if (msg.getReplyName() != null && !msg.getReplyName().isEmpty()) {
                replyQuoteBox.setVisibility(View.VISIBLE);
                tvQuoteName.setText(msg.getReplyName());
                tvQuoteText.setText(msg.getReplyText() != null ? msg.getReplyText() : "");
                replyQuoteBox.setOnClickListener(v -> {
                    if (listener != null && msg.getReplyMsgId() != null) {
                        listener.onJumpToMessage(msg.getReplyMsgId());
                    }
                });
            } else {
                replyQuoteBox.setVisibility(View.GONE);
            }

            // Media Images
            mediaContainer.removeAllViews();
            List<String> images = msg.getImages();
            if (images != null && !images.isEmpty()) {
                mediaContainer.setVisibility(View.VISIBLE);
                float density = itemView.getContext().getResources().getDisplayMetrics().density;
                for (String imgUrl : images) {
                    ImageView imgView = new ImageView(itemView.getContext());
                    int heightPx = (int) (180 * density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
                    lp.setMargins(0, (int) (3 * density), 0, (int) (3 * density));
                    imgView.setLayoutParams(lp);
                    imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                    ImageUtils.loadImage(itemView.getContext(), imgUrl, imgView, 8);
                    imgView.setOnClickListener(v -> {
                        if (listener != null) listener.onMediaClicked(imgUrl, false);
                    });
                    mediaContainer.addView(imgView);
                }
            } else {
                mediaContainer.setVisibility(View.GONE);
            }

            // Video Inline Player
            if (msg.getVideo() != null && !msg.getVideo().isEmpty()) {
                cardMsgVideo.setVisibility(View.VISIBLE);
                String videoUrl = msg.getVideo();
                ImageUtils.loadImage(itemView.getContext(), videoUrl, ivMsgVideoThumb, 10);

                vvMsgVideoInline.setVisibility(View.GONE);
                ivMsgVideoThumb.setVisibility(View.VISIBLE);
                btnPlayVideo.setVisibility(View.VISIBLE);
                btnPlayVideo.setImageResource(R.drawable.ic_play);
                btnFullscreenVideo.setVisibility(View.GONE);
                pbVideoLoading.setVisibility(View.GONE);

                View.OnClickListener videoClickListener = v -> {
                    if (vvMsgVideoInline.isPlaying()) {
                        // 2nd Click -> Launch Fullscreen
                        if (listener != null) {
                            listener.onMediaClicked(videoUrl, true);
                        }
                    } else {
                        // 1st Click -> Play Inline
                        pbVideoLoading.setVisibility(View.VISIBLE);
                        btnPlayVideo.setVisibility(View.GONE);

                        try {
                            if (videoUrl.startsWith("data:video/")) {
                                java.io.File temp = ImageUtils.saveBase64ToCacheFile(itemView.getContext(), videoUrl, "vid_", ".mp4");
                                if (temp != null) {
                                    vvMsgVideoInline.setVideoPath(temp.getAbsolutePath());
                                } else {
                                    vvMsgVideoInline.setVideoURI(Uri.parse(videoUrl));
                                }
                            } else {
                                String serverUrl = PreferenceManager.getInstance(itemView.getContext()).getServerBaseUrl();
                                String full = ImageUtils.getFullMediaUrl(serverUrl, videoUrl);
                                vvMsgVideoInline.setVideoURI(Uri.parse(full));
                            }

                            vvMsgVideoInline.setOnPreparedListener(mp -> {
                                pbVideoLoading.setVisibility(View.GONE);
                                ivMsgVideoThumb.setVisibility(View.GONE);
                                vvMsgVideoInline.setVisibility(View.VISIBLE);
                                btnFullscreenVideo.setVisibility(View.VISIBLE);
                                mp.setLooping(true);
                                mp.start();
                            });

                            vvMsgVideoInline.setOnErrorListener((mp, what, extra) -> {
                                pbVideoLoading.setVisibility(View.GONE);
                                btnPlayVideo.setVisibility(View.VISIBLE);
                                ivMsgVideoThumb.setVisibility(View.VISIBLE);
                                vvMsgVideoInline.setVisibility(View.GONE);
                                if (listener != null) listener.onMediaClicked(videoUrl, true);
                                return true;
                            });
                        } catch (Exception e) {
                            pbVideoLoading.setVisibility(View.GONE);
                            btnPlayVideo.setVisibility(View.VISIBLE);
                        }
                    }
                };

                btnPlayVideo.setOnClickListener(videoClickListener);
                ivMsgVideoThumb.setOnClickListener(videoClickListener);
                vvMsgVideoInline.setOnClickListener(videoClickListener);

                btnFullscreenVideo.setOnClickListener(v -> {
                    if (listener != null) listener.onMediaClicked(videoUrl, true);
                });
            } else {
                cardMsgVideo.setVisibility(View.GONE);
            }

            // Audio
            if (msg.getAudio() != null && !msg.getAudio().isEmpty()) {
                audioPlayerView.setVisibility(View.VISIBLE);
                setupAudioPlayer(audioPlayerView, msg.getAudio(), listener);
            } else {
                audioPlayerView.setVisibility(View.GONE);
            }

            // Timestamp
            String tz = PreferenceManager.getInstance(itemView.getContext()).getTimezone();
            tvMsgTime.setText(TimeUtils.formatMessageTime(msg.getTime(), tz));
            tvMsgTime.setVisibility((streak == StreakPosition.ONLY || streak == StreakPosition.LAST) ? View.VISIBLE : View.GONE);

            // Reply button
            btnReply.setOnClickListener(v -> {
                if (listener != null) listener.onReply(msg);
            });
        }
    }

    private static void setupAudioPlayer(View view, String audioUrl, MessageInteractionListener listener) {
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
                if (url != null && url.equals(audioUrl)) btnPlay.setImageResource(R.drawable.ic_pause);
            }

            @Override
            public void onPause(String url) {
                if (url != null && url.equals(audioUrl)) btnPlay.setImageResource(R.drawable.ic_play);
            }

            @Override
            public void onStop(String url) {
                if (url != null && url.equals(audioUrl)) {
                    btnPlay.setImageResource(R.drawable.ic_play);
                    seekBar.setProgress(0);
                    tvCurrent.setText("0:00");
                }
            }

            @Override
            public void onProgress(String url, int currentMs, int durationMs) {
                if (url != null && url.equals(audioUrl)) {
                    if (durationMs > 0) {
                        int progress = (int) (((float) currentMs / durationMs) * 100);
                        seekBar.setProgress(progress);
                    }
                    tvCurrent.setText(formatDuration(currentMs));
                    tvDuration.setText(formatDuration(durationMs));
                }
            }

            @Override
            public void onError(String url, String error) {
                if (url != null && url.equals(audioUrl)) {
                    btnPlay.setImageResource(R.drawable.ic_play);
                }
            }
        });
    }

    private static String formatDuration(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }
}
