package com.aeroassist.ai;

import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ActiveSessionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_active_sessions);

        // 1. Hook back button
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // 2. Dynamically determine current device model and info for premium presentation
        TextView tvCurrentDeviceName = findViewById(R.id.tvCurrentDeviceName);
        TextView tvCurrentDeviceDetails = findViewById(R.id.tvCurrentDeviceDetails);

        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;

        if (manufacturer != null && manufacturer.length() > 0) {
            manufacturer = manufacturer.substring(0, 1).toUpperCase() + manufacturer.substring(1);
        }
        
        String deviceDisplayName = manufacturer + " " + model;
        tvCurrentDeviceName.setText(deviceDisplayName + " (Current)");
        tvCurrentDeviceDetails.setText("AeroAssist App • Android " + androidVersion + " • Active Now");
    }
}
