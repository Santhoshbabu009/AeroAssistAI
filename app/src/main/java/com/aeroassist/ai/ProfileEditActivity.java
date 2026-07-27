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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileEditActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 100;
    private ImageView profileImage;
    private EditText editName, editEmail, editPhone;
    private String userEmail;
    private String encodedImage = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);

        userEmail = getIntent().getStringExtra("email");
        if (userEmail == null) userEmail = "default";

        profileImage = findViewById(R.id.editProfileImage);
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editPhone = findViewById(R.id.editPhone);

        loadCurrentData();

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, PICK_IMAGE);
        });

        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfile());
    }

    private void loadCurrentData() {
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        editName.setText(prefs.getString("name_" + userEmail, ""));
        editEmail.setText(userEmail);
        editPhone.setText(prefs.getString("phone_" + userEmail, ""));
        
        String savedImage = prefs.getString("image_" + userEmail, null);
        if (savedImage != null) {
            encodedImage = savedImage;
            byte[] imageBytes = Base64.decode(savedImage, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            profileImage.setImageBitmap(bitmap);
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
                profileImage.setImageBitmap(bitmap);
                
                // Encode to Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] b = baos.toByteArray();
                encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveProfile() {
        String name = editName.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("name_" + userEmail, name);
        editor.putString("phone_" + userEmail, phone);
        if (encodedImage != null) {
            editor.putString("image_" + userEmail, encodedImage);
        }
        editor.apply();

        Toast.makeText(this, "Profile Saved Locally!", Toast.LENGTH_SHORT).show();
        
        // In a real app, you would also call the backend UPDATE_PROFILE_ENDPOINT here
        // syncWithBackend(name, phone, encodedImage);
        
        finish();
    }
}
