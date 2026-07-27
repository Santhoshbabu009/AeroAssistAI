package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

public class BoardingPassActivity extends AppCompatActivity {

    private TextView tvPassengerName, tvClass, tvFromCity, tvFromCode, tvToCity, tvToCode, tvFlightNumber, tvGate, tvSeat;
    private ImageView imgQRCode, btnEditTicket;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boarding_pass);

        prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);

        tvPassengerName = findViewById(R.id.tvPassengerName);
        tvClass = findViewById(R.id.tvClass);
        tvFromCity = findViewById(R.id.tvFromCity);
        tvFromCode = findViewById(R.id.tvFromCode);
        tvToCity = findViewById(R.id.tvToCity);
        tvToCode = findViewById(R.id.tvToCode);
        tvFlightNumber = findViewById(R.id.tvFlightNumber);
        tvGate = findViewById(R.id.tvGate);
        tvSeat = findViewById(R.id.tvSeat);
        imgQRCode = findViewById(R.id.imgQRCode);
        btnEditTicket = findViewById(R.id.btnEditTicket);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        btnEditTicket.setOnClickListener(v -> showEditTicketDialog());

        loadTicketDetails();
    }

    private void loadTicketDetails() {
        // Fetch values from SharedPreferences, defaulting to Santhosh Babu and Chennai to Singapore
        String defaultName = prefs.getString("username", "Santhosh Babu");
        
        String passenger = prefs.getString("ticket_passenger", defaultName);
        String cabinClass = prefs.getString("ticket_class", "ECONOMY");
        String fromCity = prefs.getString("ticket_from_city", "CHENNAI");
        String fromCode = prefs.getString("ticket_from_code", "MAA");
        String toCity = prefs.getString("ticket_to_city", "SINGAPORE");
        String toCode = prefs.getString("ticket_to_code", "SIN");
        String flight = prefs.getString("ticket_flight", "AI346");
        String gate = prefs.getString("ticket_gate", "A12");
        String seat = prefs.getString("ticket_seat", "14A");

        tvPassengerName.setText(passenger);
        tvClass.setText(cabinClass);
        tvFromCity.setText(fromCity.toUpperCase());
        tvFromCode.setText(fromCode.toUpperCase());
        tvToCity.setText(toCity.toUpperCase());
        tvToCode.setText(toCode.toUpperCase());
        tvFlightNumber.setText(flight.toUpperCase());
        tvGate.setText(gate.toUpperCase());
        tvSeat.setText(seat.toUpperCase());

        // Dynamic API scannable QR Code generation
        String qrData = "PASSENGER: " + passenger + " | FLIGHT: " + flight + " | SEAT: " + seat + " | CLASS: " + cabinClass + " | FROM: " + fromCode + " | TO: " + toCode;
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=350x350&data=" + Uri.encode(qrData);

        Glide.with(this)
                .load(qrUrl)
                .placeholder(R.drawable.qr_placeholder)
                .into(imgQRCode);
    }

    private void showEditTicketDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        layout.setBackgroundColor(Color.parseColor("#F8FAFC"));
        scrollView.addView(layout);

        TextView title = new TextView(this);
        title.setText("Edit Ticket Information");
        title.setTextSize(18);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divParams.topMargin = dpToPx(12);
        divParams.bottomMargin = dpToPx(16);
        layout.addView(divider, divParams);

        // Input fields setup
        EditText editPassenger = createField(layout, "Passenger Name", tvPassengerName.getText().toString());
        EditText editFlight = createField(layout, "Flight Number", tvFlightNumber.getText().toString());
        EditText editClass = createField(layout, "Cabin Class", tvClass.getText().toString());
        EditText editFromCity = createField(layout, "Departure City", tvFromCity.getText().toString());
        EditText editFromCode = createField(layout, "Departure Airport Code (3 Letters)", tvFromCode.getText().toString());
        EditText editToCity = createField(layout, "Destination City", tvToCity.getText().toString());
        EditText editToCode = createField(layout, "Destination Airport Code (3 Letters)", tvToCode.getText().toString());
        EditText editGate = createField(layout, "Gate", tvGate.getText().toString());
        EditText editSeat = createField(layout, "Seat", tvSeat.getText().toString());

        // Save & Cancel buttons
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dpToPx(24);
        layout.addView(buttonsLayout, buttonsParams);

        AlertDialog dialog = builder.setView(scrollView).create();

        Button btnSave = new Button(this);
        btnSave.setText("Save Pass");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setBackgroundColor(Color.parseColor("#4F46E5")); // Indigo
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        saveParams.rightMargin = dpToPx(12);
        buttonsLayout.addView(btnSave, saveParams);

        Button btnCancel = new Button(this);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(Color.parseColor("#475569"));
        btnCancel.setBackgroundColor(Color.parseColor("#E2E8F0"));
        buttonsLayout.addView(btnCancel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnSave.setOnClickListener(v -> {
            String passName = editPassenger.getText().toString().trim();
            String flNo = editFlight.getText().toString().trim();
            String cabCl = editClass.getText().toString().trim();
            String fCity = editFromCity.getText().toString().trim();
            String fCode = editFromCode.getText().toString().trim();
            String tCity = editToCity.getText().toString().trim();
            String tCode = editToCode.getText().toString().trim();
            String gt = editGate.getText().toString().trim();
            String st = editSeat.getText().toString().trim();

            if (passName.isEmpty() || flNo.isEmpty() || cabCl.isEmpty() || fCity.isEmpty() || fCode.isEmpty() || tCity.isEmpty() || tCode.isEmpty() || gt.isEmpty() || st.isEmpty()) {
                Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save to SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("ticket_passenger", passName);
            editor.putString("ticket_flight", flNo);
            editor.putString("ticket_class", cabCl);
            editor.putString("ticket_from_city", fCity);
            editor.putString("ticket_from_code", fCode);
            editor.putString("ticket_to_city", tCity);
            editor.putString("ticket_to_code", tCode);
            editor.putString("ticket_gate", gt);
            editor.putString("ticket_seat", st);
            editor.apply();

            Toast.makeText(this, "Boarding pass updated successfully!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            loadTicketDetails();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setColor(Color.parseColor("#F8FAFC"));
            windowBg.setCornerRadius(dpToPx(24));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
    }

    private EditText createField(LinearLayout layout, String labelName, String currentValue) {
        TextView label = new TextView(this);
        label.setText(labelName);
        label.setTextSize(12);
        label.setTextColor(Color.parseColor("#64748B"));
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dpToPx(8);
        layout.addView(label, labelParams);

        EditText input = new EditText(this);
        input.setText(currentValue);
        input.setTextSize(14);
        input.setTextColor(Color.parseColor("#0F172A"));
        input.setBackgroundColor(Color.parseColor("#F1F5F9"));
        input.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#F1F5F9"));
        inputBg.setCornerRadius(dpToPx(8));
        input.setBackground(inputBg);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dpToPx(4);
        inputParams.bottomMargin = dpToPx(8);
        layout.addView(input, inputParams);

        return input;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
