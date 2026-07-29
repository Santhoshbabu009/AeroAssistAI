package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.EditText;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;
import android.view.View;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.appcompat.widget.SwitchCompat;

public class ProfileActivity extends BaseActivity {

    TextView nameText, emailText, mobileText;
    Button signOutBtn;
    ImageView profileImage;
    Spinner languageSpinner;
    String name, email, mobile;
    EditText editName, editMobile;
    Button saveProfileBtn, changePassBtn;
    ImageView btnEditName, btnEditMobile;
    boolean isEditing = false;
    SwitchCompat voiceReplySwitch;
    GestureDetector navGestureDetector;

    GoogleSignInClient googleSignInClient;
    SharedPreferences prefs;
    String userEmail = "default";

    private static final int PICK_IMAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        
        LocaleHelper.setLocale(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        nameText = findViewById(R.id.profileName);
        emailText = findViewById(R.id.profileEmail);
        mobileText = findViewById(R.id.profileMobile); // NEW
        signOutBtn = findViewById(R.id.signOutBtn);
        profileImage = findViewById(R.id.profileImage);
        languageSpinner = findViewById(R.id.languageSpinner);

        prefs = getSharedPreferences("UserData", MODE_PRIVATE);

        editName = findViewById(R.id.editName);
        editMobile = findViewById(R.id.editMobile);
        saveProfileBtn = findViewById(R.id.saveProfileBtn);
        changePassBtn = findViewById(R.id.changePassBtn);
        btnEditName = findViewById(R.id.btnEditName);
        btnEditMobile = findViewById(R.id.btnEditMobile);

        setupLanguageSpinner();
        setupEditing();
        setupPasswordReset();
        setupVoiceReplyToggle();

        // Receive user data
        name = getIntent().getStringExtra("name");
        email = getIntent().getStringExtra("email");
        mobile = getIntent().getStringExtra("mobile");

        // Load from SharedPreferences first (overrides intent if user has previously saved)
        name = prefs.getString("profile_name_" + email, name);
        mobile = prefs.getString("profile_mobile_" + email, mobile);

        if(name == null || name.isEmpty())
            name = "AeroAssist User";

        if(email == null || email.isEmpty())
            email = "Not available";

        if(mobile == null || mobile.isEmpty())
            mobile = "Not available";

        GoogleSignInAccount googleAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (googleAccount != null) {
            name = googleAccount.getDisplayName();
            if (googleAccount.getEmail() != null) {
                email = googleAccount.getEmail();
            }
        }

        nameText.setText(name);
        emailText.setText(email);
        mobileText.setText(mobile);
        
        userEmail = email;

        // ✅ LOAD SAVED IMAGE
        boolean loadedGoogleImage = false;
        if (googleAccount != null) {
            Uri photoUri = googleAccount.getPhotoUrl();
            if (photoUri != null) {
                com.bumptech.glide.Glide.with(this)
                        .load(photoUri)
                        .placeholder(R.drawable.certificate_bg)
                        .into(profileImage);
                loadedGoogleImage = true;
            }
        }

        if (!loadedGoogleImage) {
            String savedImage = prefs.getString("image_" + userEmail, null);
            if (savedImage != null) {
                byte[] imageBytes = Base64.decode(savedImage, Base64.DEFAULT);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                profileImage.setImageBitmap(bitmap);
            }
        }

        // ✅ CLICK TO SELECT IMAGE
        profileImage.setOnClickListener(v -> openGallery());

        // Google sign-in config
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        signOutBtn.setOnClickListener(v -> signOut());

        // Swipe right → Help Desk
        navGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && diffX > 120 && Math.abs(velocityX) > 150) {
                    // Swipe Right → Help Desk
                    finish();
                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (navGestureDetector != null) {
            navGestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    // ✅ OPEN GALLERY
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }

    // ✅ HANDLE IMAGE RESULT
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                profileImage.setImageBitmap(bitmap);

                // Save image
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] imageBytes = baos.toByteArray();

                String encodedImage = Base64.encodeToString(imageBytes, Base64.DEFAULT);

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("image_" + userEmail, encodedImage);
                editor.apply();

                Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void signOut() {

        googleSignInClient.signOut().addOnCompleteListener(task -> {

            Intent intent = new Intent(ProfileActivity.this, AuthActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);
            finish();
        });
    }

    private void setupEditing() {
        btnEditName.setOnClickListener(v -> toggleEdit());
        btnEditMobile.setOnClickListener(v -> toggleEdit());

        saveProfileBtn.setOnClickListener(v -> saveProfile());
    }

    private void toggleEdit() {
        isEditing = !isEditing;
        if (isEditing) {
            nameText.setVisibility(View.GONE);
            mobileText.setVisibility(View.GONE);
            editName.setVisibility(View.VISIBLE);
            editMobile.setVisibility(View.VISIBLE);
            editName.setText(name);
            editMobile.setText(mobile);
            saveProfileBtn.setVisibility(View.VISIBLE);
        } else {
            nameText.setVisibility(View.VISIBLE);
            mobileText.setVisibility(View.VISIBLE);
            editName.setVisibility(View.GONE);
            editMobile.setVisibility(View.GONE);
            saveProfileBtn.setVisibility(View.GONE);
        }
    }

    private void saveProfile() {
        String newName = editName.getText().toString();
        String newMobile = editMobile.getText().toString();
        
        OkHttpClient client = new OkHttpClient();
        String url = Constants.UPDATE_PROFILE_ENDPOINT;

        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("name", newName);
            json.put("mobile", newMobile);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Network Error: Could not connect to API", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        name = newName;
                        mobile = newMobile;
                        // ✅ Save locally so changes persist across sessions
                        prefs.edit()
                            .putString("profile_name_" + email, newName)
                            .putString("profile_mobile_" + email, newMobile)
                            .apply();
                        runOnUiThread(() -> {
                            nameText.setText(name);
                            mobileText.setText(mobile);
                            toggleEdit();
                            Toast.makeText(ProfileActivity.this, "Profile Updated", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupPasswordReset() {
        changePassBtn.setOnClickListener(v -> {
            // Step 1: Request OTP
            requestPasswordResetOtp();
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
                    runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Network Error: Cannot connect to backend server.", Toast.LENGTH_LONG).show());
                }
                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Intent intent = new Intent(ProfileActivity.this, PasswordResetOtpActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                        });
                    } else {
                        String errorMsg = "Cannot reset Google Account password here";
                        try {
                            String respBody = response.body().string();
                            JSONObject errJson = new JSONObject(respBody);
                            if (errJson.has("message")) {
                                errorMsg = errJson.getString("message");
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        final String finalErrorMsg = errorMsg;
                        runOnUiThread(() -> Toast.makeText(ProfileActivity.this, finalErrorMsg, Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupVoiceReplyToggle() {
        voiceReplySwitch = findViewById(R.id.voiceReplySwitch);
        SharedPreferences settingsPrefs = getSharedPreferences("Settings", MODE_PRIVATE);
        boolean voiceEnabled = settingsPrefs.getBoolean("voice_reply_enabled", true);
        voiceReplySwitch.setChecked(voiceEnabled);

        voiceReplySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsPrefs.edit().putBoolean("voice_reply_enabled", isChecked).apply();
            String msg = isChecked ? "Voice reply turned ON" : "Voice reply turned OFF";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupLanguageSpinner() {
        String[] languages = {"English", "Español", "हिन्दी (Hindi)", "தமிழ் (Tamil)", "తెలుగు (Telugu)", "മലയാളം (Malayalam)"};
        final String[] langCodes = {"en", "es", "hi", "ta", "te", "ml"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, languages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        // Set default selection
        SharedPreferences settingsPrefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String currentLang = settingsPrefs.getString("App_Lang", "en");
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(currentLang)) {
                languageSpinner.setSelection(i);
                break;
            }
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isFirstLoad = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(isFirstLoad) {
                    isFirstLoad = false;
                    return; // Avoid loop
                }
                String code = langCodes[position];
                LocaleHelper.updateLanguage(ProfileActivity.this, code);
                
                // Restart App to apply language universally while PRESERVING session data
                Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                intent.putExtra("name", name);
                intent.putExtra("email", email);
                intent.putExtra("mobile", mobile);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
}