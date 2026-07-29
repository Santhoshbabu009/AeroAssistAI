package com.aeroassist.ai;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SeatPassengerActivity extends BaseActivity {

    private String flightJson;
    private String date;
    private String selectedSeat = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seat_passenger);

        flightJson = getIntent().getStringExtra("FLIGHT_JSON");
        date = getIntent().getStringExtra("DATE");

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        Spinner genderSpinner = findViewById(R.id.passengerGenderSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Male", "Female", "Other"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        genderSpinner.setAdapter(adapter);

        GridLayout seatGrid = findViewById(R.id.seatGrid);
        TextView selectedSeatText = findViewById(R.id.selectedSeatText);

        // Generate dummy seats (3x4 grid)
        String[] rows = {"1", "2", "3", "4"};
        String[] cols = {"A", "B", "C"};

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                Button seatBtn = new Button(this);
                String seatId = rows[i] + cols[j];
                seatBtn.setText(seatId);
                seatBtn.setBackgroundColor(Color.parseColor("#1E293B"));
                seatBtn.setTextColor(Color.WHITE);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 120;
                params.height = 120;
                params.setMargins(8, 8, 8, 8);
                seatBtn.setLayoutParams(params);

                // Dummy occupied logic
                if (Math.random() > 0.7) {
                    seatBtn.setBackgroundColor(Color.parseColor("#334155"));
                    seatBtn.setTextColor(Color.GRAY);
                    seatBtn.setEnabled(false);
                } else {
                    seatBtn.setOnClickListener(v -> {
                        // Reset all to default color (unless disabled)
                        for (int k = 0; k < seatGrid.getChildCount(); k++) {
                            Button b = (Button) seatGrid.getChildAt(k);
                            if (b.isEnabled()) {
                                b.setBackgroundColor(Color.parseColor("#1E293B"));
                                b.setTextColor(Color.WHITE);
                            }
                        }
                        // Highlight selected
                        seatBtn.setBackgroundColor(Color.parseColor("#00E5FF"));
                        seatBtn.setTextColor(Color.BLACK);
                        selectedSeat = seatId;
                        selectedSeatText.setText("Selected Seat: " + selectedSeat);
                    });
                }
                seatGrid.addView(seatBtn);
            }
        }

        Button reviewBtn = findViewById(R.id.reviewBookingBtn);
        reviewBtn.setOnClickListener(v -> {
            EditText nameInput = findViewById(R.id.passengerNameInput);
            EditText ageInput = findViewById(R.id.passengerAgeInput);
            EditText mobileInput = findViewById(R.id.contactMobileInput);

            String name = nameInput.getText().toString().trim();
            String age = ageInput.getText().toString().trim();
            String mobile = mobileInput.getText().toString().trim();
            String gender = genderSpinner.getSelectedItem().toString();

            if (name.isEmpty() || age.isEmpty() || mobile.isEmpty() || selectedSeat.isEmpty()) {
                Toast.makeText(this, "Please fill all details and select a seat", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(SeatPassengerActivity.this, BookingReviewActivity.class);
            intent.putExtra("FLIGHT_JSON", flightJson);
            intent.putExtra("DATE", date);
            intent.putExtra("PAX_NAME", name);
            intent.putExtra("PAX_AGE", age);
            intent.putExtra("PAX_GENDER", gender);
            intent.putExtra("PAX_MOBILE", mobile);
            intent.putExtra("SEAT", selectedSeat);
            startActivity(intent);
        });
    }
}
