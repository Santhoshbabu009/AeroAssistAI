package com.aeroassist.ai;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {ChatMessage.class}, version = 4)
public abstract class ChatDatabase extends RoomDatabase {

    private static volatile ChatDatabase INSTANCE;

    public abstract ChatDao chatDao();

    public static ChatDatabase getInstance(android.content.Context context) {
        if (INSTANCE == null) {
            synchronized (ChatDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = androidx.room.Room.databaseBuilder(
                            context.getApplicationContext(),
                            ChatDatabase.class,
                            "chat-database"
                    ).fallbackToDestructiveMigration()
                     .build();
                }
            }
        }
        return INSTANCE;
    }
}