package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;

public class LanguageSettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_settings);
        
        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        String currentLang = prefs.getString("App_Lang", "en");

        RadioGroup group = findViewById(R.id.languageGroup);
        
        // Set initial selection
        if (currentLang.equals("en")) group.check(R.id.langEn);
        else if (currentLang.equals("hi")) group.check(R.id.langHi);
        else if (currentLang.equals("te")) group.check(R.id.langTe);
        else if (currentLang.equals("ml")) group.check(R.id.langMl);
        else if (currentLang.equals("ta")) group.check(R.id.langTa);
        else if (currentLang.equals("es")) group.check(R.id.langEs);
        else if (currentLang.equals("fr")) group.check(R.id.langFr);
        else if (currentLang.equals("de")) group.check(R.id.langDe);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String lang = "en";
            if (checkedId == R.id.langEn) lang = "en";
            else if (checkedId == R.id.langHi) lang = "hi";
            else if (checkedId == R.id.langTe) lang = "te";
            else if (checkedId == R.id.langMl) lang = "ml";
            else if (checkedId == R.id.langTa) lang = "ta";
            else if (checkedId == R.id.langEs) lang = "es";
            else if (checkedId == R.id.langFr) lang = "fr";
            else if (checkedId == R.id.langDe) lang = "de";

            LocaleHelper.updateLanguage(this, lang);
            
            android.widget.Toast.makeText(this, "Language updated. Please restart app.", android.widget.Toast.LENGTH_LONG).show();
            
            // Retrieve session to preserve login
            android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
            String email = session.getString("email", null);
            String name = session.getString("name", "User");
            String mobile = session.getString("mobile", "");
            String userType = session.getString("user_type", "Visitor");

            // Restart app automatically
            Intent intent = new Intent(this, MainActivity.class);
            if (email != null) {
                intent.putExtra("email", email);
                intent.putExtra("name", name);
                intent.putExtra("mobile", mobile);
                intent.putExtra("user_type", userType);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }
}
