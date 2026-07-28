package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SecurityGuideActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);
        
        ((TextView)findViewById(R.id.guideTitle)).setText("Security Guide");
        ((TextView)findViewById(R.id.guideBody)).setText(
            "1. Liquids & Gels: Must be in containers of 100ml or less, packed in a clear 1-liter bag.\n\n" +
            "2. Electronics: Laptops and large tablets must be removed from bags and placed in separate bins.\n\n" +
            "3. Clothing: Remove jackets, belts, and large jewelry. Some airports may require shoe removal.\n\n" +
            "4. Prohibited Items: No sharp objects, flammables, or weapons of any kind.\n\n" +
            "5. Body Scanning: Follow officer instructions during the scan. Stand in the marked position."
        );
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
