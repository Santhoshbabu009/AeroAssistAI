package com.aeroassist.ai;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChatDao {

    @Insert
    void insert(ChatMessage chat);

    @Query("SELECT * FROM ChatMessage WHERE userEmail = :email AND userType = :type")
    List<ChatMessage> getAllChats(String email, String type);

    @Query("SELECT * FROM ChatMessage WHERE sessionId = :sessionId")
    List<ChatMessage> getChatsBySession(long sessionId);

    @Query("SELECT DISTINCT sessionId FROM ChatMessage WHERE userEmail = :email AND userType = :type ORDER BY sessionId DESC")
    List<Long> getAllSessions(String email, String type);

    @Query("SELECT sessionId FROM ChatMessage WHERE userEmail = :email AND userType = :type ORDER BY sessionId DESC LIMIT 1")
    Long getLastSessionId(String email, String type);
}