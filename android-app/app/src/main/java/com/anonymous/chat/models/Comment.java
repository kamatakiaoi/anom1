package com.anonymous.chat.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Comment implements Serializable {
    private int id;
    private int postId;
    private String userId;
    private String uid;
    private String name;
    private String avatar;
    private String color;
    private String body;
    private String image;
    private List<String> images = new ArrayList<>();
    private Integer parentId;
    private String replyName;
    private String replyText;
    private String time;

    public Comment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPostId() { return postId; }
    public void setPostId(int postId) { this.postId = postId; }

    public String getUserId() { return userId != null ? userId : (uid != null ? uid : ""); }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUid() { return uid != null ? uid : (userId != null ? userId : ""); }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name != null ? name : "Anonymous"; }
    public String getAuthorName() { return getName(); }
    public void setName(String name) { this.name = name; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getColor() { return color != null ? color : "#888888"; }
    public void setColor(String color) { this.color = color; }

    public String getBody() { return body != null ? body : ""; }
    public void setBody(String body) { this.body = body; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public List<String> getImages() {
        if (images == null) images = new ArrayList<>();
        if (images.isEmpty() && image != null && !image.isEmpty()) {
            images.add(image);
        }
        return images;
    }
    public void setImages(List<String> images) { this.images = images; }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public String getReplyName() { return replyName; }
    public void setReplyName(String replyName) { this.replyName = replyName; }

    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }

    public String getTime() { return time != null ? time : ""; }
    public void setTime(String time) { this.time = time; }
}
