package com.aeroassist.ai;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class UnitConverterActivity extends BaseActivity {

    private Spinner spinnerCategory, spinnerFrom, spinnerTo;
    private EditText etInput;
    private TextView tvResult;
    private Button btnConvert;

    private String[] categories = {"Length", "Weight", "Temperature"};
    private String[][] units = {
        {"Kilometers", "Miles", "Meters"},
        {"Kilograms", "Pounds", "Grams"},
        {"Celsius", "Fahrenheit", "Kelvin"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unit_converter);

        spinnerCategory = findViewById(R.id.spinnerCategory);
        spinnerFrom = findViewById(R.id.spinnerUnitFrom);
        spinnerTo = findViewById(R.id.spinnerUnitTo);
        etInput = findViewById(R.id.etInput);
        tvResult = findViewById(R.id.tvUnitResult);
        btnConvert = findViewById(R.id.btnConvertUnits);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(catAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateUnitSpinners(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnConvert.setOnClickListener(v -> performConversion());
    }

    private void updateUnitSpinners(int categoryIndex) {
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units[categoryIndex]);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFrom.setAdapter(unitAdapter);
        spinnerTo.setAdapter(unitAdapter);
    }

    private void performConversion() {
        String input = etInput.getText().toString();
        if (input.isEmpty()) return;

        double val = Double.parseDouble(input);
        int cat = spinnerCategory.getSelectedItemPosition();
        int from = spinnerFrom.getSelectedItemPosition();
        int to = spinnerTo.getSelectedItemPosition();

        double result = val;

        if (cat == 0) { // Length
            // Normalize to Meters
            double meters = (from == 0) ? val * 1000 : (from == 1) ? val * 1609.34 : val;
            // Convert to target
            result = (to == 0) ? meters / 1000 : (to == 1) ? meters / 1609.34 : meters;
        } else if (cat == 1) { // Weight
            // Normalize to Grams
            double grams = (from == 0) ? val * 1000 : (from == 1) ? val * 453.592 : val;
            result = (to == 0) ? grams / 1000 : (to == 1) ? grams / 453.592 : grams;
        } else if (cat == 2) { // Temp
            if (from == 0 && to == 1) result = (val * 9/5) + 32;
            else if (from == 1 && to == 0) result = (val - 32) * 5/9;
            else if (from == 0 && to == 2) result = val + 273.15;
            else if (from == 2 && to == 0) result = val - 273.15;
        }

        tvResult.setText(String.format("%.2f", result));
    }
}
