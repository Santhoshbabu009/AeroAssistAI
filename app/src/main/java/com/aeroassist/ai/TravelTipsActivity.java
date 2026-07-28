package com.aeroassist.ai;

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

public class TravelTipsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_tips);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvTips);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Tip> list = new ArrayList<>();
        list.add(new Tip("Arrive early for international flights to avoid long security queues.", "ðŸ’¡ Tip #1"));
        list.add(new Tip("Free water stations are located near Gate 5 and Gate 18.", "ðŸ’¡ Tip #2"));
        list.add(new Tip("Use the terminal shuttle instead of walking between T1 and T2.", "ðŸ’¡ Tip #3"));

        rv.setAdapter(new TipAdapter(list));
    }

    private static class Tip {
        String content, id;
        Tip(String c, String i) { content = c; id = i; }
    }

    private class TipAdapter extends RecyclerView.Adapter<TipAdapter.ViewHolder> {
        private final List<Tip> items;
        TipAdapter(List<Tip> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Tip item = items.get(position);
            holder.name.setText(item.id);
            holder.desc.setText(item.content);
            holder.rating.setVisibility(View.GONE);
            holder.icon.setText("âœˆï¸");
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
