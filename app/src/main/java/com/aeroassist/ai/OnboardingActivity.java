package com.aeroassist.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout layoutIndicators;
    private Button btnAction;
    private TextView btnSkip;
    private List<OnboardingItem> onboardingItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if onboarding was already completed in the past
        SharedPreferences config = getSharedPreferences("AppConfig", MODE_PRIVATE);
        if (config.getBoolean("onboarding_completed", false)) {
            launchNextScreen();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        layoutIndicators = findViewById(R.id.layoutIndicators);
        btnAction = findViewById(R.id.btnAction);
        btnSkip = findViewById(R.id.btnSkip);

        setupOnboardingItems();

        viewPager.setAdapter(new OnboardingAdapter(onboardingItems));
        setupIndicators(onboardingItems.size());
        setCurrentIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
                if (position == onboardingItems.size() - 1) {
                    btnAction.setText("Get Started");
                } else {
                    btnAction.setText("Continue");
                }
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());

        btnAction.setOnClickListener(v -> {
            int currentItem = viewPager.getCurrentItem();
            if (currentItem < onboardingItems.size() - 1) {
                viewPager.setCurrentItem(currentItem + 1);
            } else {
                finishOnboarding();
            }
        });
    }

    private void setupOnboardingItems() {
        onboardingItems = new ArrayList<>();
        onboardingItems.add(new OnboardingItem(
                "Smart Flight Companion",
                "Real-time flight tracking, smart gate alerts, and passenger assistant right in your pocket.",
                R.drawable.ic_plane
        ));
        onboardingItems.add(new OnboardingItem(
                "AI Help Desk",
                "Have a travel question? Chat with Aero, our state-of-the-art AI assistant, for airport guidance.",
                R.drawable.ic_robot
        ));
        onboardingItems.add(new OnboardingItem(
                "Express Digital Passes",
                "Store your boarding passes, luggage logs, and digital travel documents safely on-device.",
                R.drawable.ic_wallet
        ));
    }

    private void setupIndicators(int count) {
        ImageView[] indicators = new ImageView[count];
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);

        for (int i = 0; i < count; i++) {
            indicators[i] = new ImageView(getApplicationContext());
            indicators[i].setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                    getApplicationContext(), R.drawable.indicator_inactive
            ));
            indicators[i].setLayoutParams(params);
            layoutIndicators.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int index) {
        int childCount = layoutIndicators.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) layoutIndicators.getChildAt(i);
            if (i == index) {
                imageView.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                        getApplicationContext(), R.drawable.indicator_active
                ));
            } else {
                imageView.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                        getApplicationContext(), R.drawable.indicator_inactive
                ));
            }
        }
    }

    private void finishOnboarding() {
        SharedPreferences config = getSharedPreferences("AppConfig", MODE_PRIVATE);
        config.edit().putBoolean("onboarding_completed", true).apply();
        launchNextScreen();
    }

    private void launchNextScreen() {
        Intent intent = new Intent(OnboardingActivity.this, UserTypeSelectionActivity.class);
        startActivity(intent);
        finish();
    }

    // Slide Data Model
    private static class OnboardingItem {
        private final String title;
        private final String description;
        private final int iconResId;

        OnboardingItem(String title, String description, int iconResId) {
            this.title = title;
            this.description = description;
            this.iconResId = iconResId;
        }
    }

    // ViewPager2 Adapter
    private static class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {
        private final List<OnboardingItem> items;

        OnboardingAdapter(List<OnboardingItem> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_slide, parent, false)
            );
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            OnboardingItem item = items.get(position);
            holder.slideTitle.setText(item.title);
            holder.slideDescription.setText(item.description);
            holder.slideIcon.setImageResource(item.iconResId);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView slideTitle, slideDescription;
            ImageView slideIcon;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                slideTitle = itemView.findViewById(R.id.slideTitle);
                slideDescription = itemView.findViewById(R.id.slideDescription);
                slideIcon = itemView.findViewById(R.id.slideIcon);
            }
        }
    }
}
