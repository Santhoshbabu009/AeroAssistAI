package com.aeroassist.ai;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class GateInfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);
        
        ((TextView)findViewById(R.id.guideTitle)).setText("Gate Information");
        ((TextView)findViewById(R.id.guideBody)).setText(
            "• Gate A1 - A10: Near Security Checkpoint Alpha.\n" +
            "• Gate A11 - A20: East Wing (8 mins walk).\n" +
            "• Gate B1 - B25: International Pier North.\n" +
            "• Gate B26 - B50: International Pier South.\n\n" +
            "Note: Boarding usually starts 45 minutes before departure and gates close 15-20 minutes before takeoff. Always check the display boards for the most accurate information."
        );
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
