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

        // Parse flight number
        String flightNumber = "";
        try {
            if (flightJson != null) {
                org.json.JSONObject flightObj = new org.json.JSONObject(flightJson);
                flightNumber = flightObj.optString("flight_number", "");
            }
        } catch (Exception e) { e.printStackTrace(); }

        fetchAndRenderSeats(flightNumber, date != null ? date : "", seatGrid, selectedSeatText);

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
    private void fetchAndRenderSeats(String flightNumber, String flightDate, GridLayout seatGrid, TextView selectedSeatText) {
        String url = Constants.FLIGHT_SEATS_ENDPOINT + "?flight_number=" + flightNumber + "&date=" + flightDate;
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull java.io.IOException e) {
                runOnUiThread(() -> renderSeatGrid(new java.util.HashSet<>(), seatGrid, selectedSeatText));
            }

            @Override
            public void onResponse(@androidx.annotation.NonNull okhttp3.Call call, @androidx.annotation.NonNull okhttp3.Response response) throws java.io.IOException {
                java.util.Set<String> occupied = new java.util.HashSet<>();
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    org.json.JSONObject json = new org.json.JSONObject(body);
                    if (json.optString("status").equals("success")) {
                        org.json.JSONArray arr = json.optJSONArray("occupied_seats");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                occupied.add(arr.getString(i));
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
                runOnUiThread(() -> renderSeatGrid(occupied, seatGrid, selectedSeatText));
            }
        });
    }

    private void renderSeatGrid(java.util.Set<String> occupiedSeats, GridLayout seatGrid, TextView selectedSeatText) {
        seatGrid.removeAllViews();
        String[] rows = {"1", "2", "3", "4", "5", "6"};
        String[] cols = {"A", "B", "C", "D"};

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                Button seatBtn = new Button(this);
                String seatId = rows[i] + cols[j];
                seatBtn.setText(seatId);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 110;
                params.height = 110;
                params.setMargins(6, 6, 6, 6);
                seatBtn.setLayoutParams(params);

                if (occupiedSeats.contains(seatId)) {
                    seatBtn.setBackgroundColor(Color.parseColor("#334155"));
                    seatBtn.setTextColor(Color.parseColor("#64748B"));
                    seatBtn.setEnabled(false);
                } else {
                    seatBtn.setBackgroundColor(Color.parseColor("#1E293B"));
                    seatBtn.setTextColor(Color.WHITE);
                    seatBtn.setOnClickListener(v -> {
                        for (int k = 0; k < seatGrid.getChildCount(); k++) {
                            Button b = (Button) seatGrid.getChildAt(k);
                            if (b.isEnabled()) {
                                b.setBackgroundColor(Color.parseColor("#1E293B"));
                                b.setTextColor(Color.WHITE);
                            }
                        }
                        seatBtn.setBackgroundColor(Color.parseColor("#00E5FF"));
                        seatBtn.setTextColor(Color.BLACK);
                        selectedSeat = seatId;
                        selectedSeatText.setText("Selected Seat: " + selectedSeat);
                    });
                }
                seatGrid.addView(seatBtn);
            }
        }
    }
}
