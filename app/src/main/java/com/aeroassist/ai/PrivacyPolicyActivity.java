package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class PrivacyPolicyActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);
        
        ((TextView)findViewById(R.id.guideTitle)).setText("Privacy Policy");
        ((TextView)findViewById(R.id.guideBody)).setText(
            "Your privacy is important to us. AeroAssistAI collects only the information necessary to provide you with seamless travel assistance.\n\n" +
            "1. Data Collection: We collect location data for navigation and flight numbers for tracking.\n\n" +
            "2. Data Security: All data is encrypted and stored securely on our servers.\n\n" +
            "3. Third-Party Sharing: We do not share your personal data with third parties except for essential services like map providers and airline APIs.\n\n" +
            "4. User Rights: You have the right to request deletion of your account and data at any time.\n\n" +
            "For full details, please visit our website."
        );
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
