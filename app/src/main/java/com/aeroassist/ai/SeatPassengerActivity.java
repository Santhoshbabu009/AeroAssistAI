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
import android.view.View;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

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
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }

        Spinner genderSpinner = findViewById(R.id.passengerGenderSpinner);
        if (genderSpinner != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Male", "Female", "Other"});
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            genderSpinner.setAdapter(adapter);
        }

        GridLayout seatGrid = findViewById(R.id.seatGrid);
        TextView selectedSeatText = findViewById(R.id.selectedSeatText);

        String flightNumber = "";
        try {
            if (flightJson != null) {
                JSONObject flightObj = new JSONObject(flightJson);
                flightNumber = flightObj.optString("flight_number", "");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        fetchAndRenderSeats(flightNumber, date != null ? date : "", seatGrid, selectedSeatText);

        Button reviewBtn = findViewById(R.id.reviewBookingBtn);
        if (reviewBtn != null) {
            reviewBtn.setOnClickListener(v -> {
                EditText nameInput = findViewById(R.id.passengerNameInput);
                EditText ageInput = findViewById(R.id.passengerAgeInput);
                EditText mobileInput = findViewById(R.id.contactMobileInput);

                String name = nameInput != null ? nameInput.getText().toString().trim() : "";
                String age = ageInput != null ? ageInput.getText().toString().trim() : "";
                String mobile = mobileInput != null ? mobileInput.getText().toString().trim() : "";
                String gender = (genderSpinner != null && genderSpinner.getSelectedItem() != null) ? genderSpinner.getSelectedItem().toString() : "Male";

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

    private void fetchAndRenderSeats(String flightNumber, String flightDate, GridLayout seatGrid, TextView selectedSeatText) {
        if (seatGrid == null) return;

        String url = Constants.FLIGHT_SEATS_ENDPOINT + "?flight_number=" + flightNumber + "&date=" + flightDate;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> renderSeatGrid(new HashSet<>(), seatGrid, selectedSeatText));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                Set<String> occupied = new HashSet<>();
                try {
                    String body = response.body() != null ? response.body().string() : "{}";
                    JSONObject json = new JSONObject(body);
                    if ("success".equals(json.optString("status"))) {
                        // backend returns "booked_seats" key
                        JSONArray arr = json.optJSONArray("booked_seats");
                        if (arr == null) arr = json.optJSONArray("occupied_seats"); // legacy fallback
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                occupied.add(arr.getString(i));
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                runOnUiThread(() -> renderSeatGrid(occupied, seatGrid, selectedSeatText));
            }
        });
    }

    private void renderSeatGrid(Set<String> occupiedSeats, GridLayout seatGrid, TextView selectedSeatText) {
        if (seatGrid == null) return;
        seatGrid.removeAllViews();

        String[] rows = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "10"};
        String[] cols = {"A", "B", "C", "AISLE", "D", "E", "F"};

        float density = getResources().getDisplayMetrics().density;
        int seatSizePx = (int) (40 * density);
        int aisleWidthPx = (int) (20 * density);
        int marginPx = (int) (3 * density);

        for (int i = 0; i < rows.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                String colName = cols[j];

                if ("AISLE".equals(colName)) {
                    View aisleView = new View(this);
                    GridLayout.LayoutParams aisleParams = new GridLayout.LayoutParams();
                    aisleParams.width = aisleWidthPx;
                    aisleParams.height = seatSizePx;
                    aisleParams.setMargins(marginPx, marginPx, marginPx, marginPx);
                    aisleView.setLayoutParams(aisleParams);
                    seatGrid.addView(aisleView);
                    continue;
                }

                Button seatBtn = new Button(this);
                String seatId = rows[i] + colName;
                seatBtn.setText(seatId);
                seatBtn.setTextSize(11);
                seatBtn.setPadding(0, 0, 0, 0);

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = seatSizePx;
                params.height = seatSizePx;
                params.setMargins(marginPx, marginPx, marginPx, marginPx);
                seatBtn.setLayoutParams(params);

                if (occupiedSeats != null && occupiedSeats.contains(seatId)) {
                    seatBtn.setBackgroundColor(Color.parseColor("#334155"));
                    seatBtn.setTextColor(Color.parseColor("#64748B"));
                    seatBtn.setEnabled(false);
                } else {
                    seatBtn.setBackgroundColor(Color.parseColor("#1E293B"));
                    seatBtn.setTextColor(Color.WHITE);
                    seatBtn.setOnClickListener(v -> {
                        for (int k = 0; k < seatGrid.getChildCount(); k++) {
                            View child = seatGrid.getChildAt(k);
                            if (child instanceof Button && child.isEnabled()) {
                                child.setBackgroundColor(Color.parseColor("#1E293B"));
                                ((Button) child).setTextColor(Color.WHITE);
                            }
                        }
                        seatBtn.setBackgroundColor(Color.parseColor("#00E5FF"));
                        seatBtn.setTextColor(Color.BLACK);
                        selectedSeat = seatId;
                        if (selectedSeatText != null) {
                            selectedSeatText.setText("Selected Seat: " + selectedSeat);
                        }
                    });
                }
                seatGrid.addView(seatBtn);
            }
        }
    }
}
