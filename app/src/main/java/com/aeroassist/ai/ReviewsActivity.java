package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ReviewsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reviews);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnPostReview).setOnClickListener(v -> startActivity(new Intent(this, PostReviewActivity.class)));

        RecyclerView rv = findViewById(R.id.rvReviews);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Review> list = new ArrayList<>();
        list.add(new Review("John Doe", "Terminal 2 is amazing, very clean!", "â­â­â­â­â­"));
        list.add(new Review("Alice Smith", "Food court in T1 has great options.", "â­â­â­â­"));
        list.add(new Review("Bob Wilson", "Security lines were a bit long today.", "â­â­â­"));

        rv.setAdapter(new ReviewAdapter(list));
    }

    private static class Review {
        String user, content, rating;
        Review(String u, String c, String r) { user = u; content = c; rating = r; }
    }

    private class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {
        private final List<Review> items;
        ReviewAdapter(List<Review> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Review item = items.get(position);
            holder.name.setText(item.user);
            holder.desc.setText(item.content);
            holder.rating.setText(item.rating);
            holder.icon.setText("ðŸ‘¤");
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, rating, icon;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                desc = v.findViewById(R.id.itemCategory);
                rating = v.findViewById(R.id.itemRating);
                icon = v.findViewById(R.id.itemIcon);
            }
        }
    }
}
