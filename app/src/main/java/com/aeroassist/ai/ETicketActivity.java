package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ETicketActivity extends BaseActivity {

    private String pnr;
    private OkHttpClient client;
    private ProgressBar progressBar;
    private LinearLayout ticketContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eticket);

        client = new OkHttpClient();
        pnr = getIntent().getStringExtra("PNR");

        ImageButton homeBtn = findViewById(R.id.homeBtn);
        homeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(ETicketActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        progressBar = findViewById(R.id.progressBar);
        ticketContainer = findViewById(R.id.ticketContainer);

        if (pnr != null && !pnr.isEmpty()) {
            fetchETicket();
        } else {
            Toast.makeText(this, "Invalid PNR", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void fetchETicket() {
        progressBar.setVisibility(View.VISIBLE);
        String url = Constants.FLIGHT_BOOKINGS_ENDPOINT + "/" + pnr;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ETicketActivity.this, "Network Error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.body() != null ? response.body().string() : "{}";
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject jsonObject = new JSONObject(respStr);
                        if (jsonObject.optString("status").equals("success")) {
                            JSONObject booking = jsonObject.getJSONObject("booking");
                            JSONObject flight = booking.getJSONObject("flight_details");
                            JSONArray paxArray = booking.getJSONArray("passenger_details");
                            JSONObject pax = paxArray.length() > 0 ? paxArray.getJSONObject(0) : new JSONObject();

                            TextView eticketPnr = findViewById(R.id.eticketPnr);
                            eticketPnr.setText(booking.optString("pnr", pnr));

                            TextView eticketOrig = findViewById(R.id.eticketOrig);
                            eticketOrig.setText(flight.optString("origin"));

                            TextView eticketDest = findViewById(R.id.eticketDest);
                            eticketDest.setText(flight.optString("destination"));

                            TextView eticketPaxName = findViewById(R.id.eticketPaxName);
                            eticketPaxName.setText(pax.optString("name", "Unknown"));

                            TextView eticketSeat = findViewById(R.id.eticketSeat);
                            eticketSeat.setText(pax.optString("seat", "-"));

                            TextView eticketFlight = findViewById(R.id.eticketFlight);
                            eticketFlight.setText(flight.optString("airline") + " " + flight.optString("flight_number"));

                            TextView eticketDep = findViewById(R.id.eticketDep);
                            eticketDep.setText(flight.optString("date") + " " + flight.optString("departure_time"));

                            ticketContainer.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(ETicketActivity.this, "Failed to load ticket", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(ETicketActivity.this, "Parsing Error", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}
