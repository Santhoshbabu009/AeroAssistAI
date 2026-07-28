package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class SecuritySettingsActivity extends BaseActivity {

    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_settings);

        // Retrieve active session email robustly from SharedPreferences or Intent
        SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        email = session.getString("email", null);
        if (email == null) {
            email = getIntent().getStringExtra("email");
        }

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> {
            if (email == null || email.isEmpty() || "default".equals(email)) {
                Toast.makeText(SecuritySettingsActivity.this, "No active user session detected", Toast.LENGTH_SHORT).show();
            } else {
                requestPasswordResetOtp();
            }
        });

        findViewById(R.id.btnActiveSessions).setOnClickListener(v -> {
            Intent intent = new Intent(SecuritySettingsActivity.this, ActiveSessionsActivity.class);
            startActivity(intent);
        });
    }

    private void requestPasswordResetOtp() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        String url = Constants.PASSWORD_RESET_REQUEST_ENDPOINT;
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();
            
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(SecuritySettingsActivity.this, "Network Error: Cannot connect to backend server.", Toast.LENGTH_LONG).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Intent intent = new Intent(SecuritySettingsActivity.this, PasswordResetOtpActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(SecuritySettingsActivity.this, "Cannot reset Google Account password here", Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
