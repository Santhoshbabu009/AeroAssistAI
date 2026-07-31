package com.aeroassist.ai;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AeroAssistApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private static AeroAssistApplication instance;
    private Handler handler;
    private Runnable inactivityRunnable;
    private Activity currentActivity;
    private static final long INACTIVITY_TIMEOUT = 20 * 60 * 1000; // 20 minutes in milliseconds

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        LocaleHelper.setLocale(this);
        handler = new Handler(Looper.getMainLooper());
        registerActivityLifecycleCallbacks(this);

        inactivityRunnable = () -> handleInactivityLogout();
        resetInactivityTimer();
    }

    public static void resetInactivityTimer() {
        if (instance != null && instance.handler != null && instance.inactivityRunnable != null) {
            instance.handler.removeCallbacks(instance.inactivityRunnable);
            instance.handler.postDelayed(instance.inactivityRunnable, INACTIVITY_TIMEOUT);
        }
    }

    private void handleInactivityLogout() {
        if (currentActivity == null || currentActivity.isFinishing()) {
            return;
        }

        SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        SharedPreferences vendorSession = getSharedPreferences("VendorSession", MODE_PRIVATE);
        SharedPreferences userData = getSharedPreferences("UserData", MODE_PRIVATE);

        boolean isLoggedInUser = session.contains("email") || session.contains("token") || userData.contains("email");
        boolean isLoggedInVendor = vendorSession.contains("email") || vendorSession.contains("vendor_id") || vendorSession.contains("token");

        if (isLoggedInUser || isLoggedInVendor) {
            session.edit().clear().apply();
            vendorSession.edit().clear().apply();
            userData.edit().clear().apply();

            Toast.makeText(currentActivity, "You have been automatically logged out due to 20 minutes of inactivity.", Toast.LENGTH_LONG).show();

            Intent intent = new Intent(currentActivity, UserTypeSelectionActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            currentActivity.startActivity(intent);
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        resetInactivityTimer();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}
    @Override public void onActivityStarted(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
