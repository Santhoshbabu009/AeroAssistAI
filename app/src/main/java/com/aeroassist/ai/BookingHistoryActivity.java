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

    private List<JSONObject> buildDemoBookings() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONObject booking = new JSONObject();
            booking.put("id", "AA8921");
            booking.put("pnr", "AA8921");
            booking.put("booking_id", "BK-892102");
            booking.put("ticket_number", "TKT-9920192");
            booking.put("payment_id", "PAY-8810239");
            booking.put("transaction_id", "TXN-7781920192");
            booking.put("booking_status", "Confirmed");
            booking.put("departure_date", "2026-08-01");

            JSONObject flight = new JSONObject();
            flight.put("airline", "Air India");
            flight.put("flight_number", "AI-432");
            flight.put("origin", "MAA");
            flight.put("destination", "DEL");
            flight.put("date", "2026-08-01");
            flight.put("departure_time", "06:00 AM");
            flight.put("arrival_time", "08:15 AM");
            flight.put("duration", "2h 15m");
            flight.put("stops", "Non-stop");
            flight.put("cabinClass", "Economy");
            flight.put("baggage", "25 kg Check-in + 7 kg Hand Bag");

            JSONObject pax = new JSONObject();
            pax.put("name", "Santhosh Babu");
            pax.put("seat", "12A");
            pax.put("gender", "Male");
            pax.put("age", "28");

            JSONArray paxArray = new JSONArray();
            paxArray.put(pax);

            booking.put("flight_details", flight);
            booking.put("passenger_details", paxArray);

            list.add(booking);
        } catch (Exception ignored) {}
        return list;
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
                    displayList(buildDemoBookings());
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
                            List<JSONObject> bookingsList = new ArrayList<>();
                            for (int i = 0; i < bookingsArray.length(); i++) {
                                bookingsList.add(bookingsArray.getJSONObject(i));
                            }
                            if (bookingsList.isEmpty()) {
                                displayList(buildDemoBookings());
                            } else {
                                displayList(bookingsList);
                            }
                        } else {
                            displayList(buildDemoBookings());
                        }
                    } catch (Exception e) {
                        displayList(buildDemoBookings());
                    }
                });
            }
        });
    }

    private void displayList(List<JSONObject> list) {
        if (list == null || list.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            bookingsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            bookingsRecyclerView.setVisibility(View.VISIBLE);
            bookingsRecyclerView.setAdapter(new BookingAdapter(list));
        }
    }

    private class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.BookingViewHolder> {

        private final List<JSONObject> bookingList;

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
                String pnr = booking.optString("pnr", booking.optString("booking_id", "AA8921"));
                String status = booking.optString("booking_status", booking.optString("status", "Confirmed")).toUpperCase();
                JSONObject flight = booking.optJSONObject("flight_details");
                if (flight == null) flight = new JSONObject();

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

                String orig = flight.optString("origin", "MAA");
                String dest = flight.optString("destination", "DEL");
                holder.bookingRoute.setText(orig + " ➔ " + dest);

                String fDate = flight.optString("date", booking.optString("departure_date", "2026-08-01"));
                String airline = flight.optString("airline", "Air India");
                String flightNum = flight.optString("flight_number", "AI-432");
                holder.bookingMeta.setText(fDate + " | " + airline + " (" + flightNum + ")");

                String depTime = flight.optString("departure_time", "06:00 AM");
                String arrTime = flight.optString("arrival_time", "08:15 AM");
                holder.bookingTime.setText(depTime + " - " + arrTime);

                final String finalPnr = pnr;
                holder.viewTicketBtn.setOnClickListener(v -> {
                    Intent intent = new Intent(BookingHistoryActivity.this, ETicketActivity.class);
                    intent.putExtra("PNR", finalPnr);
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
