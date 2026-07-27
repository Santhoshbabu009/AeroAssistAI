package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LeaderboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_tips); // Reusing list layout

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        View titleView = findViewById(android.R.id.content).findViewWithTag("title");
        if (titleView instanceof TextView) {
            ((TextView) titleView).setText("Travel Leaderboard");
        }

        RecyclerView rv = findViewById(R.id.rvTips);
        rv.setLayoutManager(new LinearLayoutManager(this));

        SharedPreferences session = getSharedPreferences("UserData", MODE_PRIVATE);
        String safeEmail = getIntent().getStringExtra("email");
        if (safeEmail == null || safeEmail.trim().isEmpty()) {
            safeEmail = "default";
        }
        String currentUserName = session.getString("name_" + safeEmail, "Traveler");

        SharedPreferences globalPrefs = getSharedPreferences("GlobalStats", MODE_PRIVATE);
        
        List<Player> list = new ArrayList<>();
        boolean currentUserAdded = false;

        // Fetch all actual quiz scores from SharedPreferences
        Map<String, ?> allScores = globalPrefs.getAll();
        for (Map.Entry<String, ?> entry : allScores.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("score_")) {
                String playerEmail = key.substring(6); // Extract email local part
                int score = 0;
                if (entry.getValue() instanceof Integer) {
                    score = (Integer) entry.getValue();
                } else if (entry.getValue() instanceof String) {
                    try {
                        score = Integer.parseInt((String) entry.getValue());
                    } catch (NumberFormatException e) {
                        score = 0;
                    }
                }
                
                String playerName = session.getString("name_" + playerEmail, "");
                if (playerName.isEmpty()) {
                    if (playerEmail.contains("@")) {
                        playerName = playerEmail.split("@")[0];
                        if (playerName.length() > 0) {
                            playerName = playerName.substring(0, 1).toUpperCase() + playerName.substring(1);
                        }
                    } else {
                        playerName = "Traveler " + playerEmail;
                    }
                }

                if (playerEmail.equalsIgnoreCase(safeEmail)) {
                    playerName = currentUserName + " (You)";
                    currentUserAdded = true;
                }

                list.add(new Player(playerName, score, ""));
            }
        }

        // Always ensure the active player is listed on the leaderboard
        if (!currentUserAdded) {
            int score = globalPrefs.getInt("score_" + safeEmail, 0);
            list.add(new Player(currentUserName + " (You)", score, ""));
        }

        // Sort player list in descending order of actual scores
        Collections.sort(list, (p1, p2) -> Integer.compare(p2.scoreValue, p1.scoreValue));

        // Dynamically assign standard medals and rank placements
        for (int i = 0; i < list.size(); i++) {
            Player p = list.get(i);
            if (i == 0) {
                p.rank = "🥇";
            } else if (i == 1) {
                p.rank = "🥈";
            } else if (i == 2) {
                p.rank = "🥉";
            } else {
                p.rank = "#" + (i + 1);
            }
        }

        rv.setAdapter(new PlayerAdapter(list));
    }

    private static class Player {
        String name, scoreText, rank;
        int scoreValue;
        Player(String name, int scoreValue, String rank) {
            this.name = name;
            this.scoreValue = scoreValue;
            this.scoreText = scoreValue + " pts";
            this.rank = rank;
        }
    }

    private class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.ViewHolder> {
        private final List<Player> items;
        PlayerAdapter(List<Player> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Player item = items.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.scoreText);
            holder.icon.setText(item.rank);
            holder.rating.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, icon, rating;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                desc = v.findViewById(R.id.itemCategory);
                icon = v.findViewById(R.id.itemIcon);
                rating = v.findViewById(R.id.itemRating);
            }
        }
    }
}
