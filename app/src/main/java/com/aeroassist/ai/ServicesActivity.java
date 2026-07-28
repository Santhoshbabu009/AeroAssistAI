package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class ServicesActivity extends BaseActivity {

    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services);

        email = getIntent().getStringExtra("email");

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        CardView cardDining = findViewById(R.id.cardDining);
        CardView cardShopping = findViewById(R.id.cardShopping);
        CardView cardLounges = findViewById(R.id.cardLounges);
        CardView cardParking = findViewById(R.id.cardParking);
        CardView cardBaggage = findViewById(R.id.cardBaggage);
        CardView cardCurrency = findViewById(R.id.cardCurrency);
        CardView cardGuides = findViewById(R.id.cardGuides);
        CardView cardTerminalInfo = findViewById(R.id.cardTerminalInfo);

        cardBaggage.setOnClickListener(v -> {
            startActivity(new Intent(this, BaggageHubActivity.class));
        });

        cardDining.setOnClickListener(v -> {
            Intent intent = new Intent(this, RestaurantListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        cardShopping.setOnClickListener(v -> {
            Intent intent = new Intent(this, StoreListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        cardLounges.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoungeListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        cardParking.setOnClickListener(v -> {
            Intent intent = new Intent(this, ParkingActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        cardCurrency.setOnClickListener(v -> {
            startActivity(new Intent(this, CurrencyConverterActivity.class));
        });

        cardGuides.setOnClickListener(v -> {
            startActivity(new Intent(this, AirportGuideActivity.class));
        });

        cardTerminalInfo.setOnClickListener(v -> {
            startActivity(new Intent(this, TerminalHubActivity.class));
        });
        
        // Add more listeners as screens are built
    }
}
