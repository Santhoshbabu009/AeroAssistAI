package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;

public class VendorLoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private AppCompatButton loginButton;
    private ImageView backBtn;
    private TextView btnAdminRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_login);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        backBtn = findViewById(R.id.backBtn);
        btnAdminRegister = findViewById(R.id.btnAdminRegister);

        backBtn.setOnClickListener(v -> finish());

        btnAdminRegister.setOnClickListener(v -> {
            Intent intent = new Intent(this, VendorRegistrationActivity.class);
            startActivity(intent);
        });

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            } else {
                loginVendor(email, password);
            }
        });
    }

    private void loginVendor(String email, String password) {
        OkHttpClient client = new OkHttpClient();
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/login";

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            runOnUiThread(() -> Toast.makeText(VendorLoginActivity.this, "Authenticating Vendor...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    String error = e.getMessage() != null ? e.getMessage() : "Connection failed";
                    runOnUiThread(() -> Toast.makeText(VendorLoginActivity.this, "Server error: " + error, Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        String status = resJson.optString("status");
                        if ("success".equals(status)) {
                            String token = resJson.optString("token", "");
                            JSONObject vendorJson = resJson.getJSONObject("vendor");
                            long vendorId = vendorJson.getLong("id");
                            String name = vendorJson.getString("name");
                            String type = vendorJson.getString("type");
                            String terminal = vendorJson.getString("terminal");
                            String gate = vendorJson.getString("gate");

                            runOnUiThread(() -> {
                                SharedPreferences prefs = getSharedPreferences("VendorSession", MODE_PRIVATE);
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putLong("vendor_id", vendorId);
                                editor.putString("email", email);
                                editor.putString("name", name);
                                editor.putString("type", type);
                                editor.putString("terminal", terminal);
                                editor.putString("gate", gate);
                                editor.putString("token", token);
                                editor.apply();

                                Toast.makeText(VendorLoginActivity.this, "Vendor Authentication Success", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(VendorLoginActivity.this, VendorDashboardActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String msg = resJson.optString("message", "Invalid Credentials");
                            runOnUiThread(() -> Toast.makeText(VendorLoginActivity.this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(VendorLoginActivity.this, "Server Parse Error", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
