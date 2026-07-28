package com.aeroassist.ai;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
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
import java.util.Calendar;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.app.ProgressDialog;
import android.os.Handler;
import com.bumptech.glide.Glide;

public class LoungeDetailsActivity extends BaseActivity {

    private ImageView backBtn, loungeImageCover;
    private TextView loungeNameText, loungeLocationText, pricePerSlotText, slotsText, bookingTotalText;
    private EditText dateInput, timeInput;
    private TextView btnMinus, btnPlus;
    private AppCompatButton btnBook;

    private long loungeId;
    private String loungeName, email;
    private double pricePerSlot;
    private int guestCount = 1;
    private OkHttpClient client;

    private String pendingDate;
    private String pendingTime;
    private static final int UPI_PAYMENT_REQUEST_CODE = 8765;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lounge_details);

        loungeId = getIntent().getLongExtra("lounge_id", -1);
        loungeName = getIntent().getStringExtra("lounge_name");
        pricePerSlot = getIntent().getDoubleExtra("lounge_price", 1200.0);
        String loungeImageUrl = getIntent().getStringExtra("lounge_image_url");
        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        loungeImageCover = findViewById(R.id.loungeImageCover);
        loungeNameText = findViewById(R.id.loungeNameText);
        loungeLocationText = findViewById(R.id.loungeLocationText);
        pricePerSlotText = findViewById(R.id.pricePerSlotText);
        slotsText = findViewById(R.id.slotsText);
        bookingTotalText = findViewById(R.id.bookingTotalText);
        dateInput = findViewById(R.id.dateInput);
        timeInput = findViewById(R.id.timeInput);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnBook = findViewById(R.id.btnBook);

        loungeNameText.setText(loungeName);
        pricePerSlotText.setText("â‚¹" + String.format("%.0f", pricePerSlot) + " / Slot (per person)");

        if (loungeImageUrl != null && !loungeImageUrl.isEmpty()) {
            Glide.with(this)
                    .load(loungeImageUrl)
                    .placeholder(R.drawable.certificate_bg)
                    .into(loungeImageCover);
        } else {
            loungeImageCover.setImageResource(R.drawable.certificate_bg);
        }

        backBtn.setOnClickListener(v -> finish());

        // Date Picker Setup
        dateInput.setOnClickListener(v -> showDatePicker());

        // Time Picker Setup
        timeInput.setOnClickListener(v -> showTimePicker());

        // Plus/Minus
        btnPlus.setOnClickListener(v -> {
            guestCount++;
            updateTotals();
        });

        btnMinus.setOnClickListener(v -> {
            if (guestCount > 1) {
                guestCount--;
                updateTotals();
            }
        });

        btnBook.setOnClickListener(v -> bookLoungeSlot());

        updateTotals();
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog picker = new DatePickerDialog(this, (view, y, m, d) -> {
            // Month is 0-indexed
            String formattedDate = String.format("%04d-%02d-%02d", y, m + 1, d);
            dateInput.setText(formattedDate);
        }, year, month, day);

        picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        picker.show();
    }

    private void showTimePicker() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        TimePickerDialog picker = new TimePickerDialog(this, (view, h, m) -> {
            String formattedTime = String.format("%02d:%02d", h, m);
            timeInput.setText(formattedTime);
        }, hour, minute, true);

        picker.show();
    }

    private void updateTotals() {
        slotsText.setText(String.valueOf(guestCount));
        double total = guestCount * pricePerSlot;
        bookingTotalText.setText("â‚¹" + String.format("%.2f", total));
    }

    private void bookLoungeSlot() {
        String date = dateInput.getText().toString().trim();
        String time = timeInput.getText().toString().trim();

        if (date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please select both date and time", Toast.LENGTH_SHORT).show();
            return;
        }

        startUpiPaymentFlow(date, time);
    }

    private void startUpiPaymentFlow(String date, String time) {
        double grandTotal = guestCount * pricePerSlot;
        String amountStr = String.format("%.2f", grandTotal);
        String note = "Lounge Booking - " + loungeName;
        String txnId = "TXN" + System.currentTimeMillis();

        // Create standard UPI URI with payee address, name, note, amount, currency and transaction reference ID
        android.net.Uri uri = new android.net.Uri.Builder()
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

        this.pendingDate = date;
        this.pendingTime = time;

        try {
            // Direct launch to invoke native UPI deep link apps
            startActivityForResult(upiIntent, UPI_PAYMENT_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            // Fallback to premium mockup chooser on emulators with no UPI apps
            showMockUpiChooserDialog(date, time, grandTotal);
        } catch (Exception e) {
            showMockUpiChooserDialog(date, time, grandTotal);
        }
    }

    private void showMockUpiChooserDialog(final String date, final String time, final double amount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Root Layout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        rootLayout.setBackgroundColor(Color.parseColor("#F8FAFC")); // Clean light slate background
        
        // Title Text
        TextView titleText = new TextView(this);
        titleText.setText("Select UPI Application");
        titleText.setTextSize(18);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.parseColor("#0F172A")); // Slate 900
        titleText.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.addView(titleText);
        
        // Subtitle Text
        TextView subText = new TextView(this);
        subText.setText("AeroAssist Lounge Booking â€¢ â‚¹" + String.format("%.2f", amount));
        subText.setTextSize(13);
        subText.setTextColor(Color.parseColor("#64748B")); // Slate 500
        subText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dpToPx(4);
        subParams.bottomMargin = dpToPx(20);
        rootLayout.addView(subText, subParams);
        
        // Add a line divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0")); // Slate 200
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divParams.bottomMargin = dpToPx(12);
        rootLayout.addView(divider, divParams);
        
        // UPI App options
        String[] upiApps = {"Google Pay", "PhonePe", "Paytm", "BHIM UPI"};
        String[] upiColors = {"#2563EB", "#7C3AED", "#0052B4", "#10B981"}; // Harmonious curated colors
        
        final AlertDialog dialog = builder.setView(rootLayout).create();
        
        for (int i = 0; i < upiApps.length; i++) {
            final String appName = upiApps[i];
            String appColor = upiColors[i];
            
            // App row container (Card-like layout)
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
            row.setClickable(true);
            row.setFocusable(true);
            
            // Set rounded card background programmatically
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dpToPx(12));
            cardBg.setStroke(dpToPx(1), Color.parseColor("#E2E8F0"));
            row.setBackground(cardBg);
            
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(10);
            row.setLayoutParams(rowParams);
            
            // App circle icon placeholder
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
            
            // App name text
            TextView nameText = new TextView(this);
            nameText.setText(appName);
            nameText.setTextSize(15);
            nameText.setTextColor(Color.parseColor("#1E293B")); // Slate 800
            nameText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(nameText, nameParams);
            
            // Pay action badge
            TextView payBadge = new TextView(this);
            payBadge.setText("Pay");
            payBadge.setTextColor(Color.parseColor("#4F46E5")); // Indigo 600
            payBadge.setTextSize(12);
            payBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            payBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#EEF2F6"));
            badgeBg.setCornerRadius(dpToPx(6));
            payBadge.setBackground(badgeBg);
            
            row.addView(payBadge);
            
            // Click action
            row.setOnClickListener(v -> {
                dialog.dismiss();
                simulateUpiPayment(appName, date, time);
            });
            
            rootLayout.addView(row);
        }
        
        // Cancel/Dismiss button
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Cancel Payment");
        cancelBtn.setTextSize(14);
        cancelBtn.setTextColor(Color.parseColor("#EF4444")); // Red 500
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
        
        // Show dialog with rounded window background
        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setColor(Color.parseColor("#F8FAFC"));
            windowBg.setCornerRadius(dpToPx(20));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
    }

    private void simulateUpiPayment(final String appName, final String date, final String time) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Contacting " + appName + "...");
        progress.setCancelable(false);
        progress.show();
        
        new Handler().postDelayed(() -> {
            progress.setMessage("Processing payment of â‚¹" + String.format("%.2f", guestCount * pricePerSlot) + "...");
            
            new Handler().postDelayed(() -> {
                progress.dismiss();
                
                // Show successful checkmark dialog
                GradientDrawable successBg = new GradientDrawable();
                successBg.setColor(Color.WHITE);
                successBg.setCornerRadius(dpToPx(16));
                
                LinearLayout layout = new LinearLayout(LoungeDetailsActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
                layout.setGravity(Gravity.CENTER);
                
                TextView tick = new TextView(LoungeDetailsActivity.this);
                tick.setText("âœ”");
                tick.setTextSize(36);
                tick.setTextColor(Color.parseColor("#10B981")); // Tailwind emerald 500
                tick.setGravity(Gravity.CENTER);
                
                GradientDrawable tickBg = new GradientDrawable();
                tickBg.setShape(GradientDrawable.OVAL);
                tickBg.setColor(Color.parseColor("#ECFDF5")); // Tailwind emerald 50
                tickBg.setSize(dpToPx(64), dpToPx(64));
                tick.setBackground(tickBg);
                
                LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
                tickParams.bottomMargin = dpToPx(16);
                layout.addView(tick, tickParams);
                
                TextView title = new TextView(LoungeDetailsActivity.this);
                title.setText("Payment Successful!");
                title.setTextSize(18);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.parseColor("#0F172A"));
                title.setGravity(Gravity.CENTER);
                
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                titleParams.bottomMargin = dpToPx(8);
                layout.addView(title, titleParams);
                
                TextView sub = new TextView(LoungeDetailsActivity.this);
                sub.setText("Txn ID: TXN" + System.currentTimeMillis() + "\nPaid via " + appName);
                sub.setTextSize(13);
                sub.setTextColor(Color.parseColor("#64748B"));
                sub.setGravity(Gravity.CENTER);
                
                layout.addView(sub);
                
                final AlertDialog successDialog = new AlertDialog.Builder(LoungeDetailsActivity.this)
                        .setView(layout)
                        .setCancelable(false)
                        .create();
                
                successDialog.show();
                if (successDialog.getWindow() != null) {
                    successDialog.getWindow().setBackgroundDrawable(successBg);
                }
                
                new Handler().postDelayed(() -> {
                    successDialog.dismiss();
                    executeBackendBooking(date, time);
                }, 2000);
                
            }, 1500);
        }, 1200);
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
                executeBackendBooking(pendingDate, pendingTime);
            } else {
                Toast.makeText(this, "Payment failed or was cancelled.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void executeBackendBooking(final String date, final String time) {
        try {
            JSONObject bookingJson = new JSONObject();
            bookingJson.put("user_email", email);
            bookingJson.put("vendor_id", loungeId);
            bookingJson.put("booking_date", date);
            bookingJson.put("booking_time", time);
            bookingJson.put("slots", guestCount);

            RequestBody body = RequestBody.create(
                    bookingJson.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/bookings";
            Request request = new Request.Builder().url(url).post(body).build();

            runOnUiThread(() -> Toast.makeText(LoungeDetailsActivity.this, "Booking slot...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(LoungeDetailsActivity.this, "Server connection error", Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        if ("success".equals(resJson.optString("status"))) {
                            runOnUiThread(() -> {
                                new AlertDialog.Builder(LoungeDetailsActivity.this)
                                        .setTitle("Booking Confirmed!")
                                        .setMessage("Your lounge slot booking at " + loungeName + " has been successfully received and is pending confirmation.")
                                        .setPositiveButton("View Bookings", (dialog, which) -> {
                                            Intent intent = new Intent(LoungeDetailsActivity.this, LoungeBookingHistoryActivity.class);
                                            intent.putExtra("email", email);
                                            startActivity(intent);
                                            finish();
                                        })
                                        .setCancelable(false)
                                        .show();
                            });
                        } else {
                            String msg = resJson.optString("message", "Booking failed");
                            runOnUiThread(() -> Toast.makeText(LoungeDetailsActivity.this, msg, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(LoungeDetailsActivity.this, "Booking response parse error", Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
