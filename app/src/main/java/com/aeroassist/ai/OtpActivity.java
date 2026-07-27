package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class OtpActivity extends AppCompatActivity {

    private EditText otp1, otp2, otp3, otp4;
    private Button verifyButton;
    private TextView resendText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        verifyButton = findViewById(R.id.verifyButton);
        resendText = findViewById(R.id.resendText);

        verifyButton.setOnClickListener(v -> {
            String code = otp1.getText().toString() + otp2.getText().toString() + 
                          otp3.getText().toString() + otp4.getText().toString();
            
            if (code.length() < 4) {
                Toast.makeText(this, "Please enter full 4-digit code", Toast.LENGTH_SHORT).show();
            } else {
                String email = getIntent().getStringExtra("email");
                verifyOtp(email, code);
            }
        });

        resendText.setOnClickListener(v -> {
            Toast.makeText(this, "OTP Verification Ping Active", Toast.LENGTH_SHORT).show();
        });

        setupAutoMove();
    }

    private void setupAutoMove() {
        otp1.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) otp2.requestFocus();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        otp2.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) otp3.requestFocus();
                else if (s.length() == 0) otp1.requestFocus();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        otp3.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1) otp4.requestFocus();
                else if (s.length() == 0) otp2.requestFocus();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        otp4.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) otp3.requestFocus();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void verifyOtp(String email, String code) {
        OkHttpClient client = new OkHttpClient();
        String url = Constants.VERIFY_OTP_ENDPOINT;

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("otp", code);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(OtpActivity.this, "Server Verification Offline", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        String status = resJson.optString("status");
                        if ("success".equals(status)) {
                            runOnUiThread(() -> {
                                Toast.makeText(OtpActivity.this, "Server Verification Complete!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(OtpActivity.this, MainActivity.class);
                                intent.putExtra("name", resJson.optString("name", "User"));
                                intent.putExtra("email", email);
                                intent.putExtra("user_type", getIntent().getStringExtra("user_type"));
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String msg = resJson.optString("message", "Invalid Verification Code");
                            runOnUiThread(() -> Toast.makeText(OtpActivity.this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(OtpActivity.this, "Failed OTP Payload parsing", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
