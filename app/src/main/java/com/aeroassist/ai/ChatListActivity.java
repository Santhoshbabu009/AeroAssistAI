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

public class ChatListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvChats);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<ChatRoom> list = new ArrayList<>();
        list.add(new ChatRoom("JFK Terminal 4 Lounge", "12 people active", "💬"));
        list.add(new ChatRoom("DXB Duty Free Chat", "45 people active", "🛒"));
        list.add(new ChatRoom("LHR Terminal 5 Info", "8 people active", "ℹ️"));

        rv.setAdapter(new ChatAdapter(list));
    }

    private static class ChatRoom {
        String name, status, icon;
        ChatRoom(String n, String s, String i) { name = n; status = s; icon = i; }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private final List<ChatRoom> items;
        ChatAdapter(List<ChatRoom> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatRoom item = items.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.status);
            holder.icon.setText(item.icon);
            holder.rating.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, icon, rating;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                desc = v.findViewById(R.id.itemCategory);
                icon = v.findViewById(R.id.itemIcon);
                rating = v.findViewById(R.id.itemRating);
            }
        }
    }
}
