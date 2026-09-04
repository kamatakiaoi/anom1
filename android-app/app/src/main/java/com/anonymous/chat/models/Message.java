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

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    public String getUid() { return uid != null ? uid : ""; }
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
                parseAndAddImages(result, img);
            }
        }
        if (result.isEmpty() && image != null && !image.trim().isEmpty()) {
            parseAndAddImages(result, image);
        }
        return result;
    }

    private static void parseAndAddImages(List<String> result, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String trimmed = raw.trim();
        // If it starts with [ and ends with ] or contains commas
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                JSONArray arr = new JSONArray(trimmed);
                for (int i = 0; i < arr.length(); i++) {
                    String item = cleanImgToken(arr.getString(i));
                    if (!item.isEmpty() && !result.contains(item)) result.add(item);
                }
                return;
            } catch (Exception ignored) {}
        }
        // Fallback: strip outer brackets if any and split by comma
        if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split(",");
        for (String p : parts) {
            String clean = cleanImgToken(p);
            if (!clean.isEmpty() && !result.contains(clean)) {
                result.add(clean);
            }
        }
    }

    private static String cleanImgToken(String token) {
        if (token == null) return "";
        return token.trim().replaceAll("^[\"'\\[\\]]+|[\"'\\[\\],]+$", "").trim();
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
