package com.aeroassist.ai;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ola.mapsdk.camera.MapControlSettings;
import com.ola.mapsdk.interfaces.OlaMapCallback;
import com.ola.mapsdk.model.OlaLatLng;
import com.ola.mapsdk.model.OlaMarkerOptions;
import com.ola.mapsdk.model.OlaPolylineOptions;
import com.ola.mapsdk.view.Marker;
import com.ola.mapsdk.view.OlaMap;
import com.ola.mapsdk.view.OlaMapView;
import com.ola.mapsdk.view.Polyline;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;

public class NavigationActivity extends AppCompatActivity implements OlaMapCallback {

    private OlaMap olaMap;
    private OlaMapView mapView;
    private Polyline currentRoute;
    private List<Marker> durationMarkers = new ArrayList<>();
    Button findAirportBtn;

    FusedLocationProviderClient fusedLocationClient;
    LocationCallback locationCallback;
    Marker userMarker;
    Marker destinationMarker;

    boolean airportFound = false;
    OlaLatLng userLastLocation = null;
    OlaLatLng destinationLocation = null;
    String locationIntent;

    private List<Marker> poiMarkers = new ArrayList<>();
    private Button btnGates, btnRestrooms, btnFood, btnSecurity, btnLounges;

    // Google Maps UI Enhancements
    private BottomSheetBehavior bottomSheetBehavior;
    private View bottomSheet, layoutChooseDestination, layoutRouteDetails;
    private TextView tvOrigin, tvDestination, tvDuration, tvDistance;
    private RecyclerView rvDestinations, rvRouteSteps;
    private Button btnStartNav, btnCancelNav;
    private TerminalAdapter terminalAdapter;
    private StepAdapter stepAdapter;
    private TextView tvDirectionsLabel;

    private EditText etSearchPlace;
    private ImageButton btnSearchPlace;
    private View indoorFiltersRow;

    // TTS & Voice Navigation Simulation
    private TextToSpeech textToSpeech;
    private List<Step> globalRouteSteps = new ArrayList<>();
    private Handler ttsHandler = new Handler(Looper.getMainLooper());
    private Runnable ttsRunnable;
    private int currentTtsStepIndex = 0;

    // Helper: create a marker using Builder pattern
    private Marker addMarker(OlaLatLng position, String title, Integer iconResId) {
        OlaMarkerOptions.Builder builder = new OlaMarkerOptions.Builder()
                .setPosition(position)
                .setSnippet(title);
        
        if (iconResId != null) {
            builder.setIconIntRes(iconResId);
        }
        
        return olaMap.addMarker(builder.build());
    }

    // Helper: create a polyline using Builder pattern
    private Polyline addPolyline(ArrayList<OlaLatLng> points, String color, float width) {
        OlaPolylineOptions options = new OlaPolylineOptions.Builder()
                .setPoints(points)
                .setColor(color)
                .setWidth(width)
                .build();
        return olaMap.addPolyline(options);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        locationIntent = getIntent().getStringExtra("location");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapView = findViewById(R.id.mapView);
        mapView.getMap(Constants.OLA_MAPS_API_KEY, this, new MapControlSettings.Builder().build());

        setupNewUI();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.US);
            }
        });
    }

    private void setupNewUI() {
        bottomSheet = findViewById(R.id.bottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

        layoutChooseDestination = findViewById(R.id.layoutChooseDestination);
        layoutRouteDetails = findViewById(R.id.layoutRouteDetails);

        tvOrigin = findViewById(R.id.tvOrigin);
        tvDestination = findViewById(R.id.tvDestination);
        tvDuration = findViewById(R.id.tvDuration);
        tvDistance = findViewById(R.id.tvDistance);

        rvDestinations = findViewById(R.id.rvDestinations);
        btnStartNav = findViewById(R.id.btnStartNav);
        btnCancelNav = findViewById(R.id.btnCancelNav);

        rvDestinations.setLayoutManager(new LinearLayoutManager(this));
        rvRouteSteps = findViewById(R.id.rvRouteSteps);
        tvDirectionsLabel = findViewById(R.id.tvDirectionsLabel);
        rvRouteSteps.setLayoutManager(new LinearLayoutManager(this));
        stepAdapter = new StepAdapter(new ArrayList<>());
        rvRouteSteps.setAdapter(stepAdapter);

        indoorFiltersRow = findViewById(R.id.indoorFiltersRow);
        etSearchPlace = findViewById(R.id.etSearchPlace);
        btnSearchPlace = findViewById(R.id.btnSearchPlace);

        btnSearchPlace.setOnClickListener(v -> performSearch());
        etSearchPlace.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch();
                return true;
            }
            return false;
        });

        List<Terminal> terminals = new ArrayList<>();
        terminals.add(new Terminal("Terminal 1", "International Departures", new OlaLatLng(12.9816, 80.1631, 0.0)));
        terminals.add(new Terminal("Terminal 2", "Domestic Arrivals", new OlaLatLng(12.9790, 80.1633, 0.0)));
        terminals.add(new Terminal("Terminal 4", "New Domestic Terminal", new OlaLatLng(12.9772, 80.1601, 0.0)));
        terminals.add(new Terminal("Airport Gate A12", "Boarding Area", new OlaLatLng(13.0097, 80.2206, 0.0)));

        terminalAdapter = new TerminalAdapter(terminals, terminal -> {
            destinationLocation = terminal.location;
            tvDestination.setText(terminal.name);
            tvDestination.setTextColor(Color.parseColor("#202124"));
            
            airportFound = true;
            indoorFiltersRow.setVisibility(View.VISIBLE);
            
            if (userLastLocation != null) {
                drawRoute(userLastLocation, destinationLocation);
                switchToRouteDetails();
            }
        });
        rvDestinations.setAdapter(terminalAdapter);

        btnCancelNav.setOnClickListener(v -> {
            airportFound = false;
            destinationLocation = null;
            locationIntent = null; // Clear intent to prevent loop
            if (currentRoute != null) currentRoute.removePolyline();
            if (destinationMarker != null) destinationMarker.removeMarker();
            switchToChooseDestination();
            tvDestination.setText("Choose destination");
            tvDestination.setTextColor(Color.parseColor("#666666"));
            etSearchPlace.setText("");

            if (ttsRunnable != null) ttsHandler.removeCallbacks(ttsRunnable);
            if (textToSpeech != null) textToSpeech.stop();
        });

        btnStartNav.setOnClickListener(v -> {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            startVoiceNavigation();
        });

        findAirportBtn = findViewById(R.id.findAirportBtn);
        findAirportBtn.setOnClickListener(v -> {
            if (userLastLocation != null) {
                findAirportBtn.setText("Searching nearest airport...");
                findNearestAirport(userLastLocation);
            } else {
                Toast.makeText(this, "Waiting for GPS location...", Toast.LENGTH_SHORT).show();
            }
        });

        setupPOIFilters();
    }

    private void switchToRouteDetails() {
        layoutChooseDestination.setVisibility(View.GONE);
        layoutRouteDetails.setVisibility(View.VISIBLE);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void switchToChooseDestination() {
        layoutChooseDestination.setVisibility(View.VISIBLE);
        layoutRouteDetails.setVisibility(View.GONE);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }

    private void performSearch() {
        String query = etSearchPlace.getText().toString().trim();
        if (query.isEmpty()) return;
        Toast.makeText(this, "Searching...", Toast.LENGTH_SHORT).show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(NavigationActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(query, 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        destinationLocation = new OlaLatLng(address.getLatitude(), address.getLongitude(), 0.0);
                        tvDestination.setText(address.getFeatureName() != null ? address.getFeatureName() : query);
                        tvDestination.setTextColor(Color.parseColor("#202124"));
                        
                        // Hide indoor filters for generic places
                        airportFound = true; // Use airportFound=true to prevent intent overriding it
                        locationIntent = null; // Clear auto-nav loop
                        indoorFiltersRow.setVisibility(View.GONE);
                        
                        if (destinationMarker != null) destinationMarker.removeMarker();
                        destinationMarker = addMarker(destinationLocation, tvDestination.getText().toString(), R.drawable.ic_destination_pin);
                        
                        if (userLastLocation != null) {
                            olaMap.moveCameraToLatLong(destinationLocation, 16, 500);
                            drawRoute(userLastLocation, destinationLocation);
                            switchToRouteDetails();
                        }
                    } else {
                        Toast.makeText(NavigationActivity.this, "Location not found", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(NavigationActivity.this, "Error finding location", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startVoiceNavigation() {
        if (textToSpeech != null) {
            textToSpeech.speak("Starting navigation. Please follow the route.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
        
        currentTtsStepIndex = 0;
        if (ttsRunnable != null) {
            ttsHandler.removeCallbacks(ttsRunnable);
        }

        ttsRunnable = new Runnable() {
            @Override
            public void run() {
                if (globalRouteSteps != null && currentTtsStepIndex < globalRouteSteps.size()) {
                    Step step = globalRouteSteps.get(currentTtsStepIndex);
                    // Standardize text if it has HTML elements
                    String instruction = step.instruction.replaceAll("<[^>]*>", "");
                    
                    if (textToSpeech != null) {
                        textToSpeech.speak(instruction + ". In " + step.distance, TextToSpeech.QUEUE_ADD, null, null);
                    }
                    currentTtsStepIndex++;
                    ttsHandler.postDelayed(this, 15000); // Wait 15s to announce next step
                } else if (globalRouteSteps != null && currentTtsStepIndex >= globalRouteSteps.size() && !globalRouteSteps.isEmpty()) {
                    if (textToSpeech != null) {
                        textToSpeech.speak("You have arrived at your destination.", TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            }
        };
        // Delay first actual turn instruction by 4s to let the initial "Starting navigation" finish
        ttsHandler.postDelayed(ttsRunnable, 4000);
    }

    // Terminal Data Model
    private static class Terminal {
        String name, subtitle;
        OlaLatLng location;
        Terminal(String n, String s, OlaLatLng l) { name = n; subtitle = s; location = l; }
    }

    // Terminal Adapter
    private class TerminalAdapter extends RecyclerView.Adapter<TerminalAdapter.ViewHolder> {
        private final List<Terminal> terminals;
        private final OnTerminalClickListener listener;

        TerminalAdapter(List<Terminal> t, OnTerminalClickListener l) { terminals = t; listener = l; }

        @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_terminal, parent, false);
            return new ViewHolder(v);
        }

        @Override public void onBindViewHolder(ViewHolder holder, int position) {
            Terminal t = terminals.get(position);
            holder.name.setText(t.name);
            holder.subtitle.setText(t.subtitle);
            holder.itemView.setOnClickListener(v -> listener.onTerminalClick(t));
        }

        @Override public int getItemCount() { return terminals.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, subtitle;
            ViewHolder(View v) { super(v); name = v.findViewById(R.id.tvTerminalName); subtitle = v.findViewById(R.id.tvTerminalSubtitle); }
        }
    }

    interface OnTerminalClickListener { void onTerminalClick(Terminal t); }

    // Navigation Step Model
    private static class Step {
        String instruction, distance;
        Step(String i, String d) { instruction = i; distance = d; }
    }

    // Step Adapter for Turn-by-Turn Directions
    private class StepAdapter extends RecyclerView.Adapter<StepAdapter.ViewHolder> {
        private final List<Step> steps;

        StepAdapter(List<Step> s) { steps = s; }

        @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_direction_step, parent, false);
            return new ViewHolder(v);
        }

        @Override public void onBindViewHolder(ViewHolder holder, int position) {
            Step s = steps.get(position);
            String cleanInstruction = android.text.Html.fromHtml(s.instruction, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
            holder.instruction.setText(cleanInstruction);
            holder.distance.setText(s.distance);
        }

        @Override public int getItemCount() { return steps.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView instruction, distance;
            ViewHolder(View v) { super(v); instruction = v.findViewById(R.id.tvStepInstruction); distance = v.findViewById(R.id.tvStepDistance); }
        }
    }

    @Override
    public void onMapReady(OlaMap olaMap) {
        this.olaMap = olaMap;
        startLocationUpdates();
    }

    @Override
    public void onMapError(String error) {
        Log.e("OlaMaps", "Map Error: " + error);
        runOnUiThread(() -> Toast.makeText(this, "Map Error: " + error, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override
    protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
    @Override
    protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override
    protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }
    @Override
    protected void onSaveInstanceState(Bundle outState) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState); }
    @Override
    public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }

    private void startLocationUpdates() {
        LocationRequest request = LocationRequest.create();
        request.setInterval(5000);
        request.setFastestInterval(3000);
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;

                for (android.location.Location loc : result.getLocations()) {
                    userLastLocation = new OlaLatLng(loc.getLatitude(), loc.getLongitude(), 0.0);

                    if (olaMap == null) return;

                    if (userMarker != null) userMarker.removeMarker();
                    userMarker = addMarker(userLastLocation, "You are here", R.drawable.ic_user_location);

                    if (!airportFound) {
                        if (locationIntent != null) {
                            if (locationIntent.equals("gate5")) {
                                destinationLocation = new OlaLatLng(12.9795, 80.1630, 0.0);
                            } else {
                                destinationLocation = new OlaLatLng(13.0097, 80.2206, 0.0);
                            }

                            airportFound = true;
                            locationIntent = null; // Consume intent
                            indoorFiltersRow.setVisibility(View.VISIBLE);
                            
                            if (destinationMarker != null) destinationMarker.removeMarker();
                            destinationMarker = addMarker(destinationLocation, "Navigating to " + locationIntent, R.drawable.ic_destination_pin);

                            olaMap.moveCameraToLatLong(destinationLocation, 16, 500);
                            drawRoute(userLastLocation, destinationLocation);
                            tvDestination.setText("Navigating to " + locationIntent);
                            tvDestination.setTextColor(Color.parseColor("#202124"));
                            switchToRouteDetails();
                        } else {
                            // Only update camera if we haven't found an airport yet
                            if (destinationLocation == null) {
                                olaMap.moveCameraToLatLong(userLastLocation, 14, 500);
                            }
                        }
                    }
                    // Removed continuous drawRoute() here to prevent 5-second UI flicker
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, null);
        }
    }

    private void findNearestAirport(OlaLatLng origin) {
        String url = "https://api.olamaps.io/places/v1/nearbysearch?location="
                + origin.getLatitude() + "," + origin.getLongitude()
                + "&radius=50000&types=airport&api_key=" + Constants.OLA_MAPS_API_KEY;

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Request-Id", UUID.randomUUID().toString())
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                triggerFallbackToChennai(origin);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws java.io.IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonData);
                    JSONArray results = jsonObject.optJSONArray("predictions");

                    if (results != null && results.length() > 0) {
                        JSONObject first = results.getJSONObject(0);
                        JSONObject geom = first.getJSONObject("geometry").getJSONObject("location");
                        double lat = geom.getDouble("lat");
                        double lon = geom.getDouble("lng");
                        String name = first.optString("name", "Nearest Airport");

                        destinationLocation = new OlaLatLng(lat, lon, 0.0);

                        runOnUiThread(() -> {
                            airportFound = true;
                            indoorFiltersRow.setVisibility(View.VISIBLE);
                            if (destinationMarker != null) destinationMarker.removeMarker();
                            destinationMarker = addMarker(destinationLocation, name, R.drawable.ic_destination_pin);

                            olaMap.moveCameraToLatLong(destinationLocation, 16, 500);
                            tvDestination.setText(name);
                            tvDestination.setTextColor(Color.parseColor("#202124"));
                            switchToRouteDetails();
                            drawRoute(origin, destinationLocation);
                        });
                    } else {
                        triggerFallbackToChennai(origin);
                    }
                } catch (Exception e) {
                    triggerFallbackToChennai(origin);
                }
            }
        });
    }

    private void triggerFallbackToChennai(OlaLatLng origin) {
        runOnUiThread(() -> {
            airportFound = true;
            indoorFiltersRow.setVisibility(View.VISIBLE);
            destinationLocation = new OlaLatLng(12.9788, 80.1625, 0.0);
            if (destinationMarker != null) destinationMarker.removeMarker();
            destinationMarker = addMarker(destinationLocation, "Chennai International Airport", R.drawable.ic_destination_pin);
            olaMap.moveCameraToLatLong(destinationLocation, 16, 500);
            tvDestination.setText("Chennai Airport");
            tvDestination.setTextColor(Color.parseColor("#202124"));
            switchToRouteDetails();
            drawRoute(origin, destinationLocation);
        });
    }

    private void drawRoute(OlaLatLng origin, OlaLatLng destination) {
        String url = "https://api.olamaps.io/routing/v1/directions?origin="
                + origin.getLatitude() + "," + origin.getLongitude()
                + "&destination=" + destination.getLatitude() + "," + destination.getLongitude()
                + "&api_key=" + Constants.OLA_MAPS_API_KEY;

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        okhttp3.RequestBody emptyBody = okhttp3.RequestBody.create(new byte[0], null);
        Request request = new Request.Builder()
                .url(url)
                .post(emptyBody)
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                drawFallbackTrackerLine(origin, destination);
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws java.io.IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonData);
                    JSONArray routes = jsonObject.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        JSONArray legs = route.getJSONArray("legs");
                        JSONObject firstLeg = legs.getJSONObject(0);

                        double duration = firstLeg.optDouble("duration", 0);
                        double distance = firstLeg.optDouble("distance", 0);
                        String geometry = route.optString("overview_polyline", "");

                        String durationStr = Math.round(duration / 60.0) + " min";
                        String distanceStr = String.format("%.1f km", distance / 1000.0);

                        List<Step> routeSteps = new ArrayList<>();
                        JSONArray steps = firstLeg.getJSONArray("steps");
                        for (int i = 0; i < steps.length(); i++) {
                            JSONObject s = steps.getJSONObject(i);
                            String instruction = s.optString("instructions", "Continue");
                            String stepDistance = s.optString("readable_distance", "");
                            routeSteps.add(new Step(instruction, stepDistance));
                        }

                        runOnUiThread(() -> {
                            tvDuration.setText(durationStr);
                            tvDistance.setText("(" + distanceStr + ")");
                            if (currentRoute != null) currentRoute.removePolyline();

                            try {
                                ArrayList<OlaLatLng> polylinePoints = decodePolyline(geometry);
                                currentRoute = addPolyline(polylinePoints, "#4285F4", 8f);

                                if (!polylinePoints.isEmpty()) {
                                    olaMap.moveCameraToLatLong(destination, 14, 500);
                                }
                            } catch (Exception e) {
                                drawFallbackTrackerLine(origin, destination);
                            }

                            stepAdapter = new StepAdapter(routeSteps);
                            rvRouteSteps.setAdapter(stepAdapter);
                            globalRouteSteps = routeSteps;
                            tvDirectionsLabel.setVisibility(routeSteps.isEmpty() ? View.GONE : View.VISIBLE);
                            rvRouteSteps.setVisibility(routeSteps.isEmpty() ? View.GONE : View.VISIBLE);
                            switchToRouteDetails();
                        });
                    } else {
                        drawFallbackTrackerLine(origin, destination);
                    }
                } catch (Exception e) {
                    drawFallbackTrackerLine(origin, destination);
                }
            }
        });
    }

    private ArrayList<OlaLatLng> decodePolyline(String encoded) {
        ArrayList<OlaLatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            poly.add(new OlaLatLng(lat / 1E5, lng / 1E5, 0.0));
        }
        return poly;
    }

    private void drawFallbackTrackerLine(OlaLatLng origin, OlaLatLng destination) {
        runOnUiThread(() -> {
            if (currentRoute != null) currentRoute.removePolyline();
            ArrayList<OlaLatLng> pts = new ArrayList<>();
            pts.add(origin);
            pts.add(destination);
            currentRoute = addPolyline(pts, "#0000FF", 10f);
            switchToRouteDetails();
        });
    }

    private void setupPOIFilters() {
        btnGates = findViewById(R.id.btnFilterGates);
        btnRestrooms = findViewById(R.id.btnFilterRestrooms);
        btnFood = findViewById(R.id.btnFilterFood);
        btnSecurity = findViewById(R.id.btnFilterSecurity);
        btnLounges = findViewById(R.id.btnFilterLounges);

        btnGates.setOnClickListener(v -> addPOIMarkers("gate"));
        btnRestrooms.setOnClickListener(v -> addPOIMarkers("restroom"));
        btnFood.setOnClickListener(v -> addPOIMarkers("food"));
        btnSecurity.setOnClickListener(v -> addPOIMarkers("security"));
        btnLounges.setOnClickListener(v -> addPOIMarkers("lounge"));
    }

    private void addPOIMarkers(String type) {
        if (olaMap == null) return;

        for (Marker m : poiMarkers) m.removeMarker();
        poiMarkers.clear();

        if (destinationLocation == null) {
            Toast.makeText(this, "Please select/find an airport first", Toast.LENGTH_SHORT).show();
            return;
        }

        String placesType;
        String categoryName;

        switch (type) {
            case "gate":      placesType = "airport_gate";        categoryName = "Gate/Area"; break;
            case "restroom":  placesType = "restroom";             categoryName = "Restroom"; break;
            case "food":      placesType = "food_court|restaurant"; categoryName = "Food & Dining"; break;
            case "security":  placesType = "security_check";       categoryName = "Airport Security"; break;
            case "lounge":    placesType = "airport_lounge";       categoryName = "Lounge"; break;
            default:          placesType = type;                   categoryName = type; break;
        }

        searchDynamicPOIs(destinationLocation, placesType, categoryName);
    }

    private void searchDynamicPOIs(OlaLatLng airportLoc, String type, String displayTitle) {
        String url = "https://api.olamaps.io/places/v1/nearbysearch?location="
                + airportLoc.getLatitude() + "," + airportLoc.getLongitude()
                + "&radius=2000&types=" + type + "&api_key=" + Constants.OLA_MAPS_API_KEY;

        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Request-Id", UUID.randomUUID().toString())
                .build();

        client.newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(NavigationActivity.this, "Search failed", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) throws java.io.IOException {
                try {
                    String jsonData = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonData);
                    JSONArray results = jsonObject.optJSONArray("predictions");

                    runOnUiThread(() -> {
                        if (results == null || results.length() == 0) {
                            Toast.makeText(NavigationActivity.this, "No results found for " + displayTitle, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        for (Marker m : poiMarkers) m.removeMarker();
                        poiMarkers.clear();

                        try {
                            for (int i = 0; i < Math.min(results.length(), 10); i++) {
                                JSONObject res = results.getJSONObject(i);
                                JSONObject geom = res.getJSONObject("geometry").getJSONObject("location");
                                OlaLatLng pos = new OlaLatLng(geom.getDouble("lat"), geom.getDouble("lng"), 0.0);
                                Marker m = addMarker(pos, res.optString("name", "POI"), null);
                                poiMarkers.add(m);
                            }
                        } catch (Exception e) {
                            Log.e("POI", "Error adding markers", e);
                        }

                        olaMap.moveCameraToLatLong(airportLoc, 17, 500);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(NavigationActivity.this, "Search error", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
