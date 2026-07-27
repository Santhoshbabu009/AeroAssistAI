package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.TextView;

public class AccountSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_settings);

        String email = getIntent().getStringExtra("email");
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Load Profile Data
        ImageView profileImg = findViewById(R.id.settingsProfileImage);
        TextView nameTxt = findViewById(R.id.settingsUserName);

        String safeEmail = email != null ? email : "default";
        String savedName = prefs.getString("name_" + safeEmail, "Aero User");
        String savedImage = prefs.getString("image_" + safeEmail, null);

        com.google.android.gms.auth.api.signin.GoogleSignInAccount googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this);
        if (googleAccount != null) {
            nameTxt.setText(googleAccount.getDisplayName());
            android.net.Uri photoUri = googleAccount.getPhotoUrl();
            if (photoUri != null) {
                com.bumptech.glide.Glide.with(this)
                        .load(photoUri)
                        .placeholder(R.drawable.certificate_bg)
                        .into(profileImg);
            } else {
                profileImg.setImageResource(R.drawable.certificate_bg);
            }
        } else {
            nameTxt.setText(savedName);
            if (savedImage != null) {
                byte[] imageBytes = Base64.decode(savedImage, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                profileImg.setImageBitmap(bitmap);
            }
        }

        CardView profile = findViewById(R.id.setProfile);
        CardView language = findViewById(R.id.setLanguage);
        CardView security = findViewById(R.id.setSecurity);
        CardView privacy = findViewById(R.id.setPrivacy);
        Button logout = findViewById(R.id.btnLogout);

        profile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileEditActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });
        language.setOnClickListener(v -> {
            Intent intent = new Intent(this, LanguageSettingsActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });
        security.setOnClickListener(v -> {
            Intent intent = new Intent(this, SecuritySettingsActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });
        privacy.setOnClickListener(v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));

        com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .build();
        com.google.android.gms.auth.api.signin.GoogleSignInClient googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso);

        logout.setOnClickListener(v -> {
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
                session.edit().clear().apply();

                Intent intent = new Intent(this, AuthActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        });
    }
}
