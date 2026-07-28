package com.aeroassist.ai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LoungeBookingHistoryActivity extends BaseActivity {

    private ImageView backBtn;
    private RecyclerView recyclerView;

    private String email;
    private OkHttpClient client;
    private List<JSONObject> bookingsList = new ArrayList<>();
    private BookingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lounge_booking_history);

        email = getIntent().getStringExtra("email");
        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BookingsAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchBookings();
    }

    private void fetchBookings() {
        String url = Constants.BACKEND_BASE_URL + "/api/bookings?email=" + email;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String res = response.body().string();
                    try {
                        JSONObject json = new JSONObject(res);
                        if ("success".equals(json.optString("status"))) {
                            JSONArray arr = json.getJSONArray("bookings");
                            bookingsList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                bookingsList.add(arr.getJSONObject(i));
                            }
                            runOnUiThread(() -> adapter.notifyDataSetChanged());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private class BookingsAdapter extends RecyclerView.Adapter<BookingsAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lounge_booking, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject booking = bookingsList.get(position);
            long id = booking.optLong("id");
            String loungeName = booking.optString("vendor_name", "Lounge");
            String status = booking.optString("status", "Pending");
            String dateStr = booking.optString("booking_date", "");
            String timeStr = booking.optString("booking_time", "");
            int slots = booking.optInt("slots", 1);
            
            // Deterministic slot price based on vendor id
            long vendorId = booking.optLong("vendor_id", 1);
            double slotPrice = 1000.0 + (vendorId % 5) * 200.0;
            double total = slots * slotPrice;

            holder.loungeName.setText(loungeName);
            holder.bookingDetails.setText("Booking ID: #" + id + " â€¢ " + slots + (slots == 1 ? " Guest" : " Guests"));
            holder.dateText.setText(dateStr);
            holder.timeText.setText(timeStr);
            holder.priceText.setText("Total Amount: â‚¹" + String.format("%.2f", total));
            holder.statusBadge.setText(status.toUpperCase());

            // Set badge colors
            if ("Pending".equals(status)) {
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE0B2));
                holder.statusBadge.setTextColor(0xFFF57C00);
            } else if ("Confirmed".equals(status)) {
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                holder.statusBadge.setTextColor(0xFF2E7D32);
            } else { // Cancelled
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
                holder.statusBadge.setTextColor(0xFFC62828);
            }
        }

        @Override
        public int getItemCount() {
            return bookingsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView loungeName, bookingDetails, dateText, timeText, priceText, statusBadge;

            ViewHolder(View v) {
                super(v);
                loungeName = v.findViewById(R.id.loungeNameText);
                bookingDetails = v.findViewById(R.id.bookingDetailsText);
                dateText = v.findViewById(R.id.dateText);
                timeText = v.findViewById(R.id.timeText);
                priceText = v.findViewById(R.id.totalPriceText);
                statusBadge = v.findViewById(R.id.statusBadge);
            }
        }
    }
}
