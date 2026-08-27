package com.anonymous.chat.models;

import java.io.Serializable;

public class Topic implements Serializable {
    private int id;
    private String name;
    private int online;
    private int msgCount;
    private boolean isSystem;
    private boolean isGeneral;
    private boolean isOwner;
    private boolean isLocked;
    private boolean locked;
    private String lockedBy;
    private boolean recommended;
    private int lastMsgId;
    private LastMessage lastMsg;

    public static class LastMessage implements Serializable {
        private int id;
        private String name;
        private String text;
        private String time;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name != null ? name : ""; }
        public void setName(String name) { this.name = name; }
        public String getText() { return text != null ? text : ""; }
        public void setText(String text) { this.text = text; }
        public String getTime() { return time != null ? time : ""; }
        public void setTime(String time) { this.time = time; }
    }

    public Topic() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public int getOnline() { return online; }
    public void setOnline(int online) { this.online = online; }

    public int getMsgCount() { return msgCount; }
    public void setMsgCount(int msgCount) { this.msgCount = msgCount; }

    public boolean isSystem() {
        return isSystem || isGeneral || "General".equalsIgnoreCase(name) || "Archive".equalsIgnoreCase(name) || "Patch notes".equalsIgnoreCase(name);
    }
    public void setSystem(boolean system) { isSystem = system; }

    public boolean isGeneral() {
        return isGeneral || "General".equalsIgnoreCase(name);
    }
    public void setGeneral(boolean general) { isGeneral = general; }

    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }

    public boolean isLocked() { return isLocked || locked; }
    public void setLocked(boolean locked) { this.isLocked = locked; this.locked = locked; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public boolean isRecommended() { return recommended; }
    public void setRecommended(boolean recommended) { this.recommended = recommended; }

    public int getLastMsgId() { return lastMsgId; }
    public void setLastMsgId(int lastMsgId) { this.lastMsgId = lastMsgId; }

    public LastMessage getLastMsg() { return lastMsg; }
    public void setLastMsg(LastMessage lastMsg) { this.lastMsg = lastMsg; }
}
