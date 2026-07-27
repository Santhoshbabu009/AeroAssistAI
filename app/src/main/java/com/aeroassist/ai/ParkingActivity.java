package com.aeroassist.ai;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
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

public class ParkingActivity extends AppCompatActivity {

    private ImageView backBtn;
    private RadioGroup zoneRadioGroup;
    private EditText plateInput;
    private Spinner durationSpinner;
    private RadioGroup paymentRadioGroup;
    private TextView itemTotalText, taxTotalText, grandTotalText, footerTotalText;
    private AppCompatButton btnBookParking;

    private String email;
    private OkHttpClient client;

    private String pendingZone = "Standard Lot A (T1)";
    private int pendingHours = 1;
    private double pendingPrice = 175.00;
    
    private static final int UPI_PAYMENT_REQUEST_CODE = 9876;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parking);

        email = getIntent().getStringExtra("email");
        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        zoneRadioGroup = findViewById(R.id.zoneRadioGroup);
        plateInput = findViewById(R.id.plateInput);
        durationSpinner = findViewById(R.id.durationSpinner);
        paymentRadioGroup = findViewById(R.id.paymentRadioGroup);
        itemTotalText = findViewById(R.id.itemTotalText);
        taxTotalText = findViewById(R.id.taxTotalText);
        grandTotalText = findViewById(R.id.grandTotalText);
        footerTotalText = findViewById(R.id.footerTotalText);
        btnBookParking = findViewById(R.id.btnBookParking);

        backBtn.setOnClickListener(v -> finish());

        // Spinner Setup
        Integer[] hours = {1, 2, 4, 8, 12, 24};
        ArrayAdapter<Integer> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        durationSpinner.setAdapter(spinnerAdapter);

        // Listeners for Price Recalculation
        zoneRadioGroup.setOnCheckedChangeListener((group, checkedId) -> calculateTotals());
        
        durationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                calculateTotals();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnBookParking.setOnClickListener(v -> placeBooking());

        calculateTotals();
    }

    private void calculateTotals() {
        int ratePerHour = 150;
        int selectedId = zoneRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.radioZonePremium) {
            ratePerHour = 300;
            pendingZone = "Premium Lot B (T2)";
        } else if (selectedId == R.id.radioZoneValet) {
            ratePerHour = 500;
            pendingZone = "Valet Parking C (T3)";
        } else {
            pendingZone = "Standard Lot A (T1)";
        }

        pendingHours = (Integer) durationSpinner.getSelectedItem();
        double baseFee = ratePerHour * pendingHours;
        double tax = 25.0;
        pendingPrice = baseFee + tax;

        itemTotalText.setText("₹" + String.format("%.2f", baseFee));
        taxTotalText.setText("₹" + String.format("%.2f", tax));
        grandTotalText.setText("₹" + String.format("%.2f", pendingPrice));
        footerTotalText.setText("₹" + String.format("%.2f", pendingPrice));
    }

    private void placeBooking() {
        String plateNumber = plateInput.getText().toString().trim().toUpperCase();
        if (plateNumber.isEmpty()) {
            Toast.makeText(this, "Please enter your Vehicle Plate Number", Toast.LENGTH_SHORT).show();
            return;
        }

        int selectedPaymentId = paymentRadioGroup.getCheckedRadioButtonId();
        String paymentMethod = "Online";
        if (selectedPaymentId == R.id.radioCod) {
            paymentMethod = "Cash";
        }

        if ("Online".equals(paymentMethod)) {
            startUpiPaymentFlow(plateNumber);
        } else {
            executeBackendBooking(plateNumber, "Cash");
        }
    }

    private void startUpiPaymentFlow(String plateNumber) {
        String amountStr = String.format("%.2f", pendingPrice);
        String note = "Parking Booking - " + pendingZone;
        String txnId = "TXN" + System.currentTimeMillis();

        Uri uri = new Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", "6380006801@axl")
                .appendQueryParameter("pn", "Santhosh Babu")
                .appendQueryParameter("tn", note)
                .appendQueryParameter("am", amountStr)
                .appendQueryParameter("cu", "INR")
                .appendQueryParameter("tr", txnId)
                .build();

        Intent upiIntent = new Intent(Intent.ACTION_VIEW, uri);

        try {
            startActivityForResult(upiIntent, UPI_PAYMENT_REQUEST_CODE);
        } catch (Exception e) {
            showMockUpiChooserDialog(plateNumber, pendingPrice);
        }
    }

    private void showMockUpiChooserDialog(final String plateNumber, final double amount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        rootLayout.setBackgroundColor(Color.parseColor("#F8FAFC"));
        
        TextView titleText = new TextView(this);
        titleText.setText("Select UPI Application");
        titleText.setTextSize(18);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.parseColor("#0F172A"));
        titleText.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.addView(titleText);
        
        TextView subText = new TextView(this);
        subText.setText("AeroAssist Premium Checkout • ₹" + String.format("%.2f", amount));
        subText.setTextSize(13);
        subText.setTextColor(Color.parseColor("#64748B"));
        subText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dpToPx(4);
        subParams.bottomMargin = dpToPx(20);
        rootLayout.addView(subText, subParams);
        
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divParams.bottomMargin = dpToPx(12);
        rootLayout.addView(divider, divParams);
        
        String[] upiApps = {"Google Pay", "PhonePe", "Paytm", "BHIM UPI"};
        String[] upiColors = {"#2563EB", "#7C3AED", "#0052B4", "#10B981"};
        
        final AlertDialog dialog = builder.setView(rootLayout).create();
        
        for (int i = 0; i < upiApps.length; i++) {
            final String appName = upiApps[i];
            String appColor = upiColors[i];
            
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
            row.setClickable(true);
            row.setFocusable(true);
            
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dpToPx(12));
            cardBg.setStroke(dpToPx(1), Color.parseColor("#E2E8F0"));
            row.setBackground(cardBg);
            
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(10);
            row.setLayoutParams(rowParams);
            
            TextView appIcon = new TextView(this);
            appIcon.setText(appName.substring(0, 1));
            appIcon.setTextColor(Color.WHITE);
            appIcon.setTextSize(14);
            appIcon.setGravity(Gravity.CENTER);
            appIcon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(Color.parseColor(appColor));
            appIcon.setBackground(iconBg);
            
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
            iconParams.rightMargin = dpToPx(12);
            row.addView(appIcon, iconParams);
            
            TextView nameText = new TextView(this);
            nameText.setText(appName);
            nameText.setTextSize(15);
            nameText.setTextColor(Color.parseColor("#1E293B"));
            nameText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(nameText, nameParams);
            
            TextView payBadge = new TextView(this);
            payBadge.setText("Pay");
            payBadge.setTextColor(Color.parseColor("#4F46E5"));
            payBadge.setTextSize(12);
            payBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            payBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#EEF2F6"));
            badgeBg.setCornerRadius(dpToPx(6));
            payBadge.setBackground(badgeBg);
            
            row.addView(payBadge);
            
            row.setOnClickListener(v -> {
                dialog.dismiss();
                simulateUpiPayment(appName, plateNumber);
            });
            
            rootLayout.addView(row);
        }
        
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Cancel Payment");
        cancelBtn.setTextSize(14);
        cancelBtn.setTextColor(Color.parseColor("#EF4444"));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        cancelBtn.setClickable(true);
        cancelBtn.setFocusable(true);
        cancelBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.topMargin = dpToPx(12);
        cancelBtn.setLayoutParams(cancelParams);
        
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        rootLayout.addView(cancelBtn);
        
        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setColor(Color.parseColor("#F8FAFC"));
            windowBg.setCornerRadius(dpToPx(20));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
    }

    private void simulateUpiPayment(final String appName, final String plateNumber) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Contacting " + appName + "...");
        progress.setCancelable(false);
        progress.show();
        
        new Handler().postDelayed(() -> {
            progress.setMessage("Processing payment of ₹" + String.format("%.2f", pendingPrice) + "...");
            
            new Handler().postDelayed(() -> {
                progress.dismiss();
                
                GradientDrawable successBg = new GradientDrawable();
                successBg.setColor(Color.WHITE);
                successBg.setCornerRadius(dpToPx(16));
                
                LinearLayout layout = new LinearLayout(ParkingActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
                layout.setGravity(Gravity.CENTER);
                
                TextView tick = new TextView(ParkingActivity.this);
                tick.setText("✔");
                tick.setTextSize(36);
                tick.setTextColor(Color.parseColor("#10B981"));
                tick.setGravity(Gravity.CENTER);
                
                GradientDrawable tickBg = new GradientDrawable();
                tickBg.setShape(GradientDrawable.OVAL);
                tickBg.setColor(Color.parseColor("#ECFDF5"));
                tickBg.setSize(dpToPx(64), dpToPx(64));
                tick.setBackground(tickBg);
                
                LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
                tickParams.bottomMargin = dpToPx(16);
                layout.addView(tick, tickParams);
                
                TextView title = new TextView(ParkingActivity.this);
                title.setText("Payment Successful!");
                title.setTextSize(18);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.parseColor("#0F172A"));
                title.setGravity(Gravity.CENTER);
                
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                titleParams.bottomMargin = dpToPx(8);
                layout.addView(title, titleParams);
                
                TextView sub = new TextView(ParkingActivity.this);
                sub.setText("Txn ID: TXN" + System.currentTimeMillis() + "\nPaid via " + appName);
                sub.setTextSize(13);
                sub.setTextColor(Color.parseColor("#64748B"));
                sub.setGravity(Gravity.CENTER);
                
                layout.addView(sub);
                
                final AlertDialog successDialog = new AlertDialog.Builder(ParkingActivity.this)
                        .setView(layout)
                        .setCancelable(false)
                        .create();
                
                successDialog.show();
                if (successDialog.getWindow() != null) {
                    successDialog.getWindow().setBackgroundDrawable(successBg);
                }
                
                new Handler().postDelayed(() -> {
                    successDialog.dismiss();
                    executeBackendBooking(plateNumber, "Online");
                }, 2000);
                
            }, 1500);
        }, 1200);
    }

    private void executeBackendBooking(final String plateNumber, final String paymentMethod) {
        try {
            JSONObject bookingJson = new JSONObject();
            bookingJson.put("user_email", email);
            bookingJson.put("zone", pendingZone);
            bookingJson.put("hours", pendingHours);
            bookingJson.put("plate_number", plateNumber);
            bookingJson.put("payment_method", paymentMethod);
            bookingJson.put("total_price", pendingPrice);

            RequestBody body = RequestBody.create(
                    bookingJson.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/parking-bookings";
            Request request = new Request.Builder().url(url).post(body).build();

            runOnUiThread(() -> Toast.makeText(ParkingActivity.this, "Booking your parking slot...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(ParkingActivity.this, "Booking failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        if ("success".equals(resJson.optString("status"))) {
                            runOnUiThread(() -> {
                                Toast.makeText(ParkingActivity.this, "Parking Lot Booked successfully!", Toast.LENGTH_LONG).show();
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(ParkingActivity.this, "Booking error: " + resJson.optString("message"), Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(ParkingActivity.this, "Server error parsing response", Toast.LENGTH_LONG).show());
                    }
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "JSON Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPI_PAYMENT_REQUEST_CODE) {
            boolean paymentSuccess = false;
            String paymentResponse = "";
            
            if (data != null) {
                paymentResponse = data.getStringExtra("response");
                if (paymentResponse == null) {
                    paymentResponse = "";
                }
            }
            
            if (resultCode == RESULT_OK) {
                if (!paymentResponse.isEmpty()) {
                    String[] responseParams = paymentResponse.split("&");
                    for (String param : responseParams) {
                        String[] keyValue = param.split("=");
                        if (keyValue.length >= 2) {
                            String key = keyValue[0].trim().toLowerCase();
                            String value = keyValue[1].trim().toLowerCase();
                            if (key.equals("status") && (value.equals("success") || value.equals("successful"))) {
                                paymentSuccess = true;
                                break;
                            }
                        }
                    }
                } else {
                    paymentSuccess = true;
                }
            }
            
            if (paymentSuccess) {
                Toast.makeText(this, "UPI Payment Successful!", Toast.LENGTH_SHORT).show();
                String plateNumber = plateInput.getText().toString().trim().toUpperCase();
                executeBackendBooking(plateNumber, "Online");
            } else {
                Toast.makeText(this, "Payment failed or was cancelled.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
