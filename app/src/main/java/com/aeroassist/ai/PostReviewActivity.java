package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class PostReviewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lost_found_report); // Reusing form layout
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnSubmitReport).setOnClickListener(v -> {
            Toast.makeText(this, "Review Posted!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
