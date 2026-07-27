package com.aeroassist.ai;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CertificateActivity extends AppCompatActivity {

    private String userEmail, userName;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_certificate);

        userEmail = getIntent().getStringExtra("email");
        if (userEmail == null || userEmail.trim().isEmpty()) {
            userEmail = "traveler@aeroassist.ai";
        }
        
        // Always fetch the latest name from SharedPreferences to ensure accuracy
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        userName = prefs.getString("name_" + userEmail, "Valued Traveller");
        if (userName == null || userName.trim().isEmpty()) {
            userName = "Valued Traveller";
        }

        TextView nameView = findViewById(R.id.userName);
        TextView dateView = findViewById(R.id.certificateDate);
        TextView idView = findViewById(R.id.certificateId);
        statusText = findViewById(R.id.statusText);
        Button btnSend = findViewById(R.id.btnSendEmail);
        Button btnDownload = findViewById(R.id.btnDownload);
        ImageView backBtn = findViewById(R.id.backBtn);

        backBtn.setOnClickListener(v -> finish());

        // Display the user name clearly
        nameView.setText(userName.toUpperCase());
        
        // Generate a random ID like AAAI-2024-X451
        String randomPart = String.format("%04d", (int)(Math.random() * 10000));
        idView.setText("AAAI-2024-" + randomPart);

        String currentDate = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date());
        dateView.setText(currentDate.toUpperCase());

        btnSend.setOnClickListener(v -> sendCertificateEmail());
        btnDownload.setOnClickListener(v -> {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "I just earned my AeroAssist AI Aviation Expert Certificate! ✈️🏆");
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share Certificate via"));
        });
    }

    private void sendCertificateEmail() {
        statusText.setText("Sending certificate to " + userEmail + "...");
        statusText.setTextColor(getResources().getColor(android.R.color.darker_gray));

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        JSONObject json = new JSONObject();
        try {
            json.put("email", userEmail);
            json.put("name", userName);
            json.put("type", "Aviation Expert Certificate");
            json.put("date", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(Constants.REWARD_CERTIFICATE_ENDPOINT)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    statusText.setText("Failed to connect to server. Check your connection.");
                    statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    Toast.makeText(CertificateActivity.this, "Network Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String result = response.body().string();
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        JSONObject resJson = new JSONObject(result);
                        if ("success".equals(resJson.optString("status"))) {
                            statusText.setText("Certificate sent successfully! Check your inbox.");
                            statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                        } else {
                            statusText.setText("Error: " + resJson.optString("message", "Unknown error"));
                            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                        }
                    } catch (Exception e) {
                        statusText.setText("Server response error.");
                    }
                });
            }
        });
    }
}
