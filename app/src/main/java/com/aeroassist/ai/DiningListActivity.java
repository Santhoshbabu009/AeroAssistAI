package com.aeroassist.ai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DiningListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ServiceAdapter adapter;
    private String type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dining_list);

        type = getIntent().getStringExtra("type");
        if (type == null) type = "dining";

        TextView titleText = findViewById(R.id.titleText);
        updateTitle(titleText);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<ServiceItem> items = getMockData(type);
        adapter = new ServiceAdapter(items);
        recyclerView.setAdapter(adapter);
    }

    private void updateTitle(TextView tv) {
        switch (type) {
            case "shopping": tv.setText("Shopping & Retail"); break;
            case "lounges": tv.setText("Airport Lounges"); break;
            default: tv.setText("Dining & Drinks"); break;
        }
    }

    private List<ServiceItem> getMockData(String type) {
        List<ServiceItem> list = new ArrayList<>();
        if (type.equals("shopping")) {
            list.add(new ServiceItem("Duty Free Americas", "Luxury Goods", "4.2", "🛍️"));
            list.add(new ServiceItem("Relay", "Books & Travel", "4.5", "📖"));
            list.add(new ServiceItem("Tech2Go", "Electronics", "4.0", "🎧"));
        } else if (type.equals("lounges")) {
            list.add(new ServiceItem("Plaza Premium Lounge", "Terminal 1", "4.8", "🛋️"));
            list.add(new ServiceItem("Air India Lounge", "Terminal 2", "4.1", "🍷"));
        } else {
            list.add(new ServiceItem("Starbucks Coffee", "Cafe • Gate 14", "4.5", "☕"));
            list.add(new ServiceItem("Burger King", "Fast Food • Gate 9", "4.2", "🍔"));
            list.add(new ServiceItem("The Great Kabab Factory", "Fine Dining • T2", "4.7", "🍱"));
            list.add(new ServiceItem("Subway", "Quick Service • Gate 22", "4.0", "🥪"));
        }
        return list;
    }

    // Static Data Model
    private static class ServiceItem {
        String name, category, rating, icon;
        ServiceItem(String n, String c, String r, String i) {
            name = n; category = c; rating = r; icon = i;
        }
    }

    // Adapter Class
    private class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ViewHolder> {
        private final List<ServiceItem> items;

        ServiceAdapter(List<ServiceItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ServiceItem item = items.get(position);
            holder.name.setText(item.name);
            holder.category.setText(item.category);
            holder.rating.setText("⭐ " + item.rating);
            holder.icon.setText(item.icon);
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, category, rating, icon;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                category = v.findViewById(R.id.itemCategory);
                rating = v.findViewById(R.id.itemRating);
                icon = v.findViewById(R.id.itemIcon);
            }
        }
    }
}
