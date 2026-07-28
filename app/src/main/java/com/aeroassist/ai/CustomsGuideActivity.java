package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class CustomsGuideActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);
        
        ((TextView)findViewById(R.id.guideTitle)).setText("Customs & Duty");
        ((TextView)findViewById(R.id.guideBody)).setText(
            "â€¢ Duty-Free Allowance: Usually includes up to 2 liters of alcohol and 200 cigarettes. Varies by country.\n\n" +
            "â€¢ Currency: Most countries require declaration of cash exceeding $10,000 USD (or equivalent).\n\n" +
            "â€¢ Food & Plants: Fresh produce, seeds, and meat products are strictly regulated and must be declared.\n\n" +
            "â€¢ Prohibited Goods: Counterfeit goods, endangered species products, and narcotics are strictly forbidden.\n\n" +
            "â€¢ Tax Refunds: Keep your receipts for high-value purchases to claim VAT refunds at the airport."
        );
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
