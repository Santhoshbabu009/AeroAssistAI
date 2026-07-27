package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;


public class AuthActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private ImageButton googleSignInBtn;
    private TextView signUpText, forgotPasswordText;
    private ImageView passwordToggle;
    private boolean isPasswordVisible = false;

    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;
    private String userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        userType = getIntent().getStringExtra("user_type");
        if (userType == null) userType = "Visitor"; // Default fallback

        // Initialize views
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        googleSignInBtn = findViewById(R.id.googleSignInBtn);
        signUpText = findViewById(R.id.signUpText);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        passwordToggle = findViewById(R.id.passwordToggle);

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

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        loginButton.setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(AuthActivity.this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            } else {
                loginUser(email, password);
            }
        });

        googleSignInBtn.setOnClickListener(v -> signIn());

        signUpText.setOnClickListener(v -> {
            // Intent to Sign Up
            startActivity(new Intent(AuthActivity.this, SignUpActivity.class));
        });

        forgotPasswordText.setOnClickListener(v -> {
            Toast.makeText(AuthActivity.this, "Forgot Password clicked", Toast.LENGTH_SHORT).show();
        });
    }

    private void signIn() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String googleEmail = account.getEmail();
            String googleName = account.getDisplayName() != null ? account.getDisplayName() : "Google User";

            // Send to backend to trigger OTP verification
            registerGoogleUser(googleEmail, googleName);

        } catch (ApiException e) {
            Toast.makeText(this, "Sign-in failed: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEmailDialog(String platform) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Sign in with " + platform);
        builder.setMessage("Enter your email to receive a verification OTP:");

        final EditText emailInput = new EditText(this);
        emailInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setHint("your@email.com");
        emailInput.setPadding(40, 20, 40, 20);
        builder.setView(emailInput);

        builder.setPositiveButton("Send OTP", (dialog, which) -> {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(AuthActivity.this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            registerGoogleUser(email, platform + " User");
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void registerGoogleUser(String email, String name) {
        OkHttpClient client = new OkHttpClient();
        String url = Constants.GOOGLE_LOGIN_ENDPOINT;

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("name", name);
            json.put("password", "google_oauth_" + email); // Auto-generated secure placeholder
            json.put("mobile", "");

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Verifying Google account...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    String error = e.getMessage() != null ? e.getMessage() : "Unknown Error";
                    runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Backend Error: " + error, Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        String status = resJson.optString("status");
                        boolean isExisting = resJson.optBoolean("existing", false);

                        runOnUiThread(() -> {
                            if ("success".equals(status)) {
                                if (isExisting) {
                                    // Existing user — go straight to MainActivity
                                    String returnedName = resJson.optString("name", name);
                                    String returnedMobile = resJson.optString("mobile", "");
                                    
                                    // Save Session
                                    android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
                                    android.content.SharedPreferences.Editor editor = session.edit();
                                    editor.putString("email", email);
                                    editor.putString("name", returnedName);
                                    editor.putString("mobile", returnedMobile);
                                    editor.putString("user_type", userType);
                                    editor.apply();

                                    Toast.makeText(AuthActivity.this, "Welcome back, " + returnedName + "!", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                                    intent.putExtra("email", email);
                                    intent.putExtra("name", returnedName);
                                    intent.putExtra("mobile", returnedMobile);
                                    intent.putExtra("user_type", userType);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                } else {
                                    // New user — send to OTP verification screen
                                    Toast.makeText(AuthActivity.this, "OTP sent to " + email, Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(AuthActivity.this, OtpActivity.class);
                                    intent.putExtra("email", email);
                                    intent.putExtra("name", name);
                                    intent.putExtra("mobile", "");
                                    intent.putExtra("password", "google_oauth_" + email);
                                    intent.putExtra("user_type", userType);
                                    startActivity(intent);
                                }
                            } else {
                                String errorMsg = resJson.optString("message", "Unknown error");
                                Toast.makeText(AuthActivity.this, "Google login failed: " + errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Server Parse Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loginUser(String email, String password) {
        OkHttpClient client = new OkHttpClient();
        String url = Constants.LOGIN_ENDPOINT;

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

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    String error = e.getMessage() != null ? e.getMessage() : "Unknown Error";
                    runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Backend Error: " + error, Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        String status = resJson.optString("status");
                        if ("success".equals(status)) {
                            String name = resJson.optString("name", "User");
                            String mobile = resJson.optString("mobile", "Not Set");
                            runOnUiThread(() -> {
                                // Save Session
                                android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
                                android.content.SharedPreferences.Editor editor = session.edit();
                                editor.putString("email", email);
                                editor.putString("name", name);
                                editor.putString("mobile", mobile);
                                editor.putString("user_type", userType);
                                editor.apply();

                                Toast.makeText(AuthActivity.this, "Remote Login Successful", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(AuthActivity.this, MainActivity.class);
                                intent.putExtra("email", email);
                                intent.putExtra("name", name);
                                intent.putExtra("mobile", mobile);
                                intent.putExtra("user_type", userType);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String msg = resJson.optString("message", "Invalid Account Database Match");
                            runOnUiThread(() -> Toast.makeText(AuthActivity.this, msg, Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(AuthActivity.this, "Remote Server Error", Toast.LENGTH_SHORT).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}