package com.anonymous.chat.models;

import java.io.Serializable;

public class UserProfile implements Serializable {
    private String id;
    private String uid;
    private String name;
    private String avatar;
    private String color;
    private String ip;
    private int messages;
    private int media;
    private String disk;
    private String lastChangeName;

    public UserProfile() {}

    public UserProfile(String id, String name, String avatar, String color) {
        this.id = id;
        this.uid = id;
        this.name = name;
        this.avatar = avatar;
        this.color = color;
    }

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

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public int getMessages() { return messages; }
    public void setMessages(int messages) { this.messages = messages; }

    public int getMedia() { return media; }
    public void setMedia(int media) { this.media = media; }

    public String getDisk() { return disk != null ? disk : "0 B"; }
    public void setDisk(String disk) { this.disk = disk; }

    public String getLastChangeName() { return lastChangeName; }
    public void setLastChangeName(String lastChangeName) { this.lastChangeName = lastChangeName; }
}
