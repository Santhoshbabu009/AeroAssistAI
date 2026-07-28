package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class FlightSearchHistoryActivity extends BaseActivity {

    LinearLayout historyList;
    Button clearHistoryBtn;
    ImageButton btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_search_history);

        historyList = findViewById(R.id.historyList);
        clearHistoryBtn = findViewById(R.id.clearHistoryBtn);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        clearHistoryBtn.setOnClickListener(v -> clearHistory());

        loadHistory();
    }

    private void loadHistory() {
        SharedPreferences prefs = getSharedPreferences("FlightHistory", MODE_PRIVATE);
        String history = prefs.getString("search_history", "");

        if (history.isEmpty()) {
            TextView emptyText = new TextView(this);
            emptyText.setText("No search history found.");
            emptyText.setTextSize(16);
            emptyText.setTextColor(0xFF666666);
            emptyText.setPadding(20, 40, 20, 40);
            emptyText.setGravity(android.view.Gravity.CENTER);
            historyList.addView(emptyText);
            clearHistoryBtn.setVisibility(View.GONE);
            return;
        }

        String[] entries = history.split(",");
        for (String entry : entries) {
            addHistoryItem(entry);
        }
    }

    private void addHistoryItem(String flightCode) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(12);
        card.setCardElevation(4);
        card.setCardBackgroundColor(0xFFF8F9FA);

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(32, 24, 32, 24);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(flightCode);
        tv.setTextSize(18);
        tv.setTextColor(0xFF333333);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView reSearch = new TextView(this);
        reSearch.setText("Search ðŸ”");
        reSearch.setTextColor(0xFF6C4AB6);
        reSearch.setTextSize(14);
        reSearch.setPadding(16, 8, 16, 8);

        layout.addView(tv);
        layout.addView(reSearch);
        card.addView(layout);

        card.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, FlightStatusActivity.class);
            // We can't easily auto-trigger search without passing data back or using more complex logic
            // But let's at least finish and the user can see it or we can restart
            // For now, let's just toast
            Toast.makeText(this, "Searching for " + flightCode, Toast.LENGTH_SHORT).show();
            // Re-open FlightStatusActivity with the code
            intent.putExtra("flight_code", flightCode);
            startActivity(intent);
            finish();
        });

        historyList.addView(card);
    }

    private void clearHistory() {
        SharedPreferences prefs = getSharedPreferences("FlightHistory", MODE_PRIVATE);
        prefs.edit().remove("search_history").apply();
        historyList.removeAllViews();
        loadHistory();
        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
    }
}
