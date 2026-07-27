package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class BookingHistoryActivity extends AppCompatActivity {

    LinearLayout historyList;
    TextView emptyText;
    Button clearBtn;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_history);

        historyList = findViewById(R.id.historyList);
        emptyText = findViewById(R.id.emptyText);
        clearBtn = findViewById(R.id.clearBtn);

        prefs = getSharedPreferences("BookingHistory", MODE_PRIVATE);

        loadHistory();

        clearBtn.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            historyList.removeAllViews();
            emptyText.setVisibility(View.VISIBLE);
            historyList.setVisibility(View.GONE);
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadHistory() {
        Set<String> entries = prefs.getStringSet("searches", new LinkedHashSet<>());

        if (entries == null || entries.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            historyList.setVisibility(View.GONE);
            return;
        }

        emptyText.setVisibility(View.GONE);
        historyList.setVisibility(View.VISIBLE);
        historyList.removeAllViews();

        // Display newest first
        String[] arr = entries.toArray(new String[0]);
        for (int i = arr.length - 1; i >= 0; i--) {
            String entry = arr[i];
            // Format: "DEL → BOM | 2025-06-15"
            CardView card = new CardView(this);
            CardView.LayoutParams params = new CardView.LayoutParams(
                    CardView.LayoutParams.MATCH_PARENT,
                    CardView.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 0, 16);
            card.setLayoutParams(params);
            card.setRadius(20f);
            card.setCardElevation(6f);
            card.setCardBackgroundColor(Color.parseColor("#CCFFFFFF"));

            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(40, 30, 40, 30);

            String[] parts = entry.split("\\|");
            String route = parts.length > 0 ? parts[0].trim() : entry;
            String date = parts.length > 1 ? parts[1].trim() : "";

            TextView routeView = new TextView(this);
            routeView.setText("✈  " + route);
            routeView.setTextSize(18);
            routeView.setTextColor(Color.parseColor("#0b2447"));
            routeView.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView dateView = new TextView(this);
            dateView.setText("\uD83D\uDCC5  " + date);
            dateView.setTextSize(13);
            dateView.setTextColor(Color.parseColor("#5a7184"));

            inner.addView(routeView);
            inner.addView(dateView);
            card.addView(inner);
            historyList.addView(card);
        }
    }
}
