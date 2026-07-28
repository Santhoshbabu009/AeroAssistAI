package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

public class PasswordResetOtpActivity extends BaseActivity {

    private EditText otp1, otp2, otp3, otp4;
    private Button verifyButton;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_reset_otp);

        email = getIntent().getStringExtra("email");

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        verifyButton = findViewById(R.id.verifyButton);

        verifyButton.setOnClickListener(v -> {
            String code = otp1.getText().toString() + otp2.getText().toString() +
                          otp3.getText().toString() + otp4.getText().toString();
            
            if (code.length() < 4) {
                Toast.makeText(this, "Enter 4-digit code", Toast.LENGTH_SHORT).show();
            } else {
                // Verify OTP is valid (in this simplified flow, we pass it to the next activity
                // and the final API call verifies it, but let's do a quick validation if needed)
                Intent intent = new Intent(this, NewPasswordActivity.class);
                intent.putExtra("email", email);
                intent.putExtra("otp", code);
                startActivity(intent);
                finish();
            }
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
}
