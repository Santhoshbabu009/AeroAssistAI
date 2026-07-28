package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;

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

public class LoungeListActivity extends BaseActivity {

    private ImageView backBtn, bookingHistoryBtn;
    private RecyclerView recyclerView;

    private androidx.cardview.widget.CardView floatingBookingCard;
    private TextView floatingBookingText;
    private Handler statusPollHandler;
    private Runnable statusPollRunnable;

    private OkHttpClient client;
    private List<JSONObject> loungeList = new ArrayList<>();
    private LoungeAdapter adapter;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lounge_list);

        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        bookingHistoryBtn = findViewById(R.id.bookingHistoryBtn);
        recyclerView = findViewById(R.id.recyclerView);
        
        floatingBookingCard = findViewById(R.id.floatingBookingCard);
        floatingBookingText = findViewById(R.id.floatingBookingText);
        statusPollHandler = new Handler();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LoungeAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
        bookingHistoryBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoungeBookingHistoryActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        statusPollRunnable = new Runnable() {
            @Override
            public void run() {
                checkLatestBookingStatus();
                statusPollHandler.postDelayed(this, 5000); // Poll status every 5 seconds
            }
        };

        fetchLounges();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusPollHandler.post(statusPollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        statusPollHandler.removeCallbacks(statusPollRunnable);
    }

    private void checkLatestBookingStatus() {
        if (email == null || email.isEmpty()) return;
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
                            if (arr.length() > 0) {
                                JSONObject latestBooking = arr.getJSONObject(0);
                                String status = latestBooking.optString("status", "Pending");
                                String loungeName = latestBooking.optString("lounge_name", "Premium Lounge");
                                int slots = latestBooking.optInt("slots", 1);
                                String guestsStr = slots + (slots == 1 ? " Guest" : " Guests");
                                String displayText = loungeName + " • " + guestsStr 
                                        + "\nStatus: " + status.toUpperCase();

                                runOnUiThread(() -> {
                                    floatingBookingCard.setVisibility(View.VISIBLE);
                                    floatingBookingText.setText(displayText);
                                    
                                    // Premium colors matching booking status
                                    if ("Pending".equals(status)) {
                                        floatingBookingCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF57C00)); // Premium Orange
                                    } else if ("Confirmed".equals(status)) {
                                        floatingBookingCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E7D32)); // Premium Green
                                    } else { // Cancelled
                                        floatingBookingCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828)); // Dark Red
                                    }
                                    
                                    floatingBookingCard.setOnClickListener(v -> {
                                        Intent intent = new Intent(LoungeListActivity.this, LoungeBookingHistoryActivity.class);
                                        intent.putExtra("email", email);
                                        startActivity(intent);
                                    });
                                });
                            } else {
                                runOnUiThread(() -> floatingBookingCard.setVisibility(View.GONE));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void fetchLounges() {
        String url = Constants.BACKEND_BASE_URL + "/api/lounges";
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
                            JSONArray arr = json.getJSONArray("lounges");
                            loungeList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                loungeList.add(arr.getJSONObject(i));
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

    private class LoungeAdapter extends RecyclerView.Adapter<LoungeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lounge, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject lounge = loungeList.get(position);
            long id = lounge.optLong("id");
            String nameStr = lounge.optString("name");
            String term = lounge.optString("terminal");
            String gate = lounge.optString("gate");
            String availability = lounge.optString("status", "Available");
            String imgUrl = lounge.optString("image_url");

            holder.nameText.setText(nameStr);
            holder.detailsText.setText(term + " • " + gate);
            
            // Availability badge styling
            if ("Available".equalsIgnoreCase(availability)) {
                holder.availabilityBadge.setText("AVAILABLE");
                holder.availabilityBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                holder.availabilityBadge.setTextColor(0xFF2E7D32);
            } else {
                holder.availabilityBadge.setText("FULL");
                holder.availabilityBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
                holder.availabilityBadge.setTextColor(0xFFC62828);
            }

            // Deterministic slot price
            double slotPrice = 1000.0 + (id % 5) * 200.0;
            holder.priceText.setText("₹" + String.format("%.0f", slotPrice) + "/slot");

            if (imgUrl != null && !imgUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(imgUrl)
                        .placeholder(R.drawable.certificate_bg)
                        .into(holder.loungeImage);
            } else {
                holder.loungeImage.setImageResource(R.drawable.certificate_bg);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(LoungeListActivity.this, LoungeDetailsActivity.class);
                intent.putExtra("lounge_id", id);
                intent.putExtra("lounge_name", nameStr);
                intent.putExtra("lounge_price", slotPrice);
                intent.putExtra("lounge_image_url", imgUrl);
                intent.putExtra("email", email);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return loungeList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, detailsText, availabilityBadge, priceText;
            ImageView loungeImage;

            ViewHolder(View v) {
                super(v);
                nameText = v.findViewById(R.id.loungeName);
                detailsText = v.findViewById(R.id.loungeDetails);
                availabilityBadge = v.findViewById(R.id.availabilityBadge);
                priceText = v.findViewById(R.id.loungePrice);
                loungeImage = v.findViewById(R.id.loungeImage);
            }
        }
    }
}
