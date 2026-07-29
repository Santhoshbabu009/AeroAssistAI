package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class BookingReviewActivity extends BaseActivity {

    private String flightJson, date, paxName, paxAge, paxGender, paxMobile, seat;
    private int totalFare;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_review);

        flightJson = getIntent().getStringExtra("FLIGHT_JSON");
        date = getIntent().getStringExtra("DATE");
        paxName = getIntent().getStringExtra("PAX_NAME");
        paxAge = getIntent().getStringExtra("PAX_AGE");
        paxGender = getIntent().getStringExtra("PAX_GENDER");
        paxMobile = getIntent().getStringExtra("PAX_MOBILE");
        seat = getIntent().getStringExtra("SEAT");

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        try {
            JSONObject flight = new JSONObject(flightJson);
            
            TextView route = findViewById(R.id.reviewRoute);
            route.setText(flight.getString("origin") + " ➔ " + flight.getString("destination"));

            TextView meta = findViewById(R.id.reviewFlightMeta);
            meta.setText(date + " | " + flight.getString("airline") + " (" + flight.getString("flight_number") + ")");

            TextView time = findViewById(R.id.reviewFlightTime);
            time.setText(flight.getString("departure_time") + " - " + flight.getString("arrival_time") + " (" + flight.getString("duration") + ")");

            TextView paxInfo = findViewById(R.id.reviewPassengerInfo);
            paxInfo.setText("• " + paxName + " (" + paxGender + ", " + paxAge + ") - Seat: " + seat);

            TextView contactInfo = findViewById(R.id.reviewContactInfo);
            contactInfo.setText("Contact: " + paxMobile);

            int baseFare = flight.getInt("base_fare");
            int taxes = 850;
            totalFare = baseFare + taxes;

            TextView reviewBaseFare = findViewById(R.id.reviewBaseFare);
            reviewBaseFare.setText("₹" + baseFare);

            TextView reviewTaxes = findViewById(R.id.reviewTaxes);
            reviewTaxes.setText("₹" + taxes);

            TextView reviewTotalFare = findViewById(R.id.reviewTotalFare);
            reviewTotalFare.setText("₹" + totalFare);

        } catch (Exception e) {
            e.printStackTrace();
        }

        Button proceedPaymentBtn = findViewById(R.id.proceedPaymentBtn);
        proceedPaymentBtn.setOnClickListener(v -> {
            Intent intent = new Intent(BookingReviewActivity.this, DummyPaymentActivity.class);
            intent.putExtra("FLIGHT_JSON", flightJson);
            intent.putExtra("DATE", date);
            intent.putExtra("PAX_NAME", paxName);
            intent.putExtra("PAX_AGE", paxAge);
            intent.putExtra("PAX_GENDER", paxGender);
            intent.putExtra("PAX_MOBILE", paxMobile);
            intent.putExtra("SEAT", seat);
            intent.putExtra("TOTAL_FARE", totalFare);
            startActivity(intent);
        });
    }
}
