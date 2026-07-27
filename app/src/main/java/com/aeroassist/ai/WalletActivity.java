package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class WalletActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        CardView boardingPass = findViewById(R.id.cardBoardingPass);
        CardView passport = findViewById(R.id.cardPassport);
        CardView idCard = findViewById(R.id.cardID);
        Button addDoc = findViewById(R.id.btnAddDoc);

        String userType = getIntent().getStringExtra("user_type");
        if (userType == null) userType = "Visitor";

        boardingPass.setOnClickListener(v -> startActivity(new Intent(this, BoardingPassActivity.class)));
        passport.setOnClickListener(v -> startActivity(new Intent(this, PassportActivity.class)));
        idCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, IDCardActivity.class);
            intent.putExtra("type", getIntent().getStringExtra("user_type")); // Using the actual user_type as the title
            startActivity(intent);
        });
        addDoc.setOnClickListener(v -> startActivity(new Intent(this, AddDocumentActivity.class)));
    }
}
