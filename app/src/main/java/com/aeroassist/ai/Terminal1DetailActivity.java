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

public class Terminal1DetailActivity extends BaseActivity {

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
        terminalTitle.setText("Terminal 1");
        terminalInfo.setText(
            "Terminal 1 primarily handles domestic operations. It consists of three levels:\n\n" +
            "â€¢ Level 1: Arrivals and Baggage Claim.\n" +
            "â€¢ Level 2: Departures and Security Check.\n" +
            "â€¢ Level 3: Lounges and Food Court.\n\n" +
            "Gates A1 to A20 are located in this terminal. Walking time from security to the farthest gate is approximately 12 minutes."
        );

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnGateInfo).setOnClickListener(v -> startActivity(new Intent(this, GateInfoActivity.class)));

        fetchGuideContent();
    }

    private void fetchGuideContent() {
        String url = Constants.BACKEND_BASE_URL + "/api/guides/terminal_1";
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
