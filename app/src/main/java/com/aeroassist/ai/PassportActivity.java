package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class PassportActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 200;
    private ImageView passportPreview;
    private TextView passportStatus;
    private Button btnSave;
    private String userEmail;
    private String encodedPassport = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passport);

        SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        userEmail = session.getString("email", "default");

        passportPreview = findViewById(R.id.passportPreview);
        passportStatus = findViewById(R.id.passportStatus);
        btnSave = findViewById(R.id.btnSavePassport);

        loadSavedPassport();

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        findViewById(R.id.btnUploadImage).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        findViewById(R.id.btnDigiLocker).setOnClickListener(v -> {
            Toast.makeText(this, "Connecting to DigiLocker...", Toast.LENGTH_SHORT).show();
            // Simulate DigiLocker Fetch
            new android.os.Handler().postDelayed(() -> {
                Toast.makeText(this, "Passport Imported from DigiLocker!", Toast.LENGTH_SHORT).show();
                passportStatus.setText("Imported from DigiLocker (Verified)");
                passportPreview.setImageResource(android.R.drawable.ic_menu_agenda); // Placeholder for doc
                btnSave.setVisibility(View.VISIBLE);
                encodedPassport = "DIGILOCKER_VERIFIED"; // Special marker
            }, 2000);
        });

        btnSave.setOnClickListener(v -> savePassport());
    }

    private void loadSavedPassport() {
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        String savedDoc = prefs.getString("passport_" + userEmail, null);
        if (savedDoc != null) {
            if (savedDoc.equals("DIGILOCKER_VERIFIED")) {
                passportPreview.setImageResource(android.R.drawable.ic_menu_agenda);
                passportStatus.setText("DigiLocker Verified Passport");
            } else {
                byte[] imageBytes = Base64.decode(savedDoc, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                passportPreview.setImageBitmap(bitmap);
                passportStatus.setText("Passport Image Saved");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE && data != null) {
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                passportPreview.setImageBitmap(bitmap);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] b = baos.toByteArray();
                encodedPassport = Base64.encodeToString(b, Base64.DEFAULT);
                
                passportStatus.setText("Image Ready to Save");
                btnSave.setVisibility(View.VISIBLE);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void savePassport() {
        if (encodedPassport == null) return;

        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        prefs.edit().putString("passport_" + userEmail, encodedPassport).apply();

        Toast.makeText(this, "Passport Saved to Travel Wallet!", Toast.LENGTH_SHORT).show();
        finish();
    }
}
