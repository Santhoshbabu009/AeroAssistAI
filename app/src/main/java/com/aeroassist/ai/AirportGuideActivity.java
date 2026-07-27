package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class AirportGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_airport_guide);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        CardView security = findViewById(R.id.guideSecurity);
        CardView customs = findViewById(R.id.guideCustoms);
        CardView visa = findViewById(R.id.guideVisa);

        security.setOnClickListener(v -> startActivity(new Intent(this, SecurityGuideActivity.class)));
        customs.setOnClickListener(v -> startActivity(new Intent(this, CustomsGuideActivity.class)));
        visa.setOnClickListener(v -> startActivity(new Intent(this, VisaInfoActivity.class)));
    }
}
