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

    private void fetchFlights() {
        progressBar.setVisibility(View.VISIBLE);
        String url = Constants.FLIGHT_SEARCH_ENDPOINT + "?origin=" + origin + "&destination=" + destination + "&date=" + date;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(FlightResultsActivity.this, "Failed to load flights", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonString = response.body().string();
                        JSONObject jsonObject = new JSONObject(jsonString);
                        if (jsonObject.getString("status").equals("success")) {
                            JSONArray flightsArray = jsonObject.getJSONArray("flights");
                            List<JSONObject> flights = new ArrayList<>();
                            for (int i = 0; i < flightsArray.length(); i++) {
                                flights.add(flightsArray.getJSONObject(i));
                            }
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                flightRecyclerView.setAdapter(new FlightAdapter(flights));
                            });
                        } else {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(FlightResultsActivity.this, "Error fetching flights", Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(FlightResultsActivity.this, "Error parsing flights", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(FlightResultsActivity.this, "Server error", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private class FlightAdapter extends RecyclerView.Adapter<FlightAdapter.FlightViewHolder> {

        private List<JSONObject> flightList;

        public FlightAdapter(List<JSONObject> flightList) {
            this.flightList = flightList;
        }

        @NonNull
        @Override
        public FlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flight_result, parent, false);
            return new FlightViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FlightViewHolder holder, int position) {
            JSONObject flight = flightList.get(position);
            try {
                holder.airlineName.setText(flight.getString("airline"));
                holder.flightNumber.setText(flight.getString("flight_number"));
                holder.flightFare.setText("₹" + flight.getInt("base_fare"));
                holder.depTime.setText(flight.getString("departure_time"));
                holder.originText.setText(flight.getString("origin"));
                holder.durationText.setText(flight.getString("duration"));
                holder.stopsText.setText(flight.getString("stops"));
                holder.arrTime.setText(flight.getString("arrival_time"));
                holder.destText.setText(flight.getString("destination"));

                holder.selectFlightBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(FlightResultsActivity.this, SeatPassengerActivity.class);
                    intent.putExtra("FLIGHT_JSON", flight.toString());
                    intent.putExtra("DATE", date);
                    startActivity(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return flightList.size();
        }

        class FlightViewHolder extends RecyclerView.ViewHolder {
            TextView airlineName, flightNumber, flightFare, depTime, originText, durationText, stopsText, arrTime, destText;
            Button selectFlightBtn;

            public FlightViewHolder(@NonNull View itemView) {
                super(itemView);
                airlineName = itemView.findViewById(R.id.airlineName);
                flightNumber = itemView.findViewById(R.id.flightNumber);
                flightFare = itemView.findViewById(R.id.flightFare);
                depTime = itemView.findViewById(R.id.depTime);
                originText = itemView.findViewById(R.id.originText);
                durationText = itemView.findViewById(R.id.durationText);
                stopsText = itemView.findViewById(R.id.stopsText);
                arrTime = itemView.findViewById(R.id.arrTime);
                destText = itemView.findViewById(R.id.destText);
                selectFlightBtn = itemView.findViewById(R.id.selectFlightBtn);
            }
        }
    }
}
