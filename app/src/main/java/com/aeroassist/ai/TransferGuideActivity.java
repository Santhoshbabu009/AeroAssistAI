package com.aeroassist.ai;

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

public class TransferGuideActivity extends BaseActivity {

    private OkHttpClient client;
    private TextView guideTitle, guideBody;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guide_content);

        client = new OkHttpClient();

        guideTitle = findViewById(R.id.guideTitle);
        guideBody = findViewById(R.id.guideBody);

        // Optimistic default content
        guideTitle.setText("Inter-Terminal Transfers");
        guideBody.setText(
            "â€¢ Free Shuttle Bus: Operates every 10 minutes between T1 and T2. Follow signs for 'Terminal Shuttle'.\n\n" +
            "â€¢ Airside Transfer: If you have a connecting flight, use the airside transfer bus to avoid re-clearing immigration.\n\n" +
            "â€¢ Walking Path: A covered walkway connects T1 and T2 (approx. 15 mins walk).\n\n" +
            "â€¢ Buggy Service: Elderly and disabled passengers can request a buggy at the information desks.\n\n" +
            "â€¢ Luggage: If your bags are not checked through, you must collect them before transferring between terminals."
        );

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        fetchGuideContent();
    }

    private void fetchGuideContent() {
        String url = Constants.BACKEND_BASE_URL + "/api/guides/transfer_guide";
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
                                guideTitle.setText(title);
                                guideBody.setText(content);
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
