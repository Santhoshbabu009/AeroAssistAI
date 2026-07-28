package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.text.InputType;
import android.graphics.Typeface;

import androidx.appcompat.app.AppCompatActivity;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class SignUpActivity extends BaseActivity {

    private EditText nameEditText, emailEditText, passwordEditText, mobileEditText, confirmPasswordEditText;
    private Button signUpButton;
    private TextView loginText;
    private ImageView passwordToggle, confirmPasswordToggle;
    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        mobileEditText = findViewById(R.id.mobileEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        signUpButton = findViewById(R.id.signUpButton);
        loginText = findViewById(R.id.loginText);
        passwordToggle = findViewById(R.id.passwordToggle);
        confirmPasswordToggle = findViewById(R.id.confirmPasswordToggle);

        passwordToggle.setOnClickListener(v -> {
            if (isPasswordVisible) {
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                passwordToggle.setImageResource(R.drawable.ic_visibility_off);
            } else {
                passwordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                passwordToggle.setImageResource(R.drawable.ic_visibility);
            }
            passwordEditText.setSelection(passwordEditText.length());
            passwordEditText.setTypeface(Typeface.DEFAULT);
            isPasswordVisible = !isPasswordVisible;
        });

        confirmPasswordToggle.setOnClickListener(v -> {
            if (isConfirmPasswordVisible) {
                confirmPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                confirmPasswordToggle.setImageResource(R.drawable.ic_visibility_off);
            } else {
                confirmPasswordEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                confirmPasswordToggle.setImageResource(R.drawable.ic_visibility);
            }
            confirmPasswordEditText.setSelection(confirmPasswordEditText.length());
            confirmPasswordEditText.setTypeface(Typeface.DEFAULT);
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
        });

        signUpButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString();
            String email = emailEditText.getText().toString();
            String password = passwordEditText.getText().toString();
            String mobile = mobileEditText.getText().toString();
            String confirmPassword = confirmPasswordEditText.getText().toString();

            if (name.trim().isEmpty() || email.trim().isEmpty() || password.isEmpty() || mobile.trim().isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(SignUpActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            } else if (!password.equals(confirmPassword)) {
                Toast.makeText(SignUpActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SignUpActivity.this, "Contacting secure server...", Toast.LENGTH_SHORT).show();
                registerUser(name, email, password, mobile);
            }
        });

        loginText.setOnClickListener(v -> {
            finish(); // Go back to original AuthActivity
        });
    }

    private void registerUser(String name, String email, String password, String mobile) {
        OkHttpClient client = new OkHttpClient();
        // Uses explicit Physical Device LAN IP bridge
        String url = Constants.REGISTER_ENDPOINT;

        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("email", email);
            json.put("password", password);
            json.put("mobile", mobile);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(SignUpActivity.this, "Server Connection Defaulted/Failed", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        String status = resJson.optString("status");
                        if ("success".equals(status)) {
                            runOnUiThread(() -> {
                                Toast.makeText(SignUpActivity.this, "SMTP OTP Sent! Check Email.", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(SignUpActivity.this, OtpActivity.class);
                                intent.putExtra("email", email);
                                intent.putExtra("name", name);
                                intent.putExtra("mobile", mobile);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String msg = resJson.optString("message", "Registration Firewall Blocked");
                            runOnUiThread(() -> Toast.makeText(SignUpActivity.this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(SignUpActivity.this, "Malformed Server Response", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
