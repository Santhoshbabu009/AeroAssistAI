package com.aeroassist.ai;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class OrderTrackingActivity extends BaseActivity {

    private ImageView backBtn;
    private TextView restaurantNameText, orderInfoText, itemsSummaryText;
    
    // Stepper views
    private TextView step1Circle, step2Circle, step3Circle, step4Circle, step5Circle;
    private TextView step1Label, step2Label, step3Label, step4Label, step5Label;
    private View line1, line2, line3, line4;

    private long orderId;
    private String email;
    private OkHttpClient client;
    private Handler handler;
    private Runnable pollRunnable;
    private String lastStatus = "";
    private String vendorName = "Restaurant";
    private NotificationHelper notificationHelper;

    // Color Constants
    private final int COLOR_ACTIVE = 0xFF4CAF50; // Green
    private final int COLOR_INACTIVE = 0xFFCCCCCC; // Grey
    private final int COLOR_TEXT_ACTIVE = 0xFF1E293B; // Slate dark
    private final int COLOR_TEXT_INACTIVE = 0xFF94A3B8; // Slate light

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_tracking);

        orderId = getIntent().getLongExtra("order_id", -1);
        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();
        handler = new Handler();
        notificationHelper = new NotificationHelper(this);

        backBtn = findViewById(R.id.backBtn);
        restaurantNameText = findViewById(R.id.restaurantNameText);
        orderInfoText = findViewById(R.id.orderInfoText);
        itemsSummaryText = findViewById(R.id.itemsSummaryText);

        step1Circle = findViewById(R.id.step1Circle);
        step2Circle = findViewById(R.id.step2Circle);
        step3Circle = findViewById(R.id.step3Circle);
        step4Circle = findViewById(R.id.step4Circle);
        step5Circle = findViewById(R.id.step5Circle);

        step1Label = findViewById(R.id.step1Label);
        step2Label = findViewById(R.id.step2Label);
        step3Label = findViewById(R.id.step3Label);
        step4Label = findViewById(R.id.step4Label);
        step5Label = findViewById(R.id.step5Label);

        line1 = findViewById(R.id.line1);
        line2 = findViewById(R.id.line2);
        line3 = findViewById(R.id.line3);
        line4 = findViewById(R.id.line4);

        backBtn.setOnClickListener(v -> finish());

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                checkOrderStatus();
                handler.postDelayed(this, 3000); // Poll every 3 seconds
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(pollRunnable);
    }

    private void checkOrderStatus() {
        android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        String token = session.getString("auth_token", "");

        String url = Constants.BACKEND_BASE_URL + "/api/orders/" + orderId;
        Request.Builder builder = new Request.Builder().url(url);
        if (token != null && !token.isEmpty()) {
            builder.addHeader("Authorization", "Bearer " + token);
        }
        Request request = builder.build();

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
                            JSONObject order = json.getJSONObject("order");
                            String status = order.optString("status");
                            vendorName = order.optString("vendor_name", "Restaurant");
                            String term = order.optString("terminal");
                            String gate = order.optString("gate");

                            // Check status changes to trigger local push notification
                            if (!lastStatus.isEmpty() && !lastStatus.equals(status)) {
                                runOnUiThread(() -> {
                                    notificationHelper.sendFlightNotification(
                                            "Order Update: " + vendorName,
                                            "Your order status changed from " + lastStatus + " to " + status + "."
                                    );
                                    Toast.makeText(OrderTrackingActivity.this, "Status: " + status, Toast.LENGTH_SHORT).show();
                                });
                            }
                            lastStatus = status;

                            // Build items description
                            JSONArray items = order.optJSONArray("items");
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

                            String paymentMethod = order.optString("payment_method", "COD");

                            runOnUiThread(() -> {
                                restaurantNameText.setText(vendorName);
                                orderInfoText.setText("Order #" + orderId + " • " + term + " " + gate + " • " + paymentMethod);
                                itemsSummaryText.setText(itemsSb.toString());
                                updateStepperUI(status);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateStepperUI(String status) {
        // Reset all to inactive
        setStepInactive(step1Circle, step1Label);
        setStepInactive(step2Circle, step2Label);
        setStepInactive(step3Circle, step3Label);
        setStepInactive(step4Circle, step4Label);
        setStepInactive(step5Circle, step5Label);
        line1.setBackgroundColor(COLOR_INACTIVE);
        line2.setBackgroundColor(COLOR_INACTIVE);
        line3.setBackgroundColor(COLOR_INACTIVE);
        line4.setBackgroundColor(COLOR_INACTIVE);

        // Reset step 2 label text & circle text
        step2Label.setText("Order Accepted");
        step2Circle.setText("2");

        // Turn active sequentially
        setStepActive(step1Circle, step1Label);

        if ("Pending".equals(status)) {
            return;
        }

        if ("Rejected".equals(status) || "Cancelled".equals(status)) {
            step2Circle.setText("X");
            step2Circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFC62828)); // Dark Red
            step2Label.setText("Order " + status);
            step2Label.setTextColor(0xFFC62828); // Dark Red
            line1.setBackgroundColor(0xFFC62828); // Red indicator line
            return;
        }

        setStepActive(step2Circle, step2Label);
        line1.setBackgroundColor(COLOR_ACTIVE);

        if ("Accepted".equals(status)) {
            return;
        }

        setStepActive(step3Circle, step3Label);
        line2.setBackgroundColor(COLOR_ACTIVE);

        if ("Preparing".equals(status)) {
            return;
        }

        setStepActive(step4Circle, step4Label);
        line3.setBackgroundColor(COLOR_ACTIVE);

        if ("Ready".equals(status)) {
            return;
        }

        setStepActive(step5Circle, step5Label);
        line4.setBackgroundColor(COLOR_ACTIVE);
    }

    private void setStepActive(TextView circle, TextView label) {
        circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_ACTIVE));
        label.setTextColor(COLOR_TEXT_ACTIVE);
    }

    private void setStepInactive(TextView circle, TextView label) {
        circle.setBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_INACTIVE));
        label.setTextColor(COLOR_TEXT_INACTIVE);
    }
}
