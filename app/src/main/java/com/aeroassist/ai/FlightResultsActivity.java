package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FlightResultsActivity extends BaseActivity {

    private ImageButton backBtn;
    private TextView routeTitle;
    private ProgressBar progressBar;
    private RecyclerView flightRecyclerView;

    private String origin, destination, date;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_results);

        client = new OkHttpClient();

        origin = getIntent().getStringExtra("ORIGIN");
        destination = getIntent().getStringExtra("DESTINATION");
        date = getIntent().getStringExtra("DATE");

        backBtn = findViewById(R.id.backBtn);
        routeTitle = findViewById(R.id.routeTitle);
        progressBar = findViewById(R.id.progressBar);
        flightRecyclerView = findViewById(R.id.flightRecyclerView);

        flightRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        if (origin != null && destination != null) {
            routeTitle.setText(origin + " ➔ " + destination);
        }

        backBtn.setOnClickListener(v -> finish());

        fetchFlights();
    }

    /** Build 6 hardcoded demo flights so the UI always works even if API is down */
    private List<JSONObject> buildDemoFlights() {
        List<JSONObject> list = new ArrayList<>();
        String[][] demos = {
            {"Air India",  "AI-101", "06:00", "08:15", "2h 15m", "Non-stop",     "4500"},
            {"IndiGo",     "6E-203", "09:30", "11:45", "2h 15m", "Non-stop",     "5200"},
            {"SpiceJet",   "SG-315", "13:15", "15:40", "2h 25m", "Non-stop",     "3990"},
            {"Vistara",    "UK-407", "17:45", "20:05", "2h 20m", "Non-stop",     "6100"},
            {"Go First",   "G8-521", "21:10", "23:30", "2h 20m", "Non-stop",     "3500"},
            {"Akasa Air",  "QP-619", "11:00", "16:30", "5h 30m", "1 Stop (HYD)", "7500"}
        };
        for (String[] d : demos) {
            try {
                JSONObject f = new JSONObject();
                f.put("airline",         d[0]);
                f.put("flight_number",   d[1]);
                f.put("departure_time",  d[2]);
                f.put("arrival_time",    d[3]);
                f.put("duration",        d[4]);
                f.put("stops",           d[5]);
                f.put("base_fare",       Integer.parseInt(d[6]));
                f.put("price_per_pax",   Integer.parseInt(d[6]));
                f.put("origin",          origin != null ? origin : "MAA");
                f.put("destination",     destination != null ? destination : "DEL");
                f.put("departure_date",  date != null ? date : "2026-08-01");
                f.put("baggage",         "15 kg + 7 kg Hand");
                f.put("aircraft",        "Airbus A320neo");
                list.add(f);
            } catch (Exception ignored) {}
        }
        return list;
    }

    private void fetchFlights() {
        progressBar.setVisibility(View.VISIBLE);
        String url = Constants.FLIGHT_SEARCH_ENDPOINT
                + "?origin=" + (origin != null ? origin : "MAA")
                + "&destination=" + (destination != null ? destination : "DEL")
                + "&date=" + (date != null ? date : "");

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // Network failed → fall back to demo flights
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showFlights(buildDemoFlights());
                    Toast.makeText(FlightResultsActivity.this,
                            "Showing demo flights (offline mode)", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.optString("status").equals("success")) {
                            JSONArray arr = json.getJSONArray("flights");
                            List<JSONObject> flights = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject f = arr.getJSONObject(i);
                                // Normalise: ensure base_fare exists
                                if (!f.has("base_fare") || f.getInt("base_fare") == 0) {
                                    f.put("base_fare", f.optInt("price_per_pax", 4500));
                                }
                                flights.add(f);
                            }
                            if (flights.isEmpty()) {
                                showFlights(buildDemoFlights());
                            } else {
                                showFlights(flights);
                            }
                        } else {
                            showFlights(buildDemoFlights());
                        }
                    } catch (Exception e) {
                        showFlights(buildDemoFlights());
                    }
                });
            }
        });
    }

    private void showFlights(List<JSONObject> flights) {
        flightRecyclerView.setAdapter(new FlightAdapter(flights));
    }

    // ─── Adapter ───────────────────────────────────────────────────────────────

    private class FlightAdapter extends RecyclerView.Adapter<FlightAdapter.FlightViewHolder> {

        private final List<JSONObject> flightList;

        FlightAdapter(List<JSONObject> flightList) {
            this.flightList = flightList;
        }

        @NonNull
        @Override
        public FlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_flight_result, parent, false);
            return new FlightViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FlightViewHolder holder, int position) {
            JSONObject flight = flightList.get(position);
            try {
                holder.airlineName.setText(flight.optString("airline", "Airline"));
                holder.flightNumber.setText(flight.optString("flight_number", "-"));

                // Safely read fare – backend may use price_per_pax OR base_fare
                int fare = flight.optInt("base_fare", 0);
                if (fare == 0) fare = flight.optInt("price_per_pax", 0);
                holder.flightFare.setText("₹" + fare);

                holder.depTime.setText(flight.optString("departure_time", "--:--"));
                holder.originText.setText(flight.optString("origin", ""));
                holder.durationText.setText(flight.optString("duration", ""));
                holder.stopsText.setText(flight.optString("stops", ""));
                holder.arrTime.setText(flight.optString("arrival_time", "--:--"));
                holder.destText.setText(flight.optString("destination", ""));

                // Ensure base_fare is stored correctly before passing along
                if (!flight.has("base_fare") || flight.getInt("base_fare") == 0) {
                    flight.put("base_fare", fare);
                }

                holder.selectFlightBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(FlightResultsActivity.this, SeatPassengerActivity.class);
                    intent.putExtra("FLIGHT_JSON", flight.toString());
                    intent.putExtra("DATE", date);
                    startActivity(intent);
                });

            } catch (Exception e) {
                e.printStackTrace();
                // Even on partial error, still set the click listener so SELECT works
                holder.selectFlightBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(FlightResultsActivity.this, SeatPassengerActivity.class);
                    intent.putExtra("FLIGHT_JSON", flight.toString());
                    intent.putExtra("DATE", date);
                    startActivity(intent);
                });
            }
        }

        @Override
        public int getItemCount() {
            return flightList.size();
        }

        class FlightViewHolder extends RecyclerView.ViewHolder {
            TextView airlineName, flightNumber, flightFare, depTime, originText,
                    durationText, stopsText, arrTime, destText;
            Button selectFlightBtn;

            FlightViewHolder(@NonNull View itemView) {
                super(itemView);
                airlineName    = itemView.findViewById(R.id.airlineName);
                flightNumber   = itemView.findViewById(R.id.flightNumber);
                flightFare     = itemView.findViewById(R.id.flightFare);
                depTime        = itemView.findViewById(R.id.depTime);
                originText     = itemView.findViewById(R.id.originText);
                durationText   = itemView.findViewById(R.id.durationText);
                stopsText      = itemView.findViewById(R.id.stopsText);
                arrTime        = itemView.findViewById(R.id.arrTime);
                destText       = itemView.findViewById(R.id.destText);
                selectFlightBtn = itemView.findViewById(R.id.selectFlightBtn);
            }
        }
    }
}
