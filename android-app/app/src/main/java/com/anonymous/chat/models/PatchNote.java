package com.anonymous.chat.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PatchNote implements Serializable {
    private String version;
    private String title;
    private String date;
    private List<String> items = new ArrayList<>();

    public PatchNote() {}

    public String getVersion() { return version != null ? version : ""; }
    public void setVersion(String version) { this.version = version; }

    public String getTitle() { return title != null ? title : ""; }
    public void setTitle(String title) { this.title = title; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public List<String> getItems() {
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<String> items) { this.items = items; }
}
