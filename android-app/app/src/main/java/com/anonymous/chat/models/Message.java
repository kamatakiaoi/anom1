package com.anonymous.chat.models;

import org.json.JSONArray;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
    private int msgId;
    private String id;
    private String uid;
    private String name;
    private String avatar;
    private String color;
    private String text;
    private String time;
    private String image;
    private List<String> images = new ArrayList<>();
    private String video;
    private String audio;
    private String replyName;
    private String replyText;
    private Integer replyMsgId;

    // Transient streak grouping fields
    private transient boolean isGrouped = false;
    private transient boolean showTime = true;
    private transient String groupPosition = "g-only";

    public Message() {}

    public int getMsgId() { return msgId; }
    public void setMsgId(int msgId) { this.msgId = msgId; }

    public String getId() { return id != null ? id : (uid != null ? uid : ""); }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid != null ? uid : (id != null ? id : ""); }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name != null ? name : "Anonymous"; }
    public void setName(String name) { this.name = name; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getColor() { return color != null ? color : "#888888"; }
    public void setColor(String color) { this.color = color; }

    public String getText() { return text != null ? text : ""; }
    public void setText(String text) { this.text = text; }

    public String getTime() { return time != null ? time : ""; }
    public void setTime(String time) { this.time = time; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

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
        if (result.isEmpty() && image != null && !image.trim().isEmpty()) {
            String trimmed = image.trim();
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
        return result;
    }
    public void setImages(List<String> images) { this.images = images; }

    public String getVideo() { return video; }
    public void setVideo(String video) { this.video = video; }

    public String getAudio() { return audio; }
    public void setAudio(String audio) { this.audio = audio; }

    public String getReplyName() { return replyName; }
    public void setReplyName(String replyName) { this.replyName = replyName; }

    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }

    public Integer getReplyMsgId() { return replyMsgId; }
    public void setReplyMsgId(Integer replyMsgId) { this.replyMsgId = replyMsgId; }

    public boolean isGrouped() { return isGrouped; }
    public void setGrouped(boolean grouped) { isGrouped = grouped; }

    public boolean isShowTime() { return showTime; }
    public void setShowTime(boolean showTime) { this.showTime = showTime; }

    public String getGroupPosition() { return groupPosition != null ? groupPosition : "g-only"; }
    public void setGroupPosition(String groupPosition) { this.groupPosition = groupPosition; }
}
