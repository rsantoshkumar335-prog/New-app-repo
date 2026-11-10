package com.santoshdev.notnote;

public class Note {
    private int id;
    private String title;
    private String content;
    private long timestamp;
    private boolean isPrivate;
    private String pageColor;
    private boolean isPinned;
    private String category;

    public Note(int id, String title, String content, long timestamp, boolean isPrivate, String pageColor) {
        this(id, title, content, timestamp, isPrivate, pageColor, false, "General");
    }

    public Note(int id, String title, String content, long timestamp, boolean isPrivate, String pageColor, boolean isPinned, String category) {
        this.id = id;
        this.title = title != null ? title : "";
        this.content = content != null ? content : "";
        this.timestamp = timestamp;
        this.isPrivate = isPrivate;
        this.pageColor = (pageColor != null && !pageColor.isEmpty()) ? pageColor : "#FFFFFF";
        this.isPinned = isPinned;
        this.category = (category != null && !category.trim().isEmpty()) ? category : "General";
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public boolean isPrivate() { return isPrivate; }
    public String getPageColor() { return pageColor; }
    public boolean isPinned() { return isPinned; }
    public String getCategory() { return category != null ? category : "General"; }

    public void setTitle(String title) { this.title = title; }
    public void setContent(String content) { this.content = content; }
    public void setPageColor(String pageColor) { this.pageColor = pageColor; }
    public void setPinned(boolean pinned) { this.isPinned = pinned; }
    public void setCategory(String category) { this.category = category; }
}
