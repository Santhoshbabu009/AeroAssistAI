package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
import androidx.appcompat.app.AppCompatActivity;
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

public class BookingHistoryActivity extends BaseActivity {

    private ProgressBar progressBar;
    private TextView emptyText;
    private RecyclerView bookingsRecyclerView;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_history);

        client = new OkHttpClient();

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progressBar);
        emptyText = findViewById(R.id.emptyText);
        bookingsRecyclerView = findViewById(R.id.bookingsRecyclerView);
        
        bookingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        fetchBookings();
    }

    private void fetchBookings() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        String email = prefs.getString("user_email", "demo@aeroassist.ai");

        String url = Constants.FLIGHT_BOOKINGS_ENDPOINT + "?email=" + email;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BookingHistoryActivity.this, "Failed to load bookings", Toast.LENGTH_SHORT).show();
                    emptyText.setVisibility(View.VISIBLE);
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
                            JSONArray bookingsArray = jsonObject.getJSONArray("bookings");
                            if (bookingsArray.length() == 0) {
                                emptyText.setVisibility(View.VISIBLE);
                            } else {
                                List<JSONObject> bookingsList = new ArrayList<>();
                                for (int i = 0; i < bookingsArray.length(); i++) {
                                    bookingsList.add(bookingsArray.getJSONObject(i));
                                }
                                bookingsRecyclerView.setAdapter(new BookingAdapter(bookingsList));
                            }
                        } else {
                            emptyText.setVisibility(View.VISIBLE);
                            Toast.makeText(BookingHistoryActivity.this, "Error fetching bookings", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        emptyText.setVisibility(View.VISIBLE);
                        Toast.makeText(BookingHistoryActivity.this, "Error parsing bookings", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

        private List<JSONObject> bookingList;

        public BookingAdapter(List<JSONObject> bookingList) {
            this.bookingList = bookingList;
        }

        @NonNull
        @Override
        public BookingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flight_booking, parent, false);
            return new BookingViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BookingViewHolder holder, int position) {
            JSONObject booking = bookingList.get(position);
            try {
                String pnr = booking.optString("pnr");
                String status = booking.optString("status", "confirmed").toUpperCase();
                JSONObject flight = booking.getJSONObject("flight_details");

                holder.bookingPnr.setText("PNR: " + pnr);
                holder.bookingStatus.setText(status);
                
                if (status.equalsIgnoreCase("PENDING")) {
                    holder.bookingStatus.setBackgroundColor(Color.parseColor("#FF9800"));
                } else if (status.equalsIgnoreCase("COMPLETED")) {
                    holder.bookingStatus.setBackgroundColor(Color.parseColor("#059669"));
                } else {
                    holder.bookingStatus.setBackgroundColor(Color.parseColor("#00E5FF"));
                    holder.bookingStatus.setTextColor(Color.BLACK);
                }

                holder.bookingRoute.setText(flight.optString("origin") + " ➔ " + flight.optString("destination"));
                holder.bookingMeta.setText(flight.optString("date") + " | " + flight.optString("airline") + " (" + flight.optString("flight_number") + ")");
                holder.bookingTime.setText(flight.optString("departure_time") + " - " + flight.optString("arrival_time"));

                holder.viewTicketBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(BookingHistoryActivity.this, ETicketActivity.class);
                    intent.putExtra("PNR", pnr);
                    startActivity(intent);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return bookingList.size();
        }

        class BookingViewHolder extends RecyclerView.ViewHolder {
            TextView bookingPnr, bookingStatus, bookingRoute, bookingMeta, bookingTime;
            Button viewTicketBtn;

            public BookingViewHolder(@NonNull View itemView) {
                super(itemView);
                bookingPnr = itemView.findViewById(R.id.bookingPnr);
                bookingStatus = itemView.findViewById(R.id.bookingStatus);
                bookingRoute = itemView.findViewById(R.id.bookingRoute);
                bookingMeta = itemView.findViewById(R.id.bookingMeta);
                bookingTime = itemView.findViewById(R.id.bookingTime);
                viewTicketBtn = itemView.findViewById(R.id.viewTicketBtn);
            }
        }
    }
}
