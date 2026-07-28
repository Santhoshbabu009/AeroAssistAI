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
import androidx.room.Room;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatHistoryActivity extends BaseActivity {

    RecyclerView historyRecycler;
    ChatDatabase db;
    String email, type;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        email = getIntent().getStringExtra("email");
        type = getIntent().getStringExtra("user_type");

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        historyRecycler = findViewById(R.id.historyRecycler);
        historyRecycler.setLayoutManager(new LinearLayoutManager(this));

        db = ChatDatabase.getInstance(this);

        new Thread(() -> {
            List<Long> sessions = db.chatDao().getAllSessions(email, type);
            runOnUiThread(() -> {
                historyRecycler.setAdapter(new SessionAdapter(sessions));
            });
        }).start();
    }

    class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {
        List<Long> sessions;
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

        SessionAdapter(List<Long> sessions) {
            this.sessions = sessions;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_session, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            long sessionId = sessions.get(position);
            holder.title.setText("Conversation #" + (getItemCount() - position));
            holder.date.setText(sdf.format(new Date(sessionId)));

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ChatHistoryActivity.this, ChatbotActivity.class);
                intent.putExtra("email", email);
                intent.putExtra("user_type", type);
                intent.putExtra("session_id", sessionId);
                startActivity(intent);
                finish();
            });
        }

        @Override
        public int getItemCount() {
            return sessions.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, date;
            ViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.sessionTitle);
                date = v.findViewById(R.id.sessionDate);
            }
        }
    }
}
