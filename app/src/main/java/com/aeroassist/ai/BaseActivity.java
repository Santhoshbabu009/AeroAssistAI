package com.aeroassist.ai;

import android.content.Context;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BaseActivity wraps attachBaseContext with LocaleHelper to ensure 
 * whole-app multi-language localization on all activities across Android versions.
 */
public class BaseActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        AeroAssistApplication.resetInactivityTimer();
    }
}
