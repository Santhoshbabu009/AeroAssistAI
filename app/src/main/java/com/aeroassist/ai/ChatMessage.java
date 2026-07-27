package com.aeroassist.ai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class ChatMessage {

    @PrimaryKey(autoGenerate = true)
    public int id;

    private String message;
    private boolean isUser;
    private String userEmail;
    private String userType;
    private long sessionId;

    public ChatMessage(String message, boolean isUser, String userEmail, String userType, long sessionId) {
        this.message = message;
        this.isUser = isUser;
        this.userEmail = userEmail;
        this.userType = userType;
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isUser() {
        return isUser;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserType() {
        return userType;
    }

    public long getSessionId() {
        return sessionId;
    }
}