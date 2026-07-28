package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;
import java.io.IOException;

public class Terminal2DetailActivity extends BaseActivity {

    private OkHttpClient client;
    private TextView terminalTitle, terminalInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal_detail);

        client = new OkHttpClient();

        terminalTitle = findViewById(R.id.terminalTitle);
        terminalInfo = findViewById(R.id.terminalInfo);

        // Optimistic default content
        terminalTitle.setText("Terminal 2");
        terminalInfo.setText(
            "Terminal 2 is the main hub for international flights. It features state-of-the-art architecture and premium services.\n\n" +
            "• Level 1: Ground Transportation & International Arrivals.\n" +
            "• Level 2: Duty-Free Shopping & Boarding Gates.\n" +
            "• Level 3: Premium Lounges & Fine Dining.\n\n" +
            "Gates B1 to B50 are located here. Automated People Movers (APM) connect different zones within the terminal."
        );

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnGateInfo).setOnClickListener(v -> startActivity(new Intent(this, GateInfoActivity.class)));

        fetchGuideContent();
    }

    private void fetchGuideContent() {
        String url = Constants.BACKEND_BASE_URL + "/api/guides/terminal_2";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Keep default content
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String res = response.body().string();
                    try {
                        JSONObject obj = new JSONObject(res);
                        if ("success".equals(obj.optString("status"))) {
                            JSONObject guide = obj.getJSONObject("guide");
                            String title = guide.getString("title");
                            String content = guide.getString("content");

                            runOnUiThread(() -> {
                                terminalTitle.setText(title);
                                terminalInfo.setText(content);
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
