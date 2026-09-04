package com.anonymous.chat.models;

import org.json.JSONArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Post implements Serializable {
    private int id;
    private String userId;
    private String uid;
    private String name;
    private String avatar;
    private String color;
    private String title;
    private String body;
    private List<String> tags = new ArrayList<>();
    private List<String> images = new ArrayList<>();
    private String video;
    private String audio;
    private int views;
    private int shares;
    private int upvotes;
    private int downvotes;
    private int score;
    private int comments;
    private int myVote; // -1, 0, 1
    private boolean isOwner;
    private String time;

    public Post() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUserId() { return userId != null ? userId : (uid != null ? uid : ""); }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUid() { return uid != null ? uid : (userId != null ? userId : ""); }
    public String getAuthorUid() { return getUid(); }
    public void setUid(String uid) { this.uid = uid; }

    public String getAuthorId() { return getUserId(); }
    public String getName() { return name != null ? name : "Anonymous"; }
    public String getAuthorName() { return getName(); }
    public void setName(String name) { this.name = name; }
    public String getCreatedAt() { return time != null ? time : ""; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getColor() { return color != null ? color : "#888888"; }
    public void setColor(String color) { this.color = color; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body != null ? body : ""; }
    public void setBody(String body) { this.body = body; }

    public List<String> getTags() {
        if (tags == null) tags = new ArrayList<>();
        return tags;
    }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<String> getImages() {
        List<String> result = new ArrayList<>();
        if (images != null && !images.isEmpty()) {
            for (String img : images) {
                if (img != null && !img.trim().isEmpty()) {
                    String trimmed = img.trim();
                    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                        try {
                            JSONArray arr = new JSONArray(trimmed);
                            for (int i = 0; i < arr.length(); i++) {
                                result.add(arr.getString(i));
                            }
                        } catch (Exception e) {
                            result.add(trimmed);
                        }
                    } else {
                        result.add(trimmed);
                    }
                }
            }
        }
        return result;
    }
    public void setImages(List<String> images) { this.images = images; }

    public String getVideo() { return video; }
    public void setVideo(String video) { this.video = video; }

    public String getAudio() { return audio; }
    public void setAudio(String audio) { this.audio = audio; }

    public int getViews() { return views; }
    public void setViews(int views) { this.views = views; }

    public int getShares() { return shares; }
    public void setShares(int shares) { this.shares = shares; }

    public int getUpvotes() { return upvotes; }
    public void setUpvotes(int upvotes) { this.upvotes = upvotes; }

    public int getDownvotes() { return downvotes; }
    public void setDownvotes(int downvotes) { this.downvotes = downvotes; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getComments() { return comments; }
    public void setComments(int comments) { this.comments = comments; }

    public int getMyVote() { return myVote; }
    public void setMyVote(int myVote) { this.myVote = myVote; }

    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }

    public String getTime() { return time != null ? time : ""; }
    public void setTime(String time) { this.time = time; }
}
