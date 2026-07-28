package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class RewardsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rewards);

        String email = getIntent().getStringExtra("email");
        android.content.SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        String name = prefs.getString("name_" + email, "User");

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        findViewById(R.id.startQuizBtn).setOnClickListener(v -> {
            Intent intent = new Intent(RewardsActivity.this, QuizActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("name", name);
            startActivity(intent);
        });

        findViewById(R.id.viewLeaderboardBtn).setOnClickListener(v -> {
            Intent intent = new Intent(RewardsActivity.this, LeaderboardActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });
    }
}
