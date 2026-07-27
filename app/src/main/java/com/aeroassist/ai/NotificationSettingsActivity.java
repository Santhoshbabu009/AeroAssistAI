package com.aeroassist.ai;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class NotificationSettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
