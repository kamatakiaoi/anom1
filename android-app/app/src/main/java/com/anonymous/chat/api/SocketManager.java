package com.anonymous.chat.api;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.anonymous.chat.models.Comment;
import com.anonymous.chat.models.Message;
import com.anonymous.chat.models.Post;
import com.anonymous.chat.models.ServerStats;
import com.anonymous.chat.models.Topic;
import com.anonymous.chat.models.UserProfile;
import com.anonymous.chat.utils.NotificationHelper;
import com.anonymous.chat.utils.PreferenceManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketManager {
    private static final String TAG = "SocketManager";
    private static SocketManager instance;

    private Socket socket;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentServerUrl = "";
    private String currentTopicName = "";
    private UserProfile myProfile;
    private ServerStats serverStats;

    // Ping tracking
    private long pingStartTime = 0;
    private long lastLatencyMs = 0;
    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            startPingMeasurement();
            mainHandler.postDelayed(this, 5000);
        }
    };

    // Track last seen General message ID to avoid duplicate notifications
    private int lastSeenGeneralMsgId = 0;

    // Listeners
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onConnectionError(String error);
        void onPingUpdated(long latencyMs);
        void onStatsUpdated(ServerStats stats);
    }

    public interface AuthListener {
        void onAuthError(String message);
        void onKeyCreated(String recoveryKey);
        void onKeyRecovered(String key);
    }

    public interface TopicListener {
        void onTopicsUpdated(List<Topic> topics);
        void onTopicCreated(int id, String name);
        void onTopicJoined(Topic topic, List<Message> history, List<UserProfile> members, int onlineCount, boolean hasMore);
        void onTopicOnlineUpdated(int onlineCount, List<UserProfile> members);
        void onTopicStateChanged(int topicId, boolean locked, String lockedBy);
        void onTopicDeleted(int topicId, String name);
        void onHistoryLoaded(List<Message> olderHistory, boolean hasMore);
    }

    public interface MessageListener {
        void onNewMessage(Message message);
        void onError(String message);
    }

    public interface ProfileListener {
        void onProfileLoaded(UserProfile profile);
        void onNameChanged(String newName);
        void onAvatarChanged(String newAvatarUrl);
    }

    public interface ExploreListener {
        void onFeedLoaded(int page, int totalPages, int total, List<Post> posts, boolean hasMore);
        void onPostCreated(Post post);
        void onPostVoteUpdated(int postId, int upvotes, int downvotes, int score);
        void onPostVoteMeUpdated(int postId, int upvotes, int downvotes, int score, int myVote);
        void onPostViewsUpdated(int postId, int views);
        void onPostSharesUpdated(int postId, int shares);
        void onNewComment(Comment comment);
        void onCommentCountUpdated(int postId, int commentsCount);
        void onCommentsLoaded(int postId, List<Comment> comments);
        void onPostDeleted(int postId);
        void onPostDetailLoaded(Post post);
    }

    public interface GeneralMessageGlobalListener {
        void onGeneralMessageReceived(Message message);
    }

    public interface UserProfileDialogListener {
        void onUserProfileReceived(UserProfile userProfile);
    }

    private final List<ConnectionListener> connectionListeners = new CopyOnWriteArrayList<>();
    private final List<AuthListener> authListeners = new CopyOnWriteArrayList<>();
    private final List<TopicListener> topicListeners = new CopyOnWriteArrayList<>();
    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<ProfileListener> profileListeners = new CopyOnWriteArrayList<>();
    private final List<ExploreListener> exploreListeners = new CopyOnWriteArrayList<>();
    private final List<GeneralMessageGlobalListener> generalGlobalListeners = new CopyOnWriteArrayList<>();
    private final List<UserProfileDialogListener> userProfileListeners = new CopyOnWriteArrayList<>();

    private Context appContext;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }

    public void init(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void addConnectionListener(ConnectionListener l) { connectionListeners.add(l); }
    public void removeConnectionListener(ConnectionListener l) { connectionListeners.remove(l); }

    public void addAuthListener(AuthListener l) { authListeners.add(l); }
    public void removeAuthListener(AuthListener l) { authListeners.remove(l); }

    public void addTopicListener(TopicListener l) { topicListeners.add(l); }
    public void removeTopicListener(TopicListener l) { topicListeners.remove(l); }

    public void addMessageListener(MessageListener l) { messageListeners.add(l); }
    public void removeMessageListener(MessageListener l) { messageListeners.remove(l); }

    public void addProfileListener(ProfileListener l) { profileListeners.add(l); }
    public void removeProfileListener(ProfileListener l) { profileListeners.remove(l); }

    public void addExploreListener(ExploreListener l) { exploreListeners.add(l); }
    public void removeExploreListener(ExploreListener l) { exploreListeners.remove(l); }

    public void addGeneralGlobalListener(GeneralMessageGlobalListener l) { generalGlobalListeners.add(l); }
    public void removeGeneralGlobalListener(GeneralMessageGlobalListener l) { generalGlobalListeners.remove(l); }

    public void addUserProfileListener(UserProfileDialogListener l) { userProfileListeners.add(l); }
    public void removeUserProfileListener(UserProfileDialogListener l) { userProfileListeners.remove(l); }

    public UserProfile getMyProfile() { return myProfile; }
    public ServerStats getServerStats() { return serverStats; }
    public boolean isConnected() { return socket != null && socket.connected(); }
    public String getCurrentTopicName() { return currentTopicName; }

    public void connect(String serverUrl) {
        if (socket != null) {
            socket.disconnect();
            socket.off();
        }

        this.currentServerUrl = serverUrl;
        try {
            String deviceMac = appContext != null
                    ? PreferenceManager.getInstance(appContext).getDeviceMac()
                    : "MAC-00:00:00:00:00:00";

            IO.Options options = IO.Options.builder()
                    .setQuery("mac=" + deviceMac)
                    .setTransports(new String[]{"websocket", "polling"})
                    .setReconnection(true)
                    .setReconnectionAttempts(Integer.MAX_VALUE)
                    .setReconnectionDelay(300)
                    .setReconnectionDelayMax(1500)
                    .setTimeout(10000)
                    .build();

            socket = IO.socket(URI.create(serverUrl), options);
            setupSocketEvents();
            socket.connect();
        } catch (Exception e) {
            Log.e(TAG, "Connection error", e);
            notifyConnectionError(e.getMessage());
        }
    }

    public void disconnect() {
        mainHandler.removeCallbacks(pingRunnable);
        if (socket != null) {
            socket.disconnect();
            socket.off();
            socket = null;
        }
    }

    private void setupSocketEvents() {
        if (socket == null) return;

        socket.on(Socket.EVENT_CONNECT, args -> {
            mainHandler.post(() -> {
                for (ConnectionListener l : connectionListeners) l.onConnected();
                mainHandler.removeCallbacks(pingRunnable);
                mainHandler.post(pingRunnable);
            });

            // Re-authenticate and join General room on every connect/reconnect
            if (appContext != null) {
                PreferenceManager prefs = PreferenceManager.getInstance(appContext);
                String savedKey = prefs.getAuthKey();
                if (savedKey != null && !savedKey.isEmpty()) {
                    authKey(savedKey);
                }
            }
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            mainHandler.post(() -> {
                mainHandler.removeCallbacks(pingRunnable);
                for (ConnectionListener l : connectionListeners) l.onDisconnected();
            });
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String err = (args.length > 0 && args[0] != null) ? args[0].toString() : "Connection Error";
            mainHandler.post(() -> {
                for (ConnectionListener l : connectionListeners) l.onConnectionError(err);
            });
        });

        // Ping check
        socket.on("pong-check", args -> {
            if (pingStartTime > 0) {
                lastLatencyMs = System.currentTimeMillis() - pingStartTime;
                pingStartTime = 0;
                mainHandler.post(() -> {
                    for (ConnectionListener l : connectionListeners) l.onPingUpdated(lastLatencyMs);
                });
            }
        });

        // Auth handling
        socket.on("auto-auth", args -> {
            if (args.length > 0 && args[0] != null && appContext != null) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String key = obj.optString("key");
                    if (key != null && !key.isEmpty()) {
                        PreferenceManager.getInstance(appContext).setAuthKey(key);
                    }
                } catch (Exception ignored) {}
            }
        });

        socket.on("require-auth", args -> {
            if (appContext != null) {
                PreferenceManager prefs = PreferenceManager.getInstance(appContext);
                String savedKey = prefs.getAuthKey();
                if (savedKey != null && !savedKey.isEmpty()) {
                    authKey(savedKey);
                }
            }
        });

        socket.on("auth-error", args -> {
            String msg = (args.length > 0 && args[0] != null) ? args[0].toString() : "Invalid key";
            try {
                if (args[0] instanceof JSONObject) {
                    msg = ((JSONObject) args[0]).optString("message", msg);
                }
            } catch (Exception ignored) {}
            String finalMsg = msg;
            mainHandler.post(() -> {
                for (AuthListener l : authListeners) l.onAuthError(finalMsg);
            });
        });

        socket.on("key-created", args -> {
            if (args.length > 0 && args[0] != null && appContext != null) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String recovery = obj.optString("recoveryKey");
                    if (recovery != null && !recovery.isEmpty()) {
                        PreferenceManager.getInstance(appContext).setRecoveryKey(recovery);
                    }
                    mainHandler.post(() -> {
                        for (AuthListener l : authListeners) l.onKeyCreated(recovery);
                    });
                } catch (Exception ignored) {}
            }
        });

        socket.on("key-recovered", args -> {
            if (args.length > 0 && args[0] != null && appContext != null) {
                try {
                    JSONObject obj = (JSONObject) args[0];
                    String key = obj.optString("key");
                    if (key != null && !key.isEmpty()) {
                        PreferenceManager.getInstance(appContext).setAuthKey(key);
                    }
                    mainHandler.post(() -> {
                        for (AuthListener l : authListeners) l.onKeyRecovered(key);
                    });
                } catch (Exception ignored) {}
            }
        });

        socket.on("profile", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                myProfile = gson.fromJson(obj.toString(), UserProfile.class);
                mainHandler.post(() -> {
                    for (ProfileListener l : profileListeners) l.onProfileLoaded(myProfile);
                });
                joinTopic("General");
            } catch (Exception e) {
                Log.e(TAG, "profile error", e);
            }
        });

        socket.on("topics", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONArray arr = (JSONArray) args[0];
                List<Topic> list = gson.fromJson(arr.toString(), new TypeToken<List<Topic>>(){}.getType());
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onTopicsUpdated(list);
                });

                // Check for new General messages from topics update when not in General
                if (!"General".equalsIgnoreCase(currentTopicName) && list != null) {
                    for (Topic t : list) {
                        if ("General".equalsIgnoreCase(t.getName()) && t.getLastMsg() != null) {
                            Topic.LastMessage lm = t.getLastMsg();
                            int msgId = lm.getId() != 0 ? lm.getId() : t.getLastMsgId();
                            if (msgId > lastSeenGeneralMsgId && lastSeenGeneralMsgId > 0) {
                                String myName = myProfile != null ? myProfile.getName() : "";
                                if (lm.getName() != null && !myName.equalsIgnoreCase(lm.getName())) {
                                    if (appContext != null) {
                                        NotificationHelper.showGeneralTopicNotification(appContext, lm.getName(), lm.getText());
                                    }
                                }
                            }
                            if (msgId > lastSeenGeneralMsgId) {
                                lastSeenGeneralMsgId = msgId;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "topics error", e);
            }
        });

        socket.on("stats", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                serverStats = gson.fromJson(obj.toString(), ServerStats.class);
                mainHandler.post(() -> {
                    for (ConnectionListener l : connectionListeners) l.onStatsUpdated(serverStats);
                });
            } catch (Exception e) {
                Log.e(TAG, "stats error", e);
            }
        });

        socket.on("topic-created", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                String name = obj.optString("name");
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onTopicCreated(id, name);
                });
            } catch (Exception e) {
                Log.e(TAG, "topic-created error", e);
            }
        });

        socket.on("joined", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                JSONObject topicObj = obj.optJSONObject("topic");
                Topic topic = topicObj != null ? gson.fromJson(topicObj.toString(), Topic.class) : null;
                JSONArray historyArr = obj.optJSONArray("history");
                List<Message> history = historyArr != null
                        ? gson.fromJson(historyArr.toString(), new TypeToken<List<Message>>(){}.getType())
                        : new ArrayList<>();
                JSONArray membersArr = obj.optJSONArray("members");
                List<UserProfile> members = membersArr != null
                        ? gson.fromJson(membersArr.toString(), new TypeToken<List<UserProfile>>(){}.getType())
                        : new ArrayList<>();
                int onlineCount = obj.optInt("topicOnline", members.size());
                boolean hasMore = obj.optBoolean("hasMore", false);

                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) {
                        l.onTopicJoined(topic, history, members, onlineCount, hasMore);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "joined error", e);
            }
        });

        socket.on("topic-online", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int count = obj.optInt("online");
                JSONArray membersArr = obj.optJSONArray("members");
                List<UserProfile> members = membersArr != null
                        ? gson.fromJson(membersArr.toString(), new TypeToken<List<UserProfile>>(){}.getType())
                        : new ArrayList<>();
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onTopicOnlineUpdated(count, members);
                });
            } catch (Exception e) {
                Log.e(TAG, "topic-online error", e);
            }
        });

        socket.on("topic-state", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                boolean locked = obj.optBoolean("locked");
                String lockedBy = obj.optString("lockedBy");
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onTopicStateChanged(id, locked, lockedBy);
                });
            } catch (Exception e) {
                Log.e(TAG, "topic-state error", e);
            }
        });

        socket.on("topic-deleted", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                String name = obj.optString("name");
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onTopicDeleted(id, name);
                });
            } catch (Exception e) {
                Log.e(TAG, "topic-deleted error", e);
            }
        });

        socket.on("history-page", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                JSONArray historyArr = obj.optJSONArray("history");
                List<Message> history = historyArr != null
                        ? gson.fromJson(historyArr.toString(), new TypeToken<List<Message>>(){}.getType())
                        : new ArrayList<>();
                boolean hasMore = obj.optBoolean("hasMore", false);
                mainHandler.post(() -> {
                    for (TopicListener l : topicListeners) l.onHistoryLoaded(history, hasMore);
                });
            } catch (Exception e) {
                Log.e(TAG, "history-page error", e);
            }
        });

        socket.on("message", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                Message msg = gson.fromJson(obj.toString(), Message.class);

                mainHandler.post(() -> {
                    for (MessageListener l : messageListeners) l.onNewMessage(msg);
                });

                String senderName = msg.getName();
                String myName = myProfile != null ? myProfile.getName() : "";
                boolean isMe = (myProfile != null && msg.getId().equals(myProfile.getId())) || myName.equalsIgnoreCase(senderName);

                if (!"General".equalsIgnoreCase(currentTopicName) && !isMe) {
                    mainHandler.post(() -> {
                        for (GeneralMessageGlobalListener l : generalGlobalListeners) {
                            l.onGeneralMessageReceived(msg);
                        }
                    });
                    if (appContext != null) {
                        NotificationHelper.showGeneralMessageNotification(appContext, msg);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "message error", e);
            }
        });

        socket.on("name-ok", args -> {
            if (args.length == 0 || args[0] == null) return;
            String name = args[0].toString();
            if (myProfile != null) myProfile.setName(name);
            mainHandler.post(() -> {
                for (ProfileListener l : profileListeners) l.onNameChanged(name);
            });
        });

        socket.on("avatar-ok", args -> {
            if (args.length == 0 || args[0] == null) return;
            String avatar = args[0].toString();
            if (myProfile != null) myProfile.setAvatar(avatar);
            mainHandler.post(() -> {
                for (ProfileListener l : profileListeners) l.onAvatarChanged(avatar);
            });
        });

        socket.on("user-profile", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                UserProfile p = gson.fromJson(obj.toString(), UserProfile.class);
                mainHandler.post(() -> {
                    for (UserProfileDialogListener l : userProfileListeners) {
                        l.onUserProfileReceived(p);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "user-profile error", e);
            }
        });

        socket.on("error", args -> {
            String err = (args.length > 0 && args[0] != null) ? args[0].toString() : "Unknown error";
            mainHandler.post(() -> {
                for (MessageListener l : messageListeners) l.onError(err);
            });
        });

        // Explore Feed Events
        socket.on("explore-feed", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int page = obj.optInt("page", 1);
                int totalPages = obj.optInt("totalPages", 1);
                int total = obj.optInt("total", 0);
                boolean hasMore = obj.optBoolean("hasMore", false);
                JSONArray postsArr = obj.optJSONArray("posts");
                List<Post> posts = postsArr != null
                        ? gson.fromJson(postsArr.toString(), new TypeToken<List<Post>>(){}.getType())
                        : new ArrayList<>();
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onFeedLoaded(page, totalPages, total, posts, hasMore);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-feed error", e);
            }
        });

        socket.on("explore-post", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                Post post = gson.fromJson(obj.toString(), Post.class);
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostCreated(post);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-post error", e);
            }
        });

        socket.on("explore-created", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                Post post = gson.fromJson(obj.toString(), Post.class);
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostCreated(post);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-created error", e);
            }
        });

        socket.on("explore-vote-update", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                int up = obj.optInt("upvotes");
                int down = obj.optInt("downvotes");
                int score = obj.optInt("score");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostVoteUpdated(id, up, down, score);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-vote-update error", e);
            }
        });

        socket.on("explore-vote-me", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                int up = obj.optInt("upvotes");
                int down = obj.optInt("downvotes");
                int score = obj.optInt("score");
                int myVote = obj.optInt("myVote");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostVoteMeUpdated(id, up, down, score, myVote);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-vote-me error", e);
            }
        });

        socket.on("explore-view-update", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                int views = obj.optInt("views");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostViewsUpdated(id, views);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-view-update error", e);
            }
        });

        socket.on("explore-share-update", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                int shares = obj.optInt("shares");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostSharesUpdated(id, shares);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-share-update error", e);
            }
        });

        socket.on("explore-comment", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                Comment comment = gson.fromJson(obj.toString(), Comment.class);
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onNewComment(comment);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-comment error", e);
            }
        });

        socket.on("explore-comment-count", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                int count = obj.optInt("comments");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onCommentCountUpdated(id, count);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-comment-count error", e);
            }
        });

        socket.on("explore-comments", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("postId");
                JSONArray arr = obj.optJSONArray("comments");
                List<Comment> comments = arr != null
                        ? gson.fromJson(arr.toString(), new TypeToken<List<Comment>>(){}.getType())
                        : new ArrayList<>();
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onCommentsLoaded(id, comments);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-comments error", e);
            }
        });

        socket.on("explore-deleted", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                int id = obj.optInt("id");
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostDeleted(id);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-deleted error", e);
            }
        });

        socket.on("explore-get", args -> {
            if (args.length == 0 || args[0] == null) return;
            try {
                JSONObject obj = (JSONObject) args[0];
                Post post = gson.fromJson(obj.toString(), Post.class);
                mainHandler.post(() -> {
                    for (ExploreListener l : exploreListeners) l.onPostDetailLoaded(post);
                });
            } catch (Exception e) {
                Log.e(TAG, "explore-get error", e);
            }
        });

        socket.on("force-logout", args -> {
            mainHandler.post(() -> {
                logout();
                if (appContext != null) {
                    android.widget.Toast.makeText(appContext, "Logged out by administrator", android.widget.Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(appContext, com.anonymous.chat.ui.auth.AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    appContext.startActivity(intent);
                }
            });
        });
    }

    public void startPingMeasurement() {
        if (socket == null || !socket.connected()) return;
        pingStartTime = System.currentTimeMillis();
        socket.emit("ping-check", pingStartTime);
    }

    public void authKey(String key) {
        if (socket == null || !socket.connected() || key == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("key", key);
            if (appContext != null) {
                obj.put("mac", PreferenceManager.getInstance(appContext).getDeviceMac());
            }
            socket.emit("auth-key", obj);
        } catch (Exception e) {
            Log.e(TAG, "authKey error", e);
        }
    }

    public void createKey(String key) {
        if (socket == null || !socket.connected() || key == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("key", key);
            if (appContext != null) {
                obj.put("mac", PreferenceManager.getInstance(appContext).getDeviceMac());
            }
            socket.emit("create-key", obj);
            if (appContext != null) {
                PreferenceManager.getInstance(appContext).setAuthKey(key);
            }
        } catch (Exception e) {
            Log.e(TAG, "createKey error", e);
        }
    }

    public void recoverKey(String recoveryKey) {
        if (socket == null || !socket.connected() || recoveryKey == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("recoveryKey", recoveryKey);
            socket.emit("recover-key", obj);
        } catch (Exception e) {
            Log.e(TAG, "recoverKey error", e);
        }
    }

    public void logout() {
        if (socket != null && socket.connected()) {
            socket.emit("logout");
        }
        if (appContext != null) {
            PreferenceManager.getInstance(appContext).setAuthKey(null);
        }
        myProfile = null;
    }

    public void joinTopic(String topicName) {
        this.currentTopicName = topicName;
        if (socket != null && socket.connected()) {
            socket.emit("join-topic", topicName);
        }
    }

    public void leaveTopic() {
        if (socket != null && socket.connected()) {
            socket.emit("leave-topic");
        }
        currentTopicName = "";
    }

    public void sendMessage(String text, List<String> images, String video, String audio, String replyName, String replyText, Integer replyMsgId) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("text", text != null ? text : "");
            if (images != null && !images.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String img : images) arr.put(img);
                obj.put("images", arr);
            }
            if (video != null && !video.isEmpty()) {
                obj.put("video", video);
            }
            if (audio != null && !audio.isEmpty()) {
                obj.put("audio", audio);
            }
            if (replyName != null && !replyName.isEmpty()) {
                obj.put("replyName", replyName);
                obj.put("replyText", replyText != null ? replyText : "");
                if (replyMsgId != null) obj.put("replyMsgId", replyMsgId);
            }
            socket.emit("message", obj);
        } catch (Exception e) {
            Log.e(TAG, "sendMessage error", e);
        }
    }

    public void changeName(String newName) {
        if (socket == null || !socket.connected()) return;
        socket.emit("change-name", newName);
    }

    public void changeAvatar(String base64Image) {
        if (socket == null || !socket.connected()) return;
        socket.emit("change-avatar", base64Image);
    }

    public void createTopic(String name) {
        if (socket == null || !socket.connected()) return;
        socket.emit("create-topic", name);
    }

    public void topicLock() {
        if (socket != null && socket.connected()) socket.emit("topic-lock");
    }

    public void topicUnlock() {
        if (socket != null && socket.connected()) socket.emit("topic-unlock");
    }

    public void topicDelete() {
        if (socket != null && socket.connected()) socket.emit("topic-delete");
    }

    public void loadHistory(int beforeId) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("beforeId", beforeId);
            socket.emit("load-history", obj);
        } catch (Exception e) {
            Log.e(TAG, "loadHistory error", e);
        }
    }

    public void requestUserProfile(String uid) {
        if (socket == null || !socket.connected() || uid == null) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("uid", uid);
            socket.emit("user-profile", obj);
        } catch (Exception e) {
            Log.e(TAG, "requestUserProfile error", e);
        }
    }

    public void loadExploreFeed(int page, String sort, String query) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("page", page);
            obj.put("sort", sort != null ? sort : "hot");
            if (query != null && !query.trim().isEmpty()) {
                obj.put("q", query.trim());
            }
            socket.emit("explore-feed", obj);
        } catch (Exception e) {
            Log.e(TAG, "loadExploreFeed error", e);
        }
    }

    public void createExplorePost(String title, String body, List<String> tags, List<String> images, String video, String audio) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", title);
            obj.put("body", body != null ? body : "");
            if (tags != null && !tags.isEmpty()) {
                JSONArray tagArr = new JSONArray();
                for (String t : tags) tagArr.put(t);
                obj.put("tags", tagArr);
            }
            if (images != null && !images.isEmpty()) {
                JSONArray arr = new JSONArray();
                for (String img : images) arr.put(img);
                obj.put("images", arr);
            }
            if (video != null && !video.isEmpty()) {
                obj.put("video", video);
            }
            if (audio != null && !audio.isEmpty()) {
                obj.put("audio", audio);
            }
            socket.emit("explore-create", obj);
        } catch (Exception e) {
            Log.e(TAG, "createExplorePost error", e);
        }
    }

    public void getExplorePost(int id) {
        if (socket == null || !socket.connected()) return;
        socket.emit("explore-get", id);
    }

    public void voteExplorePost(int id, int vote) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("postId", id);
            obj.put("vote", vote);
            socket.emit("explore-vote", obj);
        } catch (Exception e) {
            Log.e(TAG, "voteExplorePost error", e);
        }
    }

    public void viewExplorePost(int id) {
        if (socket == null || !socket.connected()) return;
        socket.emit("explore-view", id);
    }

    public void shareExplorePost(int id) {
        if (socket == null || !socket.connected()) return;
        socket.emit("explore-share", id);
    }

    public void commentExplorePost(int postId, String body, Integer parentId, String replyName, String replyText) {
        if (socket == null || !socket.connected()) return;
        try {
            JSONObject obj = new JSONObject();
            obj.put("postId", postId);
            obj.put("body", body);
            if (parentId != null) obj.put("parentId", parentId);
            if (replyName != null && !replyName.isEmpty()) {
                obj.put("replyName", replyName);
                obj.put("replyText", replyText != null ? replyText : "");
            }
            socket.emit("explore-comment", obj);
        } catch (Exception e) {
            Log.e(TAG, "commentExplorePost error", e);
        }
    }

    public void loadExploreComments(int postId) {
        if (socket == null || !socket.connected()) return;
        socket.emit("explore-comments", postId);
    }

    public void deleteExplorePost(int id) {
        if (socket == null || !socket.connected()) return;
        socket.emit("explore-delete", id);
    }

    private void notifyConnectionError(String error) {
        mainHandler.post(() -> {
            for (ConnectionListener l : connectionListeners) l.onConnectionError(error);
        });
    }
}
