package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DummyPaymentActivity extends BaseActivity {

    private String flightJson, date, paxName, paxAge, paxGender, paxMobile, seat;
    private int totalFare;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_payment);

        client = new OkHttpClient();

        flightJson = getIntent().getStringExtra("FLIGHT_JSON");
        date = getIntent().getStringExtra("DATE");
        paxName = getIntent().getStringExtra("PAX_NAME");
        paxAge = getIntent().getStringExtra("PAX_AGE");
        paxGender = getIntent().getStringExtra("PAX_GENDER");
        paxMobile = getIntent().getStringExtra("PAX_MOBILE");
        seat = getIntent().getStringExtra("SEAT");
        totalFare = getIntent().getIntExtra("TOTAL_FARE", 0);

        ImageButton backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        TextView paymentAmount = findViewById(R.id.paymentAmount);
        paymentAmount.setText("₹" + totalFare);

        Button payNowBtn = findViewById(R.id.payNowBtn);
        ProgressBar paymentProgress = findViewById(R.id.paymentProgress);
        RadioGroup paymentMethodGroup = findViewById(R.id.paymentMethodGroup);

        payNowBtn.setOnClickListener(v -> {
            payNowBtn.setText("");
            payNowBtn.setEnabled(false);
            paymentProgress.setVisibility(View.VISIBLE);

            // Determine method
            String method = "upi";
            int checkedId = paymentMethodGroup.getCheckedRadioButtonId();
            if (checkedId == R.id.radioCard) method = "card";
            else if (checkedId == R.id.radioNetBanking) method = "netbanking";
            else if (checkedId == R.id.radioWallet) method = "wallet";

            processPayment(method, payNowBtn, paymentProgress);
        });
    }

    private void processPayment(String method, Button payNowBtn, ProgressBar paymentProgress) {
        // Build payload
        try {
            JSONObject payload = new JSONObject();
            
            SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
            String email = session.getString("email", session.getString("user_email", null));
            if (email == null || email.isEmpty()) {
                SharedPreferences userSession = getSharedPreferences("UserSession", MODE_PRIVATE);
                email = userSession.getString("user_email", userSession.getString("email", "demo@aeroassist.ai"));
            }
            payload.put("email", email);
            payload.put("user_email", email);

            JSONObject flightDetails = new JSONObject(flightJson);
            flightDetails.put("date", date);
            flightDetails.put("cabinClass", "Economy");
            payload.put("flight_details", flightDetails);

            JSONArray passengers = new JSONArray();
            JSONObject pax = new JSONObject();
            pax.put("name", paxName);
            pax.put("age", paxAge);
            pax.put("gender", paxGender);
            pax.put("seat", seat);
            passengers.put(pax);
            payload.put("passenger_details", passengers);

            payload.put("payment_method", method);
            payload.put("total_fare", totalFare);

            RequestBody body = RequestBody.create(
                    payload.toString(), 
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(Constants.FLIGHT_BOOK_ENDPOINT)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        paymentProgress.setVisibility(View.GONE);
                        payNowBtn.setText("Pay Now");
                        payNowBtn.setEnabled(true);
                        Toast.makeText(DummyPaymentActivity.this, "Payment Network Error", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final String respStr = response.body() != null ? response.body().string() : "{}";
                    runOnUiThread(() -> {
                        try {
                            JSONObject respJson = new JSONObject(respStr);
                            if (respJson.optString("status").equals("success")) {
                                // Add fake delay for realism
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    paymentProgress.setVisibility(View.GONE);
                                    Toast.makeText(DummyPaymentActivity.this, "Payment Successful!", Toast.LENGTH_SHORT).show();
                                    
                                    String pnr = respJson.optString("pnr", "UNKNOWN");
                                    Intent intent = new Intent(DummyPaymentActivity.this, ETicketActivity.class);
                                    intent.putExtra("PNR", pnr);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }, 1500);
                            } else {
                                paymentProgress.setVisibility(View.GONE);
                                payNowBtn.setText("Pay Now");
                                payNowBtn.setEnabled(true);
                                Toast.makeText(DummyPaymentActivity.this, "Payment Failed", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            paymentProgress.setVisibility(View.GONE);
                            payNowBtn.setText("Pay Now");
                            payNowBtn.setEnabled(true);
                            Toast.makeText(DummyPaymentActivity.this, "Payment Error", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            paymentProgress.setVisibility(View.GONE);
            payNowBtn.setText("Pay Now");
            payNowBtn.setEnabled(true);
            Toast.makeText(this, "Failed to create payment payload", Toast.LENGTH_SHORT).show();
        }
    }
}
