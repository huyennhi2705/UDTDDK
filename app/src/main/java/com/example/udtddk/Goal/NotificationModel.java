package com.example.udtddk.Goal;

public class NotificationModel {
    public String title;
    public String content;
    public String type;
    public long timestamp;

    public NotificationModel() {}

    public NotificationModel(String title, String content, String type, long timestamp) {
        this.title = title;
        this.content = content;
        this.type = type;
        this.timestamp = timestamp;
    }
}