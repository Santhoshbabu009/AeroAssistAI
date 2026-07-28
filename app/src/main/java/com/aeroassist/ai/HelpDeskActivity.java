package com.aeroassist.ai;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class HelpDeskActivity extends BaseActivity {

    private static final String DEVELOPER_EMAIL = "noreplyaeroassistapp@gmail.com";
    private GestureDetector navGestureDetector;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_desk);

        // Receive email from intent
        email = getIntent().getStringExtra("email");

        // Swipe left â†’ Profile page
        navGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 120 && Math.abs(velocityX) > 150) {
                    if (diffX < 0) {
                        // Swipe Left â†’ Profile page
                        Intent intent = new Intent(HelpDeskActivity.this, ProfileActivity.class);
                        intent.putExtra("email", email);
                        startActivity(intent);
                        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                        return true;
                    } else {
                        // Swipe Right â†’ Main page
                        finish();
                        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                        return true;
                    }
                }
                return false;
            }
        });

        // Back button
        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        // Setup individual FAQ toggles
        setupFAQ(R.id.faqLayout1, R.id.answer1, R.id.arrow1);
        setupFAQ(R.id.faqLayout2, R.id.answer2, R.id.arrow2);
        setupFAQ(R.id.faqLayout3, R.id.answer3, R.id.arrow3);
        setupFAQ(R.id.faqLayout4, R.id.answer4, R.id.arrow4);
        setupFAQ(R.id.faqLayout5, R.id.answer5, R.id.arrow5);

        // Chat with Agent button
        findViewById(R.id.chatWithAgentBtn).setOnClickListener(v -> {
            Intent intent = new Intent(HelpDeskActivity.this, ChatbotActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        // Email Developer button
        findViewById(R.id.emailDeveloperBtn).setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:" + DEVELOPER_EMAIL));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "AeroAssist AI â€“ Query/Feedback");
            emailIntent.putExtra(Intent.EXTRA_TEXT,
                    "Hi AeroAssist Team,\n\n[Describe your query here]\n\nUser: " + (email != null ? email : ""));
            try {
                startActivity(Intent.createChooser(emailIntent, "Send Email"));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "No email app found. Please install one.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        navGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void setupFAQ(int layoutId, int answerId, int arrowId) {
        LinearLayout layout = findViewById(layoutId);
        TextView answer = findViewById(answerId);
        ImageView arrow = findViewById(arrowId);

        layout.setOnClickListener(v -> {
            if (answer.getVisibility() == View.GONE) {
                answer.setVisibility(View.VISIBLE);
                arrow.animate().rotation(180).setDuration(200).start();
            } else {
                answer.setVisibility(View.GONE);
                arrow.animate().rotation(0).setDuration(200).start();
            }
        });
    }
}