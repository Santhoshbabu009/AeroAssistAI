package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class UserTypeSelectionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_type_selection);

        CardView employeeCard = findViewById(R.id.cardEmployee);
        CardView visitorCard = findViewById(R.id.cardVisitor);
        CardView vendorCard = findViewById(R.id.cardVendor);

        employeeCard.setOnClickListener(v -> navigateToAuth("Employee"));
        visitorCard.setOnClickListener(v -> navigateToAuth("Visitor"));
        vendorCard.setOnClickListener(v -> {
            Intent intent = new Intent(this, VendorLoginActivity.class);
            startActivity(intent);
        });
    }

    private void navigateToAuth(String type) {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.putExtra("user_type", type);
        startActivity(intent);
    }
}
