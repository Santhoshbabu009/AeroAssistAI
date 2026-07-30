package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

public class MainActivity extends BaseActivity {

    CardView flightCard, chatCard, navCard, bookCard, historyCard, servicesCard, walletCard, communityCard;
    CardView quickTraceFlight, quickNavGate, weatherCard, lastFlightCard;
    TextView weatherStatus, weatherLocation, lastFlightCode, lastFlightStatus;
    TextView greetingText, tvCurrentAirport;
    LinearLayout navHomeBtn, navHelpBtn, navProfileBtn, navRewardsBtn;
    ImageView profileImage, navProfileImage;
    ViewFlipper airportViewFlipper;
    GestureDetector gestureDetector;     // for image slider
    GestureDetector navGestureDetector;  // for screen navigation

    private FusedLocationProviderClient fusedLocationClient;

    String name, email, userType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LocaleHelper.setLocale(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        flightCard = findViewById(R.id.flightCard);
        chatCard = findViewById(R.id.chatCard);
        navCard = findViewById(R.id.navCard);
        bookCard = findViewById(R.id.bookCard);
        historyCard = findViewById(R.id.historyCard);
        servicesCard = findViewById(R.id.servicesCard);
        walletCard = findViewById(R.id.walletCard);
        communityCard = findViewById(R.id.communityCard);
        navHomeBtn = findViewById(R.id.navHomeBtn);
        navHelpBtn = findViewById(R.id.navHelpBtn);
        navProfileBtn = findViewById(R.id.navProfileBtn);
        navRewardsBtn = findViewById(R.id.navRewardsBtn);
        profileImage = findViewById(R.id.profileImage);
        navProfileImage = findViewById(R.id.navProfileImage);
        airportViewFlipper = findViewById(R.id.airportViewFlipper);
        
        quickTraceFlight = findViewById(R.id.quickTraceFlight);
        quickNavGate = findViewById(R.id.quickNavGate);

        weatherCard = findViewById(R.id.weatherCard);
        weatherStatus = findViewById(R.id.weatherStatus);
        weatherLocation = findViewById(R.id.weatherLocation);
        lastFlightCard = findViewById(R.id.lastFlightCard);
        lastFlightCode = findViewById(R.id.lastFlightCode);
        lastFlightStatus = findViewById(R.id.lastFlightStatus);

        greetingText = findViewById(R.id.greetingText);
        tvCurrentAirport = findViewById(R.id.tvCurrentAirport);

        // Set initial animations for automatic flipping
        airportViewFlipper.setInAnimation(this, R.anim.slide_in_right);
        airportViewFlipper.setOutAnimation(this, R.anim.slide_out_left);
        airportViewFlipper.setFlipInterval(3000);
        airportViewFlipper.startFlipping();

        // Setup swipe gesture for image slider
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 80 && Math.abs(velocityX) > 100) {
                    if (diffX < 0) {
                        // Swipe Left → show NEXT image (comes from Right)
                        airportViewFlipper.setInAnimation(MainActivity.this, R.anim.slide_in_right);
                        airportViewFlipper.setOutAnimation(MainActivity.this, R.anim.slide_out_left);
                        airportViewFlipper.showNext();
                    } else {
                        // Swipe Right → show PREVIOUS image (comes from Left)
                        airportViewFlipper.setInAnimation(MainActivity.this, R.anim.slide_in_left);
                        airportViewFlipper.setOutAnimation(MainActivity.this, R.anim.slide_out_right);
                        airportViewFlipper.showPrevious();
                    }
                    // Reset automatic flipping timer
                    airportViewFlipper.stopFlipping();
                    // Set animations back to default for automatic flow
                    airportViewFlipper.setInAnimation(MainActivity.this, R.anim.slide_in_right);
                    airportViewFlipper.setOutAnimation(MainActivity.this, R.anim.slide_out_left);
                    airportViewFlipper.startFlipping();
                    return true;
                }
                return false;
            }
        });

        airportViewFlipper.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        // Screen-level swipe navigation (swipe left → Help Desk)
        navGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 300;
            private static final int SWIPE_VELOCITY_THRESHOLD = 1500;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                // Only trigger if horizontal swipe is dominant
                if (Math.abs(diffX) > Math.abs(diffY)
                        && Math.abs(diffX) > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        // Swipe Left → Help Desk
                        Intent intent = new Intent(MainActivity.this, HelpDeskActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                        return true;
                    }
                }
                return false;
            }
        });

        // Receive user data
        name = getIntent().getStringExtra("name");
        email = getIntent().getStringExtra("email");
        userType = getIntent().getStringExtra("user_type");
        if (userType == null) userType = "Visitor";

        com.google.android.gms.auth.api.signin.GoogleSignInAccount googleAccount = com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(this);
        if (googleAccount != null) {
            name = googleAccount.getDisplayName();
            if (googleAccount.getEmail() != null) {
                email = googleAccount.getEmail();
            }
        }

        // Personalized greeting
        if (greetingText != null) {
            String firstName = (name != null && !name.isEmpty())
                ? name.split(" ")[0] : "Traveller";
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            String timeGreet = hour < 12 ? "Good Morning" : hour < 17 ? "Good Afternoon" : "Good Evening";
            greetingText.setText(timeGreet + ", " + firstName);
        }

        // Load profile image (Prioritize user's updated custom photo over Google default)
        SharedPreferences prefs = getSharedPreferences("UserData", MODE_PRIVATE);
        String safeEmail = email != null ? email : "default";
        String savedImage = prefs.getString("image_" + safeEmail, null);

        if (savedImage != null) {
            try {
                byte[] imageBytes = Base64.decode(savedImage, Base64.DEFAULT);
                Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                if (bitmap != null) {
                    if (profileImage != null) profileImage.setImageBitmap(bitmap);
                    if (navProfileImage != null) navProfileImage.setImageBitmap(bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (googleAccount != null) {
            android.net.Uri photoUri = googleAccount.getPhotoUrl();
            if (photoUri != null) {
                if (profileImage != null) {
                    com.bumptech.glide.Glide.with(this)
                            .load(photoUri)
                            .placeholder(R.drawable.certificate_bg)
                            .into(profileImage);
                }
                if (navProfileImage != null) {
                    com.bumptech.glide.Glide.with(this)
                            .load(photoUri)
                            .placeholder(R.drawable.certificate_bg)
                            .into(navProfileImage);
                }
            }
        }

        // Flight Status
        flightCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FlightStatusActivity.class)));

        // AI Chatbot
        chatCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            startActivity(intent);
        });

        // Airport Navigation
        navCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NavigationActivity.class)));

        // Ticket Booking Engine
        bookCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BookingSearchActivity.class)));

        // Booking History
        historyCard.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BookingHistoryActivity.class)));

        // Airport Services
        servicesCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServicesActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Travel Wallet
        walletCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WalletActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            startActivity(intent);
        });

        // Community Hub
        communityCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CommunityHubActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Premium Smart Telemetry & Quick Action Carousel Click Listeners
        findViewById(R.id.quickPass).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, IDCardActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        findViewById(R.id.quickNavGate).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NavigationActivity.class)));

        findViewById(R.id.quickBaggage).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FlightStatusActivity.class)));

        findViewById(R.id.quickTraceFlight).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FlightStatusActivity.class)));

        findViewById(R.id.quickDining).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RestaurantListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        findViewById(R.id.quickLounges).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoungeListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        findViewById(R.id.quickShopping).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServicesActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        findViewById(R.id.quickParking).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ServicesActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Futuristic AI Assistant Prompt Suggestion Chips Handlers
        findViewById(R.id.btnMainPromptGate).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            intent.putExtra("prefill", "Where is Gate A24?");
            startActivity(intent);
        });

        findViewById(R.id.btnMainPromptPass).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            intent.putExtra("prefill", "Show boarding pass");
            startActivity(intent);
        });

        findViewById(R.id.btnMainPromptLounge).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            intent.putExtra("prefill", "Nearest lounge?");
            startActivity(intent);
        });

        // Futuristic HUD UI Widgets Click Listeners
        findViewById(R.id.cardAiQuickAccess).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("user_type", userType);
            startActivity(intent);
        });

        findViewById(R.id.recLoungeCard).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoungeListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        findViewById(R.id.recDiningCard).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RestaurantListActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Home Button
        navHomeBtn.setOnClickListener(v -> {
            // Already on MainActivity
        });

        // Profile button click
        navProfileBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AccountSettingsActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Help Desk Button
        navHelpBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HelpDeskActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Rewards Button
        navRewardsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RewardsActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Initialize Location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (tvCurrentAirport != null) {
            tvCurrentAirport.setOnClickListener(v -> showAirportSelectorDialog());
        }

        // Initialize Phase 2 Dashboard features
        SharedPreferences dashPrefs = getSharedPreferences("DashboardData", MODE_PRIVATE);
        String savedAirport = dashPrefs.getString("custom_airport", null);
        if (savedAirport != null) {
            if (tvCurrentAirport != null) tvCurrentAirport.setText(" " + savedAirport);
            if (weatherLocation != null) weatherLocation.setText(savedAirport);
            // Fetch weather coordinates matching saved airport if needed, otherwise show simulated
            showSimulatedWeather(savedAirport);
            loadLastFlight();
        } else {
            fetchWeatherData();
            loadLastFlight();
        }
    }

    private void fetchWeatherData() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            // Default simulated until permission granted
            showSimulatedWeather("Bengaluru Airport Terminal 2");
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                fetchRealWeather(location.getLatitude(), location.getLongitude());
            } else {
                showSimulatedWeather("Location Unavailable");
            }
        });
    }

    private void fetchRealWeather(double lat, double lon) {
        String nearest = getNearestAirport(lat, lon);
        runOnUiThread(() -> {
            if (tvCurrentAirport != null) {
                tvCurrentAirport.setText(" " + nearest);
            }
        });

        OkHttpClient client = new OkHttpClient();
        
        // 1. Fetch City name via Ola Maps Reverse Geocode
        String geoUrl = "https://api.olamaps.io/places/v1/reverse-geocode?latlng=" + lat + "," + lon + "&api_key=" + Constants.OLA_MAPS_API_KEY;
        Request geoRequest = new Request.Builder()
                .url(geoUrl)
                .addHeader("X-Request-Id", UUID.randomUUID().toString())
                .build();

        client.newCall(geoRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> weatherLocation.setText("Current Location"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        String city = "Current Location";
                        if (json.has("results")) {
                            city = json.getJSONArray("results").getJSONObject(0).optString("formatted_address", "Current Location");
                            // Truncate if too long
                            if (city.length() > 25) city = city.substring(0, 22) + "...";
                        }
                        final String finalCity = city;
                        runOnUiThread(() -> weatherLocation.setText(finalCity));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });

        // 2. Fetch Weather via Open-Meteo
        String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true";
        Request weatherRequest = new Request.Builder().url(weatherUrl).build();

        client.newCall(weatherRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> weatherStatus.setText("Weather N/A"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        JSONObject current = json.getJSONObject("current_weather");
                        double temp = current.getDouble("temperature");
                        int code = current.getInt("weathercode");
                        
                        String icon = "☀️"; // default
                        if (code >= 1 && code <= 3) icon = "⛅";
                        else if (code >= 45 && code <= 48) icon = "☁️";
                        else if (code >= 51 && code <= 67) icon = "🌧️";
                        else if (code >= 71) icon = "❄️";

                        final String status = icon + " " + Math.round(temp) + "°C";
                        runOnUiThread(() -> weatherStatus.setText(status));
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void showSimulatedWeather(String location) {
        String[] statuses = {"☀️ 28°C", "⛅ 24°C", "☁️ 22°C", "🌧️ 20°C"};
        int idx = (int) (Math.random() * statuses.length);
        weatherStatus.setText(statuses[idx]);
        weatherLocation.setText(location);
    }

    private String getNearestAirport(double lat, double lon) {
        // Major Indian Airports
        double[][] airports = {
            {12.9941, 80.1709}, // Chennai (MAA)
            {13.1986, 77.7066}, // Bengaluru (BLR)
            {28.5562, 77.1000}, // Delhi (DEL)
            {19.0896, 72.8656}  // Mumbai (BOM)
        };
        String[] names = {
            "Chennai International Airport",
            "Bengaluru International Airport",
            "Delhi International Airport",
            "Mumbai International Airport"
        };

        double minDistance = Double.MAX_VALUE;
        int nearestIdx = 1; // default to Bengaluru if anything goes wrong

        for (int i = 0; i < airports.length; i++) {
            double aLat = airports[i][0];
            double aLon = airports[i][1];
            double dist = Math.pow(lat - aLat, 2) + Math.pow(lon - aLon, 2);
            if (dist < minDistance) {
                minDistance = dist;
                nearestIdx = i;
            }
        }
        return names[nearestIdx];
    }

    private void showAirportSelectorDialog() {
        String[] airports = {
            "Chennai International Airport",
            "Bengaluru International Airport",
            "Delhi International Airport",
            "Mumbai International Airport"
        };

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("Select Airport Location");
        builder.setItems(airports, (dialog, which) -> {
            String selected = airports[which];
            SharedPreferences.Editor editor = getSharedPreferences("DashboardData", MODE_PRIVATE).edit();
            editor.putString("custom_airport", selected);
            editor.apply();

            if (tvCurrentAirport != null) tvCurrentAirport.setText(" " + selected);
            if (weatherLocation != null) weatherLocation.setText(selected);
            showSimulatedWeather(selected);
            Toast.makeText(this, "Airport location set to " + selected, Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fetchWeatherData();
        }
    }

    private void loadLastFlight() {
        SharedPreferences dashPrefs = getSharedPreferences("DashboardData", MODE_PRIVATE);
        String code = dashPrefs.getString("last_flight_code", null);
        String status = dashPrefs.getString("last_flight_status", null);

        if (code != null) {
            lastFlightCard.setVisibility(View.VISIBLE);
            lastFlightCode.setText(code);
            
            // Clean up status display if it's too long
            if (status.length() > 20) status = "View Latest Status";
            lastFlightStatus.setText(status);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (navGestureDetector != null && airportViewFlipper != null) {
            Rect rect = new Rect();
            airportViewFlipper.getGlobalVisibleRect(rect);
            // If the touch is NOT inside the Image Slider (ViewFlipper), process navigation
            if (!rect.contains((int)ev.getRawX(), (int)ev.getRawY())) {
                navGestureDetector.onTouchEvent(ev);
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}