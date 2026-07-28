package com.aeroassist.ai;

import android.content.Intent;
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
import java.util.List;

public class AchievementsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievements);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnLeaderboard).setOnClickListener(v -> startActivity(new Intent(this, LeaderboardActivity.class)));

        RecyclerView rv = findViewById(R.id.rvAchievements);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Achievement> list = new ArrayList<>();
        list.add(new Achievement("Frequent Flyer", "Completed 10 flights", "ðŸ†"));
        list.add(new Achievement("Airport Explorer", "Visited 5 different airports", "ðŸŒ"));
        list.add(new Achievement("Early Bird", "Checked in 24h before flight", "ðŸ¦"));

        rv.setAdapter(new AchievementAdapter(list));
    }

    private static class Achievement {
        String name, desc, icon;
        Achievement(String n, String d, String i) { name = n; desc = d; icon = i; }
    }

    private class AchievementAdapter extends RecyclerView.Adapter<AchievementAdapter.ViewHolder> {
        private final List<Achievement> items;
        AchievementAdapter(List<Achievement> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Achievement item = items.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.desc);
            holder.icon.setText(item.icon);
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
