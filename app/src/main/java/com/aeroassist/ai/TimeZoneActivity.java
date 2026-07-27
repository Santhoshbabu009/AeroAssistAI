package com.aeroassist.ai;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class TimeZoneActivity extends AppCompatActivity {

    private LinearLayout clockContainer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable runnable;

    private String[][] cities = {
        {"London", "GMT"},
        {"New York", "America/New_York"},
        {"Dubai", "Asia/Dubai"},
        {"Singapore", "Asia/Singapore"},
        {"Tokyo", "Asia/Tokyo"},
        {"Sydney", "Australia/Sydney"},
        {"New Delhi", "Asia/Kolkata"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_zone);

        clockContainer = findViewById(R.id.clockContainer);
        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        updateClocks();
    }

    private void updateClocks() {
        clockContainer.removeAllViews();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateSdf = new SimpleDateFormat("EEE, MMM dd", Locale.getDefault());

        for (String[] city : cities) {
            View card = getLayoutInflater().inflate(R.layout.item_service, null);
            // Reusing item_service layout but modifying it
            TextView name = card.findViewById(R.id.itemName);
            TextView time = card.findViewById(R.id.itemCategory);
            TextView rating = card.findViewById(R.id.itemRating);
            TextView icon = card.findViewById(R.id.itemIcon);

            name.setText(city[0]);
            icon.setText("🏙️");

            sdf.setTimeZone(TimeZone.getTimeZone(city[1]));
            dateSdf.setTimeZone(TimeZone.getTimeZone(city[1]));
            
            rating.setText(sdf.format(new Date()));
            rating.setTextSize(18);
            rating.setTextColor(Color.WHITE);
            time.setText(dateSdf.format(new Date()));

            clockContainer.addView(card);
        }

        runnable = () -> {
            updateClocks();
            handler.postDelayed(runnable, 1000);
        };
        handler.postDelayed(runnable, 1000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && runnable != null) handler.removeCallbacks(runnable);
    }
}
