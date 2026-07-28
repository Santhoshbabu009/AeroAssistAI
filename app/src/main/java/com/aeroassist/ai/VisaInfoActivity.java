package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class VisaInfoActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);
        
        ((TextView)findViewById(R.id.guideTitle)).setText("Visa & Immigration");
        ((TextView)findViewById(R.id.guideBody)).setText(
            "• Passport Validity: Ensure your passport is valid for at least 6 months beyond your travel date.\n\n" +
            "• Visa-Free Entry: Check if your nationality qualifies for visa-free entry or visa-on-arrival.\n\n" +
            "• E-Visas: Many countries now require e-visas or ETAs (Electronic Travel Authorizations) before departure.\n\n" +
            "• Proof of Onward Travel: You may be asked to show a return ticket or proof of funds at immigration.\n\n" +
            "• COVID-19 / Health: Check current vaccination or testing requirements for your destination."
        );
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
