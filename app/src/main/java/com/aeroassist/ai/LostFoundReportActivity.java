package com.aeroassist.ai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class LostFoundReportActivity extends BaseActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 101;
    private static final int PERMISSION_REQUEST_CAMERA = 102;

    private RadioGroup radioReportType;
    private RadioButton radioLost, radioFound;
    private EditText editItemName, editItemDesc, editItemLocation, editItemContact;
    private ImageView imgCapturedItem;
    private Button btnCapturePhoto, btnSubmitReport;
    private OkHttpClient client;
    
    private String base64ImageString = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lost_found_report);

        client = new OkHttpClient();

        radioReportType = findViewById(R.id.radioReportType);
        radioLost = findViewById(R.id.radioLost);
        radioFound = findViewById(R.id.radioFound);
        
        editItemName = findViewById(R.id.editItemName);
        editItemDesc = findViewById(R.id.editItemDesc);
        editItemLocation = findViewById(R.id.editItemLocation);
        editItemContact = findViewById(R.id.editItemContact);
        
        imgCapturedItem = findViewById(R.id.imgCapturedItem);
        btnCapturePhoto = findViewById(R.id.btnCapturePhoto);
        btnSubmitReport = findViewById(R.id.btnSubmitReport);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        btnCapturePhoto.setOnClickListener(v -> checkPermissionAndCapturePhoto());
        btnSubmitReport.setOnClickListener(v -> submitReport());
    }

    private void checkPermissionAndCapturePhoto() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission granted is required to capture photos.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                if (imageBitmap != null) {
                    imgCapturedItem.setImageBitmap(imageBitmap);
                    imgCapturedItem.setImageTintList(null); // Clear transparent white tint
                    
                    // Convert bitmap to Base64
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    imageBitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    base64ImageString = Base64.encodeToString(byteArray, Base64.DEFAULT);
                    
                    Toast.makeText(this, "Photo captured successfully!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void submitReport() {
        String name = editItemName.getText().toString().trim();
        String desc = editItemDesc.getText().toString().trim();
        String location = editItemLocation.getText().toString().trim();
        String contact = editItemContact.getText().toString().trim();

        if (name.isEmpty() || desc.isEmpty() || location.isEmpty() || contact.isEmpty()) {
            Toast.makeText(this, "Please fill in all the details", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = "Lost";
        if (radioFound.isChecked()) {
            type = "Found";
        }

        String icon = getEmojiForName(name);

        android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        String userEmail = session.getString("email", session.getString("user_email", "guest@aeroassist.ai"));
        String reporterName = session.getString("name", "AeroAssist User");

        try {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("description", desc + " • " + location);
            json.put("location", location);
            json.put("contact", contact);
            json.put("type", type);
            json.put("category", getCategoryForName(name));
            json.put("reporter_name", reporterName);
            json.put("user_email", userEmail);
            json.put("icon", icon);
            
            if (base64ImageString != null) {
                json.put("image", base64ImageString);
            }

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.LOST_ITEMS_ENDPOINT;
            Request request = new Request.Builder().url(url).post(body).build();

            btnSubmitReport.setEnabled(false);
            Toast.makeText(this, "Submitting report...", Toast.LENGTH_SHORT).show();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        btnSubmitReport.setEnabled(true);
                        Toast.makeText(LostFoundReportActivity.this, "Submission failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        Toast.makeText(LostFoundReportActivity.this, "Report Submitted Successfully!", Toast.LENGTH_LONG).show();
                        finish();
                    });
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "JSON error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCategoryForName(String name) {
        String n = name.toLowerCase();
        if (n.contains("phone") || n.contains("mobile") || n.contains("laptop") || n.contains("airpod") || n.contains("watch") || n.contains("headphone")) return "Electronics";
        if (n.contains("wallet") || n.contains("passport") || n.contains("card") || n.contains("id") || n.contains("document")) return "Documents & Wallet";
        if (n.contains("bag") || n.contains("luggage") || n.contains("backpack") || n.contains("suitcase")) return "Baggage";
        if (n.contains("key")) return "Keys & Accessories";
        return "Personal Items";
    }

    private String getEmojiForName(String name) {
        String n = name.toLowerCase();
        if (n.contains("phone") || n.contains("mobile") || n.contains("iphone")) return "📱";
        if (n.contains("wallet") || n.contains("purse") || n.contains("money") || n.contains("cash")) return "👛";
        if (n.contains("macbook") || n.contains("laptop") || n.contains("computer") || n.contains("dell") || n.contains("hp")) return "💻";
        if (n.contains("glass") || n.contains("spectacles") || n.contains("sunglass") || n.contains("rayban")) return "👓";
        if (n.contains("bag") || n.contains("luggage") || n.contains("suitcase") || n.contains("backpack")) return "🎒";
        if (n.contains("key")) return "🔑";
        if (n.contains("watch") || n.contains("smartwatch")) return "⌚";
        if (n.contains("headphone") || n.contains("earbud") || n.contains("airpod")) return "🎧";
        if (n.contains("book") || n.contains("novel") || n.contains("diary")) return "📖";
        return "📦";
    }
}
