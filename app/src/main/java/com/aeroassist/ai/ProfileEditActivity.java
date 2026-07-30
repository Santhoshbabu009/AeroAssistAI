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

public class ProfileEditActivity extends BaseActivity {

    private static final int PICK_IMAGE = 100;
    private ImageView profileImage;
    private EditText editName, editEmail, editPhone;
    private String userEmail;
    private String encodedImage = null;

    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int PERMISSION_REQUEST_CAMERA = 105;

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
        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> showPhotoSourceOptions());

        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> saveProfile());
    }

    private void showPhotoSourceOptions() {
        String[] options = {"📷 Take Photo with Camera", "🖼️ Choose from Gallery"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Change Profile Picture")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        checkCameraPermissionAndLaunch();
                    } else {
                        openGallery();
                    }
                })
                .show();
    }

    private void checkCameraPermissionAndLaunch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        } else {
            openCamera();
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to take a photo.", Toast.LENGTH_LONG).show();
            }
        }
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
        if (resultCode != RESULT_OK || data == null) return;

        Bitmap bitmap = null;
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                bitmap = (Bitmap) extras.get("data");
            }
        } else if (requestCode == PICK_IMAGE) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (bitmap != null) {
            profileImage.setImageBitmap(bitmap);
            
            // Encode to Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] b = baos.toByteArray();
            encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
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

        Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
        
        syncWithBackend(name, phone, encodedImage);
        
        finish();
    }

    private void syncWithBackend(String name, String phone, String photoBase64) {
        if (userEmail == null || userEmail.isEmpty() || userEmail.equals("default")) return;
        final String photoData = (photoBase64 != null && !photoBase64.isEmpty()) ? (photoBase64.startsWith("data:") ? photoBase64 : "data:image/jpeg;base64," + photoBase64) : null;
        
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("email", userEmail);
            json.put("name", name);
            json.put("mobile", phone);
            if (photoData != null) json.put("profile_photo", photoData);

            okhttp3.RequestBody body = okhttp3.RequestBody.create(json.toString(), okhttp3.MediaType.get("application/json"));
            okhttp3.Request request = new okhttp3.Request.Builder().url(Constants.UPDATE_PROFILE_ENDPOINT).post(body).build();
            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {}

                @Override
                public void onResponse(okhttp3.Call call, okhttp3.Response response) throws java.io.IOException {
                    if (response.body() != null) response.body().close();
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
