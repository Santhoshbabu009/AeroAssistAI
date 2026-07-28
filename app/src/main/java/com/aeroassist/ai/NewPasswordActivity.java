package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class NewPasswordActivity extends BaseActivity {

    private EditText newPassword, confirmPassword;
    private Button btnSubmitPassword;
    private String email, otp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_password);

        email = getIntent().getStringExtra("email");
        otp = getIntent().getStringExtra("otp");

        newPassword = findViewById(R.id.newPassword);
        confirmPassword = findViewById(R.id.confirmPassword);
        btnSubmitPassword = findViewById(R.id.btnSubmitPassword);

        btnSubmitPassword.setOnClickListener(v -> {
            String pass = newPassword.getText().toString();
            String confirm = confirmPassword.getText().toString();

            if (pass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "Enter both fields", Toast.LENGTH_SHORT).show();
            } else if (!pass.equals(confirm)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            } else {
                updatePassword(pass);
            }
        });
    }

    private void updatePassword(String pass) {
        OkHttpClient client = new OkHttpClient();
        String url = Constants.PASSWORD_RESET_CONFIRM_ENDPOINT;

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("otp", otp);
            json.put("new_password", pass);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(NewPasswordActivity.this, "Server Error", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(NewPasswordActivity.this, "Password Updated Successfully!", Toast.LENGTH_SHORT).show();
                            // Go back to login/auth (or profile)
                            Intent intent = new Intent(NewPasswordActivity.this, AuthActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        runOnUiThread(() -> Toast.makeText(NewPasswordActivity.this, "Verification Expired or Invalid", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
