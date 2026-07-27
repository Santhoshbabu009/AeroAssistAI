package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;

public class VendorRegistrationActivity extends AppCompatActivity {

    private ImageView backBtn;
    private EditText adminKeyInput, vendorNameInput, vendorEmailInput, vendorPasswordInput, gateInput, deleteEmailInput;
    private Spinner vendorTypeSpinner, terminalSpinner;
    private AppCompatButton registerButton, deleteButton;

    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_registration);

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        adminKeyInput = findViewById(R.id.adminKeyInput);
        vendorNameInput = findViewById(R.id.vendorNameInput);
        vendorEmailInput = findViewById(R.id.vendorEmailInput);
        vendorPasswordInput = findViewById(R.id.vendorPasswordInput);
        gateInput = findViewById(R.id.gateInput);
        vendorTypeSpinner = findViewById(R.id.vendorTypeSpinner);
        terminalSpinner = findViewById(R.id.terminalSpinner);
        registerButton = findViewById(R.id.registerButton);
        deleteEmailInput = findViewById(R.id.deleteEmailInput);
        deleteButton = findViewById(R.id.deleteButton);

        backBtn.setOnClickListener(v -> finish());

        // Setup Vendor Type Spinner
        String[] types = {"restaurant", "lounge"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, types);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        vendorTypeSpinner.setAdapter(typeAdapter);

        // Setup Terminal Spinner
        String[] terminals = {"Terminal 1", "Terminal 2", "Terminal 3"};
        ArrayAdapter<String> terminalAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, terminals);
        terminalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        terminalSpinner.setAdapter(terminalAdapter);

        registerButton.setOnClickListener(v -> registerVendor());
        deleteButton.setOnClickListener(v -> deleteVendor());
    }

    private void registerVendor() {
        String adminKey = adminKeyInput.getText().toString().trim();
        String name = vendorNameInput.getText().toString().trim();
        String email = vendorEmailInput.getText().toString().trim();
        String password = vendorPasswordInput.getText().toString().trim();
        String gate = gateInput.getText().toString().trim();
        String type = vendorTypeSpinner.getSelectedItem().toString();
        String terminal = terminalSpinner.getSelectedItem().toString();

        if (adminKey.isEmpty() || name.isEmpty() || email.isEmpty() || password.isEmpty() || gate.isEmpty()) {
            Toast.makeText(this, "Please fill in all details", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("admin_key", adminKey);
            json.put("name", name);
            json.put("email", email);
            json.put("password", password);
            json.put("type", type);
            json.put("terminal", terminal);
            json.put("gate", gate);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/vendors/register";
            Request request = new Request.Builder().url(url).post(body).build();

            runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Requesting vendor registration...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        if (response.code() == 200 && "success".equals(resJson.optString("status"))) {
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(VendorRegistrationActivity.this)
                                        .setTitle("Success")
                                        .setMessage("Vendor account successfully created! They can now log in using their credentials.")
                                        .setPositiveButton("OK", (dialog, which) -> finish())
                                        .setCancelable(false)
                                        .show();
                            });
                        } else {
                            String msg = resJson.optString("message", "Registration failed");
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(VendorRegistrationActivity.this)
                                        .setTitle("Registration Failed")
                                        .setMessage(msg)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Failed to parse response", Toast.LENGTH_LONG).show());
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteVendor() {
        String adminKey = adminKeyInput.getText().toString().trim();
        String email = deleteEmailInput.getText().toString().trim();

        if (adminKey.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Please fill in both Admin Secret Key and Vendor Email to Remove", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("admin_key", adminKey);
            json.put("email", email);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/vendors/delete";
            Request request = new Request.Builder().url(url).post(body).build();

            runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Requesting vendor deletion...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        if (response.code() == 200 && "success".equals(resJson.optString("status"))) {
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(VendorRegistrationActivity.this)
                                        .setTitle("Success")
                                        .setMessage("Vendor account successfully removed!")
                                        .setPositiveButton("OK", (dialog, which) -> {
                                            deleteEmailInput.setText("");
                                        })
                                        .setCancelable(false)
                                        .show();
                            });
                        } else {
                            String msg = resJson.optString("message", "Deletion failed");
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(VendorRegistrationActivity.this)
                                        .setTitle("Deletion Failed")
                                        .setMessage(msg)
                                        .setPositiveButton("OK", null)
                                        .show();
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(VendorRegistrationActivity.this, "Failed to parse response", Toast.LENGTH_LONG).show());
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
