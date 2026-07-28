package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CurrencyConverterActivity extends BaseActivity {

    private EditText etAmount;
    private Spinner spinnerFrom, spinnerTo;
    private TextView tvResult;
    private Button btnConvert;

    private String[] currencies = {"USD", "EUR", "GBP", "INR", "AED", "SGD", "JPY"};
    private double[] rates = {1.0, 0.92, 0.79, 83.3, 3.67, 1.34, 151.8}; // Simulated rates vs USD

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_converter);

        etAmount = findViewById(R.id.etAmount);
        spinnerFrom = findViewById(R.id.spinnerFrom);
        spinnerTo = findViewById(R.id.spinnerTo);
        tvResult = findViewById(R.id.tvResult);
        btnConvert = findViewById(R.id.btnConvert);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currencies);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrom.setAdapter(adapter);
        spinnerTo.setAdapter(adapter);

        btnConvert.setOnClickListener(v -> convert());
    }

    private void convert() {
        String input = etAmount.getText().toString();
        if (input.isEmpty()) {
            Toast.makeText(this, "Enter amount", Toast.LENGTH_SHORT).show();
            return;
        }

        double amount = Double.parseDouble(input);
        int fromIdx = spinnerFrom.getSelectedItemPosition();
        int toIdx = spinnerTo.getSelectedItemPosition();

        // Convert to USD first, then to Target
        double amountInUsd = amount / rates[fromIdx];
        double result = amountInUsd * rates[toIdx];

        tvResult.setText(String.format("%.2f %s", result, currencies[toIdx]));
    }
}
