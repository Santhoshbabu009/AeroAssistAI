package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
    private RecyclerView parkingRecyclerView;
    private Button tabFlights, tabParking;
    private OkHttpClient client;

    private List<JSONObject> flightList = new ArrayList<>();
    private List<JSONObject> parkingList = new ArrayList<>();

    private boolean showingFlights = true;

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
        parkingRecyclerView = findViewById(R.id.parkingRecyclerView);
        tabFlights = findViewById(R.id.tabFlights);
        tabParking = findViewById(R.id.tabParking);

        bookingsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        parkingRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        tabFlights.setOnClickListener(v -> switchTab(true));
        tabParking.setOnClickListener(v -> switchTab(false));
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchBookings();
        fetchParkingFromApi();
    }

    private void switchTab(boolean flights) {
        showingFlights = flights;
        if (flights) {
            tabFlights.setBackgroundColor(Color.parseColor("#00E5FF"));
            tabFlights.setTextColor(Color.parseColor("#000000"));
            tabParking.setBackgroundColor(Color.parseColor("#1E293B"));
            tabParking.setTextColor(Color.parseColor("#94A3B8"));
            bookingsRecyclerView.setVisibility(View.VISIBLE);
            parkingRecyclerView.setVisibility(View.GONE);
            showFlightEmpty(flightList.isEmpty());
        } else {
            tabParking.setBackgroundColor(Color.parseColor("#00E5FF"));
            tabParking.setTextColor(Color.parseColor("#000000"));
            tabFlights.setBackgroundColor(Color.parseColor("#1E293B"));
            tabFlights.setTextColor(Color.parseColor("#94A3B8"));
            bookingsRecyclerView.setVisibility(View.GONE);
            parkingRecyclerView.setVisibility(View.VISIBLE);
            showParkingEmpty(parkingList.isEmpty());
        }
    }

    private void showFlightEmpty(boolean empty) {
        if (empty) {
            emptyText.setText("No flight bookings found.");
            emptyText.setVisibility(View.VISIBLE);
            bookingsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            bookingsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showParkingEmpty(boolean empty) {
        if (empty) {
            emptyText.setText("No parking bookings found.");
            emptyText.setVisibility(View.VISIBLE);
            parkingRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            parkingRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private String getLoggedInEmail() {
        SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        String email = session.getString("email", session.getString("user_email", null));
        if (email == null || email.isEmpty()) {
            SharedPreferences userSession = getSharedPreferences("UserSession", MODE_PRIVATE);
            email = userSession.getString("user_email", userSession.getString("email", "demo@aeroassist.ai"));
        }
        return email;
    }

    /** Fetch real parking bookings from backend API */
    private void fetchParkingFromApi() {
        String email = getLoggedInEmail();
        Log.d("BookingHistory", "Fetching parking bookings for email: [" + email + "]");
        String encodedEmail;
        try { encodedEmail = URLEncoder.encode(email, "UTF-8"); } catch (UnsupportedEncodingException e) { encodedEmail = email; }
        String url = Constants.PARKING_BOOKINGS_ENDPOINT + "?email=" + encodedEmail;
        Request request = new Request.Builder().url(url).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    parkingList.clear();
                    parkingRecyclerView.setAdapter(new ParkingAdapter(parkingList));
                    if (!showingFlights) showParkingEmpty(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String body = response.body() != null ? response.body().string() : "{}";
                runOnUiThread(() -> {
                    parkingList.clear();
                    try {
                        JSONObject json = new JSONObject(body);
                        if (json.optString("status").equals("success")) {
                            JSONArray arr = json.getJSONArray("bookings");
                            for (int i = 0; i < arr.length(); i++) {
                                parkingList.add(arr.getJSONObject(i));
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    parkingRecyclerView.setAdapter(new ParkingAdapter(parkingList));
                    if (!showingFlights) showParkingEmpty(parkingList.isEmpty());
                });
            }
        });
    }

    private List<JSONObject> buildDemoFlightBookings() {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONObject booking = new JSONObject();
            booking.put("pnr", "AA8921");
            booking.put("booking_id", "BK-892102");
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
            booking.put("flight_details", flight);

            list.add(booking);
        } catch (Exception ignored) {}
        return list;
    }

    private void fetchBookings() {
        progressBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);

        String email = getLoggedInEmail();
        Log.d("BookingHistory", "Fetching flight bookings for email: [" + email + "]");
        String encodedEmail;
        try { encodedEmail = URLEncoder.encode(email, "UTF-8"); } catch (UnsupportedEncodingException e) { encodedEmail = email; }
        String url = Constants.FLIGHT_BOOKINGS_ENDPOINT + "?email=" + encodedEmail;

        Request request = new Request.Builder().url(url).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    flightList = new ArrayList<>();
                    bookingsRecyclerView.setAdapter(new FlightAdapter(flightList));
                    if (showingFlights) showFlightEmpty(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String respStr = response.body() != null ? response.body().string() : "{}";
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    flightList = new ArrayList<>();
                    try {
                        JSONObject jsonObject = new JSONObject(respStr);
                        if (jsonObject.optString("status").equals("success")) {
                            JSONArray arr = jsonObject.getJSONArray("bookings");
                            for (int i = 0; i < arr.length(); i++) {
                                flightList.add(arr.getJSONObject(i));
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    bookingsRecyclerView.setAdapter(new FlightAdapter(flightList));
                    if (showingFlights) showFlightEmpty(flightList.isEmpty());
                });
            }
        });
    }

    // ─────────────────── FLIGHT ADAPTER ───────────────────
    private class FlightAdapter extends RecyclerView.Adapter<FlightAdapter.FlightVH> {
        private final List<JSONObject> list;
        FlightAdapter(List<JSONObject> list) { this.list = list; }

        @NonNull @Override
        public FlightVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_flight_booking, parent, false);
            return new FlightVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FlightVH h, int pos) {
            JSONObject b = list.get(pos);
            try {
                String pnr = b.optString("pnr", b.optString("booking_id", "—"));
                String status = b.optString("booking_status", b.optString("status", "Confirmed")).toUpperCase();
                JSONObject flight = b.optJSONObject("flight_details");
                if (flight == null) flight = new JSONObject();

                h.pnr.setText("PNR: " + pnr);
                h.status.setText(status);

                if (status.equalsIgnoreCase("PENDING")) {
                    h.status.setBackgroundColor(Color.parseColor("#FF9800"));
                } else if (status.equalsIgnoreCase("COMPLETED")) {
                    h.status.setBackgroundColor(Color.parseColor("#059669"));
                } else {
                    h.status.setBackgroundColor(Color.parseColor("#00E5FF"));
                    h.status.setTextColor(Color.BLACK);
                }

                h.route.setText(flight.optString("origin", "MAA") + " ➔ " + flight.optString("destination", "DEL"));
                h.meta.setText(flight.optString("date", "2026-08-01") + " | "
                        + flight.optString("airline", "Airline") + " ("
                        + flight.optString("flight_number", "—") + ")");
                h.time.setText(flight.optString("departure_time", "—") + " - " + flight.optString("arrival_time", "—"));

                final String finalPnr = pnr;
                h.btn.setText("VIEW E-TICKET");
                h.btn.setOnClickListener(v -> {
                    Intent intent = new Intent(BookingHistoryActivity.this, ETicketActivity.class);
                    intent.putExtra("PNR", finalPnr);
                    startActivity(intent);
                });
            } catch (Exception e) { e.printStackTrace(); }
        }

        @Override public int getItemCount() { return list.size(); }

        class FlightVH extends RecyclerView.ViewHolder {
            TextView pnr, status, route, meta, time;
            Button btn;
            FlightVH(View v) {
                super(v);
                pnr    = v.findViewById(R.id.bookingPnr);
                status = v.findViewById(R.id.bookingStatus);
                route  = v.findViewById(R.id.bookingRoute);
                meta   = v.findViewById(R.id.bookingMeta);
                time   = v.findViewById(R.id.bookingTime);
                btn    = v.findViewById(R.id.viewTicketBtn);
            }
        }
    }

    // ─────────────────── PARKING ADAPTER ───────────────────
    private class ParkingAdapter extends RecyclerView.Adapter<ParkingAdapter.ParkingVH> {
        private final List<JSONObject> list;
        ParkingAdapter(List<JSONObject> list) { this.list = list; }

        @NonNull @Override
        public ParkingVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_parking_booking, parent, false);
            return new ParkingVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ParkingVH h, int pos) {
            JSONObject b = list.get(pos);
            try {
                // Support both backend field names (booking_id, slot_number) and legacy (id, slot)
                String id      = b.optString("booking_id", b.optString("id", "PRK-" + (pos + 1)));
                String status  = b.optString("booking_status", b.optString("status", "Confirmed")).toUpperCase();
                String slot    = b.optString("slot_number", b.optString("slot", "Auto-Assigned"));
                String plate   = b.optString("plate_number", b.optString("plate_number", b.optString("plate", "—")));
                String dur     = b.optString("duration_hours", b.optString("hours", "—")) + " hrs";
                String terminal = b.optString("terminal", "—");
                String entry   = b.optString("entry_time", b.optString("date", "—"));
                String price   = b.optString("total_price", b.optString("price", "—"));

                h.id.setText("Booking: " + id);
                h.status.setText(status);
                if (status.equalsIgnoreCase("PENDING")) {
                    h.status.setBackgroundColor(Color.parseColor("#FF9800"));
                } else {
                    h.status.setBackgroundColor(Color.parseColor("#059669"));
                }
                h.slot.setText("🅿  Slot: " + slot + "  |  Terminal: " + terminal);
                h.plate.setText("🚗  Vehicle: " + plate + "  |  ₹" + price);
                h.duration.setText("⏱  " + dur + "  |  📅  " + entry);
            } catch (Exception e) { e.printStackTrace(); }
        }

        @Override public int getItemCount() { return list.size(); }

        class ParkingVH extends RecyclerView.ViewHolder {
            TextView id, status, slot, plate, duration;
            ParkingVH(View v) {
                super(v);
                id       = v.findViewById(R.id.parkingId);
                status   = v.findViewById(R.id.parkingStatus);
                slot     = v.findViewById(R.id.parkingSlot);
                plate    = v.findViewById(R.id.parkingPlate);
                duration = v.findViewById(R.id.parkingDuration);
            }
        }
    }
}
