package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class IDCardActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_id_card);
        
        SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        String userEmail = session.getString("email", "default");
        String userName = session.getString("name", "User");
        String userType = session.getString("user_type", "Visitor");

        SharedPreferences userData = getSharedPreferences("UserData", MODE_PRIVATE);
        
        // 1. Set Image
        ImageView profilePic = findViewById(R.id.profilePic);
        String savedImage = userData.getString("image_" + userEmail, null);
        if (savedImage != null) {
            byte[] imageBytes = Base64.decode(savedImage, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            profilePic.setImageBitmap(bitmap);
        }

        // 2. Set Name
        TextView surnameTxt = findViewById(R.id.idSurname);
        TextView givenNamesTxt = findViewById(R.id.idGivenNames);
        
        String[] nameParts = userName.split(" ");
        if (nameParts.length > 1) {
            surnameTxt.setText(nameParts[nameParts.length - 1].toUpperCase());
            StringBuilder givenNames = new StringBuilder();
            for (int i = 0; i < nameParts.length - 1; i++) {
                givenNames.append(nameParts[i]).append(" ");
            }
            givenNamesTxt.setText(givenNames.toString().trim().toUpperCase());
        } else {
            surnameTxt.setText(userName.toUpperCase());
            givenNamesTxt.setText("-");
        }

        // 3. Set Sequential ID Number
        TextView docNoTxt = findViewById(R.id.idDocNo);
        int loginOrder = getLoginOrder(userEmail);
        docNoTxt.setText(String.format("AA-%04d", loginOrder));

        // 4. Set Role Tag
        TextView roleTag = findViewById(R.id.roleTag);
        roleTag.setText(userType.toUpperCase());
        
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }

    private int getLoginOrder(String email) {
        SharedPreferences prefs = getSharedPreferences("GlobalStats", MODE_PRIVATE);
        // Check if this email already has a number
        int existingOrder = prefs.getInt("order_" + email, -1);
        if (existingOrder != -1) {
            return existingOrder;
        }

        // Otherwise, get the next number
        int nextOrder = prefs.getInt("total_users_count", 0) + 1;
        prefs.edit()
             .putInt("total_users_count", nextOrder)
             .putInt("order_" + email, nextOrder)
             .apply();
        
        return nextOrder;
    }
}
