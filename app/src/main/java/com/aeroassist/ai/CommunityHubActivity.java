package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class CommunityHubActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community_hub);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        CardView reviews = findViewById(R.id.cardReviews);
        CardView tips = findViewById(R.id.cardTips);
        CardView achievements = findViewById(R.id.cardAchievements);
        CardView chat = findViewById(R.id.cardChat);

        reviews.setOnClickListener(v -> startActivity(new Intent(this, ReviewsActivity.class)));
        tips.setOnClickListener(v -> startActivity(new Intent(this, TravelTipsActivity.class)));
        achievements.setOnClickListener(v -> startActivity(new Intent(this, AchievementsActivity.class)));
        chat.setOnClickListener(v -> startActivity(new Intent(this, ChatListActivity.class)));
    }
}
