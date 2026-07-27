package com.aeroassist.ai;

import android.content.Intent;
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

public class OrderHistoryActivity extends AppCompatActivity {

    private ImageView backBtn;
    private RecyclerView recyclerView;

    private String email;
    private OkHttpClient client;
    private List<JSONObject> ordersList = new ArrayList<>();
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_history);

        email = getIntent().getStringExtra("email");
        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchOrderHistory();
    }

    private void fetchOrderHistory() {
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
                            ordersList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                ordersList.add(arr.getJSONObject(i));
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

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject order = ordersList.get(position);
            long id = order.optLong("id");
            String vendorName = order.optString("vendor_name", "Restaurant");
            String status = order.optString("status", "Pending");
            String created = order.optString("created_at", "");
            double total = order.optDouble("total_price", 0.0);

            String paymentMethod = order.optString("payment_method", "COD");
            holder.restaurantName.setText(vendorName);
            holder.orderDate.setText("Order #" + id + " • " + created.replace("T", " ").substring(0, Math.min(created.length(), 16)));
            holder.priceText.setText("Total Amount: ₹" + String.format("%.2f", total) + " [" + paymentMethod + "]");
            holder.statusBadge.setText(status.toUpperCase());

            // Build items summary
            JSONArray items = order.optJSONArray("items");
            StringBuilder sb = new StringBuilder();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) {
                        sb.append(item.optString("product_name")).append(" x").append(item.optInt("quantity")).append(", ");
                    }
                }
            }
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
            holder.itemsText.setText(sb.toString());

            // Set badge colors
            boolean isActive = false;
            if ("Pending".equals(status)) {
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE0B2));
                holder.statusBadge.setTextColor(0xFFF57C00);
                isActive = true;
            } else if ("Accepted".equals(status) || "Preparing".equals(status) || "Ready".equals(status)) {
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                holder.statusBadge.setTextColor(0xFF2E7D32);
                isActive = true;
            } else if ("Delivered".equals(status)) {
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                holder.statusBadge.setTextColor(0xFF2E7D32);
            } else { // Rejected or Cancelled
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
                holder.statusBadge.setTextColor(0xFFC62828);
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(OrderHistoryActivity.this, OrderTrackingActivity.class);
                intent.putExtra("order_id", id);
                intent.putExtra("email", email);
                startActivity(intent);
            });

            holder.trackIndicator.setVisibility(View.VISIBLE);
            if (isActive) {
                holder.trackIndicator.setText("TRACK ORDER >");
                holder.trackIndicator.setTextColor(0xFF2563EB); // Royal Blue for active tracking
            } else {
                holder.trackIndicator.setText("VIEW DETAILS >");
                holder.trackIndicator.setTextColor(0xFF64748B); // Slate Grey for historical reference
            }
        }

        @Override
        public int getItemCount() {
            return ordersList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView restaurantName, orderDate, itemsText, priceText, statusBadge, trackIndicator;

            ViewHolder(View v) {
                super(v);
                restaurantName = v.findViewById(R.id.restaurantNameText);
                orderDate = v.findViewById(R.id.orderDateText);
                itemsText = v.findViewById(R.id.itemsSummaryText);
                priceText = v.findViewById(R.id.totalPriceText);
                statusBadge = v.findViewById(R.id.statusBadge);
                trackIndicator = v.findViewById(R.id.trackTextIndicator);
            }
        }
    }
}
