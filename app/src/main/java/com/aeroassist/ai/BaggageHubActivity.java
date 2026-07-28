package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class BaggageHubActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_baggage_hub);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        CardView lostFound = findViewById(R.id.cardLostFound);

        lostFound.setOnClickListener(v -> startActivity(new Intent(this, LostFoundListActivity.class)));
    }
}
