package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.*;
import androidx.cardview.widget.CardView;
import android.view.View;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import android.os.Handler;
import android.os.Looper;

import com.ola.mapsdk.camera.MapControlSettings;
import com.ola.mapsdk.interfaces.OlaMapCallback;
import com.ola.mapsdk.model.OlaLatLng;
import com.ola.mapsdk.model.OlaMarkerOptions;
import com.ola.mapsdk.view.OlaMap;
import com.ola.mapsdk.view.OlaMapView;
import com.ola.mapsdk.view.Marker;

public class FlightStatusActivity extends BaseActivity implements OlaMapCallback {

    EditText flightInput;
    Button checkBtn, scanBtn, historyBtn, aiInsightsBtn;
    TextView resultText, aiInsightsText;
    CardView mapCard, aiInsightsCard;
    private NotificationHelper notificationHelper;

    private OlaMap olaMap;
    private Marker planeMarker;
    private OlaLatLng planePos;
    private OlaMapView mapView;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable movementRunnable;
    private double currentSpeedMs = 0;
    private double currentHeading = 0;

    OkHttpClient client = new OkHttpClient();

    // QR Scanner
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher =
            registerForActivityResult(new ScanContract(), result -> {

                if(result.getContents() != null){

                    String scannedFlight = result.getContents();

                    flightInput.setText(scannedFlight);

                    getFlightStatus();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_status);

        flightInput = findViewById(R.id.flightNumber);
        checkBtn = findViewById(R.id.checkBtn);
        scanBtn = findViewById(R.id.scanBtn);
        historyBtn = findViewById(R.id.historyBtn);
        resultText = findViewById(R.id.resultText);
        mapCard = findViewById(R.id.mapCard);

        aiInsightsBtn = findViewById(R.id.aiInsightsBtn);
        aiInsightsText = findViewById(R.id.aiInsightsText);
        aiInsightsCard = findViewById(R.id.aiInsightsCard);

        mapView = findViewById(R.id.flightMap);
        mapView.getMap(Constants.OLA_MAPS_API_KEY, this, new MapControlSettings.Builder().build());

        View backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        checkBtn.setOnClickListener(v -> getFlightStatus());
        scanBtn.setOnClickListener(v -> startScanner());
        historyBtn.setOnClickListener(v -> startActivity(new Intent(this, FlightSearchHistoryActivity.class)));
        aiInsightsBtn.setOnClickListener(v -> getAIInsights());

        populateLiveFidsBoard();

        notificationHelper = new NotificationHelper(this);
        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    public void onMapReady(OlaMap olaMap) {
        this.olaMap = olaMap;
    }

    @Override
    public void onMapError(String error) {
        android.util.Log.e("OlaMaps", "Map Error: " + error);
    }

    private static final int PERMISSION_REQUEST_CAMERA = 103;

    private void startScanner(){
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        } else {
            launchScanner();
        }
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Boarding Pass QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);

        barcodeLauncher.launch(options);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchScanner();
            } else {
                android.widget.Toast.makeText(this, "Camera permission is required to scan QR code.", android.widget.Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getFlightStatus(){

        String flight = flightInput.getText().toString().trim().toUpperCase();

        if(flight.isEmpty()){
            resultText.setText("Please enter a flight number (e.g. AI101)");
            return;
        }

        saveToHistory(flight);

        resultText.setText("ðŸ” Searching for " + flight + "...");
        if (mapCard != null) mapCard.setVisibility(View.GONE);

        String url = "https://api.aviationstack.com/v1/flights?access_key="
                + Constants.AVIATION_STACK_API_KEY + "&flight_iata=" + flight;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> resultText.setText("âŒ Network error. Check your internet connection.\n" + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String responseData = response.body().string();
                    JSONObject obj = new JSONObject(responseData);

                    // Detect API-level error (quota exceeded, invalid key, etc.)
                    if (obj.has("error")) {
                        JSONObject error = obj.getJSONObject("error");
                        String errMsg = error.optString("message", "Unknown API error");
                        String errCode = error.optString("code", "");
                        runOnUiThread(() -> resultText.setText(
                                "âš ï¸ API Error: " + errMsg +
                                "\nCode: " + errCode +
                                "\n\nYour AviationStack free plan may have exceeded its monthly quota."));
                        return;
                    }

                    if (!obj.has("data")) {
                        runOnUiThread(() -> resultText.setText("âš ï¸ Unexpected API response. Try again."));
                        return;
                    }

                    JSONArray data = obj.getJSONArray("data");

                    if(data.length() == 0){
                        runOnUiThread(() -> resultText.setText(
                                "âœˆï¸ Flight " + flight + " not found.\n\n• Check the IATA code (e.g. AI101, EK202)\n• Flight may have landed or not departed yet"));
                        return;
                    }

                    JSONObject flightData = data.getJSONObject(0);
                    String status = flightData.optString("flight_status", "Unknown");

                    JSONObject airline = flightData.optJSONObject("airline");
                    String airlineName = airline != null ? airline.optString("name", "Unknown") : "Unknown";

                    JSONObject departure = flightData.optJSONObject("departure");
                    String departureAirport = departure != null ? departure.optString("airport", "Unknown") : "Unknown";
                    String scheduledDep = departure != null ? departure.optString("scheduled", "") : "";
                    String gate = departure != null ? departure.optString("gate", "N/A") : "N/A";

                    JSONObject arrival = flightData.optJSONObject("arrival");
                    String arrivalAirport = arrival != null ? arrival.optString("airport", "Unknown") : "Unknown";
                    String scheduledArr = arrival != null ? arrival.optString("scheduled", "") : "";

                    String depTime = scheduledDep.length() >= 16 ? scheduledDep.replace("T"," ").substring(0,16) : scheduledDep;
                    String arrTime = scheduledArr.length() >= 16 ? scheduledArr.replace("T"," ").substring(0,16) : scheduledArr;

                    String statusEmoji = "active".equals(status) ? "ðŸŸ¢" : "landed".equals(status) ? "ðŸ”µ" : "ðŸ”´";

                    String result =
                            "âœˆï¸  " + flight + " â€” " + airlineName +
                            "\n\n" + statusEmoji + " Status: " + status.toUpperCase() +
                            "\n\nðŸ›« From: " + departureAirport +
                            "\n       Dep: " + depTime + "  |  Gate: " + gate +
                            "\n\nðŸ›¬ To: " + arrivalAirport +
                            "\n       Arr: " + arrTime;

                    JSONObject live = flightData.optJSONObject("live");
                    final double[] flightCoords = new double[5];
                    final boolean[] hasLive = {false};

                    if (live != null && !live.isNull("latitude")) {
                        try {
                            flightCoords[0] = live.getDouble("latitude");
                            flightCoords[1] = live.getDouble("longitude");
                            flightCoords[2] = live.optDouble("altitude", 0);
                            flightCoords[3] = live.optDouble("speed_horizontal", 0);
                            flightCoords[4] = live.optDouble("direction", 0);
                            hasLive[0] = true;
                        } catch (Exception ignored) {}
                    }

                    String liveNote = hasLive[0]
                            ? "\n\nðŸ“ Position: " + String.format("%.3f", flightCoords[0]) + "Â°, " + String.format("%.3f", flightCoords[1]) + "Â°"
                            + "\nâš¡ Speed: " + (int)flightCoords[3] + " km/h  |  Alt: " + (int)flightCoords[2] + " m"
                            : "\n\nðŸ“¡ No live GPS position available for this flight.";

                    runOnUiThread(() -> {
                        resultText.setText(result + liveNote);
                        aiInsightsBtn.setVisibility(View.VISIBLE);
                        aiInsightsCard.setVisibility(View.GONE);

                        // Save last flight for home screen dashboard
                        saveLastFlight(flightInput.getText().toString(), result);

                        if (hasLive[0] && olaMap != null) {
                            if (mapCard != null) mapCard.setVisibility(View.VISIBLE);
                            planePos = new OlaLatLng(flightCoords[0], flightCoords[1], 0.0);

                            if (movementRunnable != null) handler.removeCallbacks(movementRunnable);
                            currentSpeedMs = (flightCoords[3] * 1000.0) / 3600.0;
                            currentHeading = flightCoords[4];

                            if (planeMarker != null) planeMarker.removeMarker();
                            
                            OlaMarkerOptions.Builder markerBuilder = new OlaMarkerOptions.Builder()
                                    .setPosition(planePos)
                                    .setSnippet("Flight " + flight + "\nAlt: " + (int)flightCoords[2] + "m | " + (int)flightCoords[3] + "km/h")
                                    .setIconIntRes(R.drawable.ic_plane);
                            
                            planeMarker = olaMap.addMarker(markerBuilder.build());

                            olaMap.moveCameraToLatLong(planePos, 6, 1000);

                            movementRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    if (planeMarker != null && planePos != null && currentSpeedMs > 0) {
                                        planePos = computeOffset(planePos, currentSpeedMs, currentHeading);
                                        
                                        String snippet = "Flight " + flight + " | Moving...";
                                        
                                        // Force redraw by re-adding marker
                                        planeMarker.removeMarker();
                                        planeMarker = olaMap.addMarker(new OlaMarkerOptions.Builder()
                                                .setPosition(planePos)
                                                .setSnippet(snippet)
                                                .setIconIntRes(R.drawable.ic_plane)
                                                .build());
                                    }
                                    handler.postDelayed(this, 1000);
                                }
                            };
                            handler.postDelayed(movementRunnable, 1000);

                            // Simulate "Smart" Notifications
                            simulateSmartNotifications(flight);
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> resultText.setText("âŒ Parsing error: " + e.getMessage()));
                }
            }
        });
    }

    private void saveLastFlight(String code, String status) {
        SharedPreferences dashPrefs = getSharedPreferences("DashboardData", MODE_PRIVATE);
        dashPrefs.edit()
                .putString("last_flight_code", code)
                .putString("last_flight_status", status)
                .apply();
    }

    private void getAIInsights() {
        String currentStatus = resultText.getText().toString();
        if (currentStatus.isEmpty()) return;

        aiInsightsCard.setVisibility(View.VISIBLE);
        aiInsightsText.setText("ðŸ¤– AeroAssist AI is analyzing flight details...");
        aiInsightsBtn.setEnabled(false);

        JSONObject json = new JSONObject();
        try {
            json.put("message", "Provide a brief, helpful insight about this flight status: " + currentStatus);
            json.put("lang", "en");
        } catch (Exception e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(Constants.CHAT_ENDPOINT)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(() -> {
                    aiInsightsText.setText("âŒ Failed to reach AI assistant. Please try again.");
                    aiInsightsBtn.setEnabled(true);
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws java.io.IOException {
                try {
                    String responseBody = response.body().string();
                    JSONObject resJson = new JSONObject(responseBody);
                    String reply = resJson.optString("reply", "No insights available.");

                    runOnUiThread(() -> {
                        aiInsightsText.setText(reply);
                        aiInsightsBtn.setEnabled(true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        aiInsightsText.setText("âŒ Error parsing AI response.");
                        aiInsightsBtn.setEnabled(true);
                    });
                }
            }
        });
    }

    private OlaLatLng computeOffset(OlaLatLng from, double distance, double heading) {
        distance /= 6371009.0;
        heading = Math.toRadians(heading);
        double fromLat = Math.toRadians(from.getLatitude());
        double fromLng = Math.toRadians(from.getLongitude());
        double cosDistance = Math.cos(distance);
        double sinDistance = Math.sin(distance);
        double sinFromLat = Math.sin(fromLat);
        double cosFromLat = Math.cos(fromLat);
        double sinLat = cosDistance * sinFromLat + sinDistance * cosFromLat * Math.cos(heading);
        double dLng = Math.atan2(sinDistance * cosFromLat * Math.sin(heading), cosDistance - sinFromLat * sinLat);
        return new OlaLatLng(Math.toDegrees(Math.asin(sinLat)), Math.toDegrees(fromLng + dLng), 0.0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mapView != null) mapView.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapView != null) mapView.onDestroy();
        if (handler != null && movementRunnable != null) {
            handler.removeCallbacks(movementRunnable);
        }
    }

    private void saveToHistory(String flight) {
        android.content.SharedPreferences prefs = getSharedPreferences("FlightHistory", MODE_PRIVATE);
        String history = prefs.getString("search_history", "");
        
        // Add current search to the beginning if not already there
        if (!history.contains(flight)) {
            if (history.isEmpty()) {
                history = flight;
            } else {
                history = flight + "," + history;
            }
            
            // Limit to 20 entries
            String[] entries = history.split(",");
            if (entries.length > 20) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 20; i++) {
                    sb.append(entries[i]).append(i == 19 ? "" : ",");
                }
                history = sb.toString();
            }
            
            prefs.edit().putString("search_history", history).apply();
        }
    }

    private void simulateSmartNotifications(String flight) {
        // 1. Immediate Boarding Alert
        handler.postDelayed(() -> {
            notificationHelper.sendFlightNotification(
                "ðŸ“¢ Boarding Started: " + flight,
                "Boarding for flight " + flight + " has commenced at Gate A12. Please proceed for boarding."
            );
        }, 5000); // 5 seconds after search

        // 2. Delayed Gate Change Alert (The "Smart" part)
        handler.postDelayed(() -> {
            notificationHelper.sendFlightNotification(
                "âš ï¸ Gate Change Alert: " + flight,
                "Flight " + flight + " has been moved from Gate A12 to Gate B5. Please update your navigation."
            );
        }, 15000); // 15 seconds after search
    }

    private void populateLiveFidsBoard() {
        android.widget.LinearLayout container = findViewById(R.id.liveFidsContainer);
        if (container == null) return;
        container.removeAllViews();

        String[][] flights = {
            {"AI-432", "Delhi (DEL)", "Terminal 1", "Gate 9", "BOARDING", "#10B981", "06:00 AM"},
            {"6E-2051", "Mumbai (BOM)", "Terminal 1", "Gate 14", "DELAYED", "#F59E0B", "08:30 AM"},
            {"SG-103", "Bangalore (BLR)", "Terminal 2", "Gate 25", "ON TIME", "#00E5FF", "10:15 AM"},
            {"IX-541", "Dubai (DXB)", "Terminal 2", "Gate 18", "CHECK-IN", "#6366F1", "12:00 PM"},
            {"UK-812", "Hyderabad (HYD)", "Terminal 1", "Gate 6", "ON TIME", "#00E5FF", "02:45 PM"}
        };

        for (String[] f : flights) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setPadding(0, 16, 0, 16);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            android.widget.LinearLayout leftCol = new android.widget.LinearLayout(this);
            leftCol.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams leftParams = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            leftCol.setLayoutParams(leftParams);

            android.widget.TextView codeTv = new android.widget.TextView(this);
            codeTv.setText(f[0] + "  ➔  " + f[1]);
            codeTv.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            codeTv.setTextSize(14);
            codeTv.setTypeface(null, android.graphics.Typeface.BOLD);

            android.widget.TextView metaTv = new android.widget.TextView(this);
            metaTv.setText(f[2] + "  •  " + f[3] + "  •  " + f[6]);
            metaTv.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
            metaTv.setTextSize(12);

            leftCol.addView(codeTv);
            leftCol.addView(metaTv);

            android.widget.TextView statusBadge = new android.widget.TextView(this);
            statusBadge.setText(" " + f[4] + " ");
            statusBadge.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
            statusBadge.setBackgroundColor(android.graphics.Color.parseColor(f[5]));
            statusBadge.setPadding(14, 6, 14, 6);
            statusBadge.setTextSize(11);
            statusBadge.setTypeface(null, android.graphics.Typeface.BOLD);

            row.addView(leftCol);
            row.addView(statusBadge);

            android.view.View divider = new android.view.View(this);
            divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(android.graphics.Color.parseColor("#334155"));

            final String fNum = f[0];
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                flightInput.setText(fNum);
                getFlightStatus();
            });

            container.addView(row);
            container.addView(divider);
        }
    }
}