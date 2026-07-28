package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class TerminalHubActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal_hub);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        CardView t1 = findViewById(R.id.cardT1);
        CardView t2 = findViewById(R.id.cardT2);
        CardView transfers = findViewById(R.id.cardTransfers);

        t1.setOnClickListener(v -> startActivity(new Intent(this, Terminal1DetailActivity.class)));
        t2.setOnClickListener(v -> startActivity(new Intent(this, Terminal2DetailActivity.class)));
        transfers.setOnClickListener(v -> startActivity(new Intent(this, TransferGuideActivity.class)));
    }
}
