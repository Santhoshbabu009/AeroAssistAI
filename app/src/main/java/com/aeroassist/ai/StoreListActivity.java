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

public class StoreListActivity extends BaseActivity {

    private ImageView backBtn, orderHistoryBtn;
    private RecyclerView recyclerView;

    private androidx.cardview.widget.CardView floatingOrderCard;
    private TextView floatingOrderText;
    private Handler statusPollHandler;
    private Runnable statusPollRunnable;

    private OkHttpClient client;
    private List<JSONObject> storeList = new ArrayList<>();
    private StoreAdapter adapter;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_list);

        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        orderHistoryBtn = findViewById(R.id.orderHistoryBtn);
        recyclerView = findViewById(R.id.recyclerView);
        
        floatingOrderCard = findViewById(R.id.floatingOrderCard);
        floatingOrderText = findViewById(R.id.floatingOrderText);
        statusPollHandler = new Handler();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoreAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
        orderHistoryBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrderHistoryActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        statusPollRunnable = new Runnable() {
            @Override
            public void run() {
                checkLatestOrderStatus();
                statusPollHandler.postDelayed(this, 5000); // Poll status every 5 seconds
            }
        };

        fetchStores();
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

    private void checkLatestOrderStatus() {
        if (email == null || email.isEmpty()) return;
        String url = Constants.BACKEND_BASE_URL + "/api/orders?email=" + email;
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
                            JSONArray arr = json.getJSONArray("orders");
                            if (arr.length() > 0) {
                                JSONObject latestOrder = arr.getJSONObject(0);
                                long orderId = latestOrder.optLong("id");
                                String status = latestOrder.optString("status", "Pending");
                                
                                // Build items list representation
                                JSONArray items = latestOrder.optJSONArray("items");
                                StringBuilder itemsSb = new StringBuilder();
                                if (items != null) {
                                    for (int i = 0; i < items.length(); i++) {
                                        JSONObject item = items.optJSONObject(i);
                                        if (item != null) {
                                            itemsSb.append(item.optString("product_name"))
                                                    .append(" x").append(item.optInt("quantity"))
                                                    .append(", ");
                                        }
                                    }
                                }
                                if (itemsSb.length() > 2) {
                                    itemsSb.setLength(itemsSb.length() - 2);
                                }
                                String itemsStr = itemsSb.toString();
                                String displayText = (!itemsStr.isEmpty() ? itemsStr : "Order Items") 
                                        + "\nStatus: " + status.toUpperCase();

                                runOnUiThread(() -> {
                                    floatingOrderCard.setVisibility(View.VISIBLE);
                                    floatingOrderText.setText(displayText);
                                    
                                    // Set premium background color matching order status
                                    if ("Pending".equals(status)) {
                                        floatingOrderCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF57C00)); // Premium Orange
                                    } else if ("Accepted".equals(status) || "Preparing".equals(status) || "Ready".equals(status)) {
                                        floatingOrderCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF2E7D32)); // Premium Green
                                    } else if ("Delivered".equals(status)) {
                                        floatingOrderCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1E3A8A)); // Royal Blue
                                    } else { // Rejected or Cancelled
                                        floatingOrderCard.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828)); // Dark Red
                                    }
                                    
                                    floatingOrderCard.setOnClickListener(v -> {
                                        Intent intent = new Intent(StoreListActivity.this, OrderTrackingActivity.class);
                                        intent.putExtra("order_id", orderId);
                                        intent.putExtra("email", email);
                                        startActivity(intent);
                                    });
                                });
                            } else {
                                runOnUiThread(() -> floatingOrderCard.setVisibility(View.GONE));
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void fetchStores() {
        String url = Constants.BACKEND_BASE_URL + "/api/shopping";
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
                            JSONArray arr = json.getJSONArray("shopping");
                            storeList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                storeList.add(arr.getJSONObject(i));
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

    private class StoreAdapter extends RecyclerView.Adapter<StoreAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Reuse item_restaurant layout directly as it is standard and beautiful
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_restaurant, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject store = storeList.get(position);
            long id = store.optLong("id");
            String nameStr = store.optString("name");
            String term = store.optString("terminal");
            String gate = store.optString("gate");
            String imgUrl = store.optString("image_url");

            holder.nameText.setText(nameStr);
            holder.detailsText.setText(term + " â€¢ " + gate);
            
            // Random-looking but deterministic rating
            double rating = 4.0 + (id % 10) * 0.1;
            holder.ratingText.setText("â˜… " + String.format("%.1f", rating));

            if (imgUrl != null && !imgUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(imgUrl)
                        .placeholder(R.drawable.certificate_bg)
                        .into(holder.restaurantImage);
            } else {
                holder.restaurantImage.setImageResource(R.drawable.certificate_bg);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(StoreListActivity.this, StoreMenuActivity.class);
                intent.putExtra("vendor_id", id);
                intent.putExtra("vendor_name", nameStr);
                intent.putExtra("email", email);
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return storeList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, detailsText, ratingText;
            ImageView restaurantImage;

            ViewHolder(View v) {
                super(v);
                nameText = v.findViewById(R.id.restaurantName);
                detailsText = v.findViewById(R.id.restaurantDetails);
                ratingText = v.findViewById(R.id.restaurantRating);
                restaurantImage = v.findViewById(R.id.restaurantImage);
            }
        }
    }
}
