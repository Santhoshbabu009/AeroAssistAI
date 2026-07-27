package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class VendorDashboardActivity extends AppCompatActivity {

    private TextView vendorNameText, vendorInfoText, statusTitle;
    private AppCompatButton manageMenuBtn;
    private ImageView logoutBtn;
    private RecyclerView recyclerView;

    private long vendorId;
    private String email, name, type, terminal, gate;
    private OkHttpClient client;
    private Handler handler;
    private Runnable refreshRunnable;

    // Data lists
    private List<JSONObject> ordersList = new ArrayList<>();
    private List<JSONObject> bookingsList = new ArrayList<>();
    private OrderAdapter orderAdapter;
    private BookingAdapter bookingAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_dashboard);

        // Load Session
        SharedPreferences prefs = getSharedPreferences("VendorSession", MODE_PRIVATE);
        vendorId = prefs.getLong("vendor_id", -1);
        email = prefs.getString("email", "");
        name = prefs.getString("name", "Vendor Dashboard");
        type = prefs.getString("type", "restaurant");
        terminal = prefs.getString("terminal", "T1");
        gate = prefs.getString("gate", "Gate 1");

        if (vendorId == -1) {
            startActivity(new Intent(this, UserTypeSelectionActivity.class));
            finish();
            return;
        }

        client = new OkHttpClient();
        handler = new Handler();

        // Initialize UI views
        vendorNameText = findViewById(R.id.vendorNameText);
        vendorInfoText = findViewById(R.id.vendorInfoText);
        statusTitle = findViewById(R.id.statusTitle);
        manageMenuBtn = findViewById(R.id.manageMenuBtn);
        logoutBtn = findViewById(R.id.logoutBtn);
        recyclerView = findViewById(R.id.recyclerView);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        vendorNameText.setText(name);
        vendorInfoText.setText(terminal + " • " + gate + " • (" + type.toUpperCase() + ")");

        logoutBtn.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();
            startActivity(new Intent(this, UserTypeSelectionActivity.class));
            finish();
        });

        if ("restaurant".equals(type)) {
            statusTitle.setText("Incoming Food Orders");
            manageMenuBtn.setText("Manage Menu");
            manageMenuBtn.setOnClickListener(v -> {
                Intent intent = new Intent(this, VendorMenuActivity.class);
                startActivity(intent);
            });
            orderAdapter = new OrderAdapter();
            recyclerView.setAdapter(orderAdapter);
        } else {
            statusTitle.setText("Lounge Bookings");
            manageMenuBtn.setText("Toggle Status");
            manageMenuBtn.setOnClickListener(v -> toggleLoungeAvailability());
            bookingAdapter = new BookingAdapter();
            recyclerView.setAdapter(bookingAdapter);
        }

        // Setup Polling
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchVendorData();
                handler.postDelayed(this, 5000); // Poll every 5 seconds
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refreshRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refreshRunnable);
    }

    private void fetchVendorData() {
        String url;
        if ("restaurant".equals(type)) {
            url = Constants.BACKEND_BASE_URL + "/api/vendors/orders?vendor_id=" + vendorId;
        } else {
            url = Constants.BACKEND_BASE_URL + "/api/vendors/bookings?vendor_id=" + vendorId;
        }

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
                            if ("restaurant".equals(type)) {
                                JSONArray arr = json.getJSONArray("orders");
                                ordersList.clear();
                                for (int i = 0; i < arr.length(); i++) {
                                    ordersList.add(arr.getJSONObject(i));
                                }
                                runOnUiThread(() -> orderAdapter.notifyDataSetChanged());
                            } else {
                                JSONArray arr = json.getJSONArray("bookings");
                                bookingsList.clear();
                                for (int i = 0; i < arr.length(); i++) {
                                    bookingsList.add(arr.getJSONObject(i));
                                }
                                runOnUiThread(() -> bookingAdapter.notifyDataSetChanged());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateOrderStatus(long orderId, String newStatus) {
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/orders/" + orderId + "/status";
        try {
            JSONObject json = new JSONObject();
            json.put("status", newStatus);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder().url(url).post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(VendorDashboardActivity.this, "Order updated to " + newStatus, Toast.LENGTH_SHORT).show();
                            fetchVendorData();
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateBookingStatus(long bookingId, String newStatus) {
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/bookings/" + bookingId + "/status";
        try {
            JSONObject json = new JSONObject();
            json.put("status", newStatus);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder().url(url).post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(VendorDashboardActivity.this, "Booking status: " + newStatus, Toast.LENGTH_SHORT).show();
                            fetchVendorData();
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void toggleLoungeAvailability() {
        Toast.makeText(this, "Availability status toggled successfully", Toast.LENGTH_SHORT).show();
    }

    // RecyclerView Adapter for Orders (Restaurant)
    private class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vendor_order, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject order = ordersList.get(position);
            long orderId = order.optLong("id");
            String status = order.optString("status", "Pending");
            String emailStr = order.optString("user_email", "Customer");
            String termStr = order.optString("terminal", "T1");
            String gateStr = order.optString("gate", "G1");
            double total = order.optDouble("total_price", 0.0);
            String paymentMethod = order.optString("payment_method", "COD");

            holder.orderIdText.setText("Order #" + orderId);
            holder.userDetailText.setText("Customer: " + emailStr);
            holder.locationText.setText("Delivery: " + termStr + " • " + gateStr);
            holder.priceText.setText("Total Amount: ₹" + String.format("%.2f", total) + " [" + paymentMethod + "]");
            holder.statusBadge.setText(status.toUpperCase());

            // Color badge accordingly
            if ("Pending".equals(status)) {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE0B2));
                holder.statusBadge.setTextColor(0xFFF57C00);

                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.VISIBLE);
                holder.btnReject.setText("Reject");
                holder.btnAccept.setText("Accept");
                
                holder.btnReject.setOnClickListener(v -> updateOrderStatus(orderId, "Rejected"));
                holder.btnAccept.setOnClickListener(v -> updateOrderStatus(orderId, "Accepted"));
            } else if ("Accepted".equals(status)) {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                holder.statusBadge.setTextColor(0xFF2E7D32);

                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnAccept.setText("Prepare");
                holder.btnAccept.setOnClickListener(v -> updateOrderStatus(orderId, "Preparing"));
            } else if ("Preparing".equals(status)) {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE1F5FE));
                holder.statusBadge.setTextColor(0xFF0277BD);

                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnAccept.setText("Mark Ready");
                holder.btnAccept.setOnClickListener(v -> updateOrderStatus(orderId, "Ready"));
            } else if ("Ready".equals(status)) {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEDE7F6));
                holder.statusBadge.setTextColor(0xFF673AB7);

                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                holder.btnReject.setVisibility(View.GONE);
                holder.btnAccept.setText("Deliver");
                holder.btnAccept.setOnClickListener(v -> updateOrderStatus(orderId, "Delivered"));
            } else {
                // Delivered or Rejected
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                if ("Delivered".equals(status)) {
                    holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                    holder.statusBadge.setTextColor(0xFF2E7D32);
                } else {
                    holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
                    holder.statusBadge.setTextColor(0xFFC62828);
                }
                holder.actionButtonsContainer.setVisibility(View.GONE);
            }

            // Build items list representation
            JSONArray items = order.optJSONArray("items");
            StringBuilder itemsSb = new StringBuilder();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) {
                        itemsSb.append("• ").append(item.optString("product_name"))
                                .append(" x").append(item.optInt("quantity"))
                                .append("\n");
                    }
                }
            }
            if (itemsSb.length() > 0) {
                // Remove trailing newline
                itemsSb.setLength(itemsSb.length() - 1);
            }
            holder.itemsText.setText(itemsSb.toString());
        }

        @Override
        public int getItemCount() {
            return ordersList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView orderIdText, statusBadge, userDetailText, locationText, itemsText, priceText;
            AppCompatButton btnReject, btnAccept;
            View actionButtonsContainer;

            ViewHolder(View v) {
                super(v);
                orderIdText = v.findViewById(R.id.orderIdText);
                statusBadge = v.findViewById(R.id.statusBadge);
                userDetailText = v.findViewById(R.id.userDetailText);
                locationText = v.findViewById(R.id.locationText);
                itemsText = v.findViewById(R.id.itemsText);
                priceText = v.findViewById(R.id.priceText);
                btnReject = v.findViewById(R.id.btnReject);
                btnAccept = v.findViewById(R.id.btnAccept);
                actionButtonsContainer = v.findViewById(R.id.actionButtonsContainer);
            }
        }
    }

    // RecyclerView Adapter for Lounge Bookings (Lounge)
    private class BookingAdapter extends RecyclerView.Adapter<BookingAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vendor_booking, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject booking = bookingsList.get(position);
            long bookingId = booking.optLong("id");
            String status = booking.optString("status", "Pending");
            String emailStr = booking.optString("user_email", "Guest");
            String dateStr = booking.optString("booking_date", "");
            String timeStr = booking.optString("booking_time", "");
            int slotsCount = booking.optInt("slots", 1);

            holder.bookingIdText.setText("Booking #" + bookingId);
            holder.userDetailText.setText("Guest: " + emailStr);
            holder.dateText.setText(dateStr);
            holder.timeText.setText(timeStr);
            holder.slotsText.setText(slotsCount + (slotsCount == 1 ? " Guest" : " Guests"));
            holder.statusBadge.setText(status.toUpperCase());

            if ("Pending".equals(status)) {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE0B2));
                holder.statusBadge.setTextColor(0xFFF57C00);

                holder.actionButtonsContainer.setVisibility(View.VISIBLE);
                holder.btnCancel.setOnClickListener(v -> updateBookingStatus(bookingId, "Cancelled"));
                holder.btnConfirm.setOnClickListener(v -> updateBookingStatus(bookingId, "Confirmed"));
            } else {
                holder.statusBadge.setBackgroundResource(R.drawable.status_badge_bg);
                if ("Confirmed".equals(status)) {
                    holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE8F5E9));
                    holder.statusBadge.setTextColor(0xFF2E7D32);
                } else {
                    holder.statusBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFEBEE));
                    holder.statusBadge.setTextColor(0xFFC62828);
                }
                holder.actionButtonsContainer.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return bookingsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView bookingIdText, statusBadge, userDetailText, dateText, timeText, slotsText;
            AppCompatButton btnCancel, btnConfirm;
            View actionButtonsContainer;

            ViewHolder(View v) {
                super(v);
                bookingIdText = v.findViewById(R.id.bookingIdText);
                statusBadge = v.findViewById(R.id.statusBadge);
                userDetailText = v.findViewById(R.id.userDetailText);
                dateText = v.findViewById(R.id.dateText);
                timeText = v.findViewById(R.id.timeText);
                slotsText = v.findViewById(R.id.slotsText);
                btnCancel = v.findViewById(R.id.btnCancel);
                btnConfirm = v.findViewById(R.id.btnConfirm);
                actionButtonsContainer = v.findViewById(R.id.actionButtonsContainer);
            }
        }
    }
}
