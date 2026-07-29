package com.aeroassist.ai;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.net.URLEncoder;
import java.util.Calendar;

public class BookingSearchActivity extends BaseActivity {

    EditText originInput, destInput;
    Button dateBtn, searchBtn;
    
    String selectedDate = ""; // Format: YYYY-MM-DD

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_search);

        originInput = findViewById(R.id.originInput);
        destInput = findViewById(R.id.destInput);
        dateBtn = findViewById(R.id.dateBtn);
        searchBtn = findViewById(R.id.searchBtn);

        dateBtn.setOnClickListener(v -> {
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(BookingSearchActivity.this,
                    (view, year1, monthOfYear, dayOfMonth) -> {
                        // Month is 0-indexed
                        String m = String.format("%02d", (monthOfYear + 1));
                        String d = String.format("%02d", dayOfMonth);
                        selectedDate = year1 + "-" + m + "-" + d;
                        dateBtn.setText(selectedDate);
                    }, year, month, day);
            datePickerDialog.show();
        });

        searchBtn.setOnClickListener(v -> {
            String origin = originInput.getText().toString().trim().toUpperCase();
            String dest = destInput.getText().toString().trim().toUpperCase();

            if (origin.isEmpty() || dest.isEmpty() || selectedDate.isEmpty()) {
                Toast.makeText(BookingSearchActivity.this, "Please fill in all details", Toast.LENGTH_SHORT).show();
                return;
            }

            if (origin.length() != 3 || dest.length() != 3) {
                Toast.makeText(BookingSearchActivity.this, "Please use 3-letter IATA codes (e.g. DEL, BOM)", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Save this search to history
                android.content.SharedPreferences histPrefs = getSharedPreferences("BookingHistory", MODE_PRIVATE);
                java.util.Set<String> existing = histPrefs.getStringSet("searches", new java.util.LinkedHashSet<>());
                java.util.Set<String> updated = new java.util.LinkedHashSet<>(existing);
                updated.add(origin + " â†’ " + dest + " | " + selectedDate);
                histPrefs.edit().putStringSet("searches", updated).apply();

                // Launch native FlightResultsActivity instead of Google Flights
                Intent intent = new Intent(BookingSearchActivity.this, FlightResultsActivity.class);
                intent.putExtra("ORIGIN", origin);
                intent.putExtra("DESTINATION", dest);
                intent.putExtra("DATE", selectedDate);
                startActivity(intent);
                
            } catch (Exception e) {
                Toast.makeText(BookingSearchActivity.this, "Failed to launch booking engine.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
