package com.aeroassist.ai;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LostFoundListActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private RecyclerView rv;
    private List<LostItem> allItemsList;
    private List<LostItem> filteredList;
    private LostAdapter adapter;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lost_found_list);

        client = new OkHttpClient();
        allItemsList = new ArrayList<>();
        filteredList = new ArrayList<>();

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
        findViewById(R.id.btnReportNew).setOnClickListener(v -> startActivity(new Intent(this, LostFoundReportActivity.class)));

        tabLayout = findViewById(R.id.tabLayout);
        rv = findViewById(R.id.rvLostFound);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        adapter = new LostAdapter(filteredList);
        rv.setAdapter(adapter);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterListByTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchLostItems();
    }

    private void fetchLostItems() {
        String url = Constants.BACKEND_BASE_URL + "/api/lost-items";
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(LostFoundListActivity.this, "Failed to load items: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String res = response.body().string();
                try {
                    JSONObject obj = new JSONObject(res);
                    if ("success".equals(obj.optString("status"))) {
                        JSONArray arr = obj.getJSONArray("items");
                        List<LostItem> fetchedList = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject item = arr.getJSONObject(i);
                            fetchedList.add(new LostItem(
                                    item.getInt("id"),
                                    item.getString("name"),
                                    item.getString("description"),
                                    item.getString("icon"),
                                    item.optString("type", "Lost"),
                                    item.optString("contact", ""),
                                    item.optString("image", "")
                            ));
                        }

                        runOnUiThread(() -> {
                            allItemsList.clear();
                            allItemsList.addAll(fetchedList);
                            filterListByTab(tabLayout.getSelectedTabPosition());
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(LostFoundListActivity.this, "Error parsing server data", Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    private void filterListByTab(int tabIndex) {
        String filterType = (tabIndex == 1) ? "Found" : "Lost";
        filteredList.clear();
        for (LostItem item : allItemsList) {
            if (filterType.equalsIgnoreCase(item.type)) {
                filteredList.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showDetailsDialog(LostItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        layout.setBackgroundColor(Color.parseColor("#F8FAFC"));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        if (item.image != null && !item.image.isEmpty()) {
            CardView imageCard = new CardView(this);
            imageCard.setRadius(dpToPx(16));
            imageCard.setCardElevation(dpToPx(4));
            
            ImageView photoView = new ImageView(this);
            photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                byte[] decodedString = Base64.decode(item.image, Base64.DEFAULT);
                Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                photoView.setImageBitmap(decodedByte);
            } catch (Exception e) {
                photoView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
            
            LinearLayout.LayoutParams photoParams = new LinearLayout.LayoutParams(dpToPx(140), dpToPx(140));
            imageCard.addView(photoView, photoParams);
            layout.addView(imageCard);
        } else {
            TextView emojiIcon = new TextView(this);
            emojiIcon.setText(item.icon);
            emojiIcon.setTextSize(48);
            emojiIcon.setGravity(Gravity.CENTER);
            layout.addView(emojiIcon);
        }

        TextView title = new TextView(this);
        title.setText(item.name);
        title.setTextSize(20);
        title.setTextColor(Color.parseColor("#0F172A"));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dpToPx(12);
        layout.addView(title, titleParams);

        TextView badge = new TextView(this);
        badge.setText(item.type.toUpperCase() + " ITEM");
        badge.setTextSize(11);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3));
        
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setCornerRadius(dpToPx(6));
        if ("Found".equalsIgnoreCase(item.type)) {
            badgeBg.setColor(Color.parseColor("#10B981")); // Emerald Green
        } else {
            badgeBg.setColor(Color.parseColor("#EF4444")); // Red
        }
        badge.setBackground(badgeBg);
        
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = dpToPx(6);
        layout.addView(badge, badgeParams);

        TextView desc = new TextView(this);
        desc.setText(item.desc);
        desc.setTextSize(14);
        desc.setTextColor(Color.parseColor("#475569"));
        desc.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dpToPx(12);
        layout.addView(desc, descParams);

        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divParams.topMargin = dpToPx(18);
        divParams.bottomMargin = dpToPx(18);
        layout.addView(divider, divParams);

        TextView actionTitle = new TextView(this);
        if ("Found".equalsIgnoreCase(item.type)) {
            actionTitle.setText("Lost this item? Contact the finder to get it back:");
        } else {
            actionTitle.setText("Found this item? Reach out to the owner immediately:");
        }
        actionTitle.setTextSize(13);
        actionTitle.setTextColor(Color.parseColor("#1E293B"));
        actionTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        actionTitle.setGravity(Gravity.CENTER);
        layout.addView(actionTitle);

        TextView contactVal = new TextView(this);
        contactVal.setText(item.contact);
        contactVal.setTextSize(18);
        contactVal.setTextColor(Color.parseColor("#4F46E5"));
        contactVal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        contactVal.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams contactParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        contactParams.topMargin = dpToPx(8);
        layout.addView(contactVal, contactParams);

        // Buttons Layout
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = dpToPx(24);
        layout.addView(buttonsLayout, buttonsParams);

        AlertDialog dialog = builder.setView(layout).create();

        // Resolve & Remove Button
        TextView resolveBtn = new TextView(this);
        resolveBtn.setText("Resolve & Remove");
        resolveBtn.setTextSize(13);
        resolveBtn.setTextColor(Color.parseColor("#EF4444"));
        resolveBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        resolveBtn.setGravity(Gravity.CENTER);
        resolveBtn.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        
        GradientDrawable resolveBg = new GradientDrawable();
        resolveBg.setColor(Color.parseColor("#FEE2E2")); // Light red bg
        resolveBg.setCornerRadius(dpToPx(8));
        resolveBtn.setBackground(resolveBg);
        
        resolveBtn.setClickable(true);
        resolveBtn.setFocusable(true);
        
        LinearLayout.LayoutParams resolveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resolveParams.rightMargin = dpToPx(16);
        buttonsLayout.addView(resolveBtn, resolveParams);

        // Dismiss Button
        TextView closeBtn = new TextView(this);
        closeBtn.setText("Dismiss");
        closeBtn.setTextSize(13);
        closeBtn.setTextColor(Color.parseColor("#475569"));
        closeBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
        
        GradientDrawable closeBgDrawable = new GradientDrawable();
        closeBgDrawable.setColor(Color.parseColor("#E2E8F0")); // Light slate bg
        closeBgDrawable.setCornerRadius(dpToPx(8));
        closeBtn.setBackground(closeBgDrawable);
        
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        buttonsLayout.addView(closeBtn);

        closeBtn.setOnClickListener(v -> dialog.dismiss());
        resolveBtn.setOnClickListener(v -> resolveAndRemoveItem(item, dialog));

        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setColor(Color.parseColor("#F8FAFC"));
            windowBg.setCornerRadius(dpToPx(20));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
    }

    private void resolveAndRemoveItem(LostItem item, AlertDialog dialog) {
        try {
            JSONObject json = new JSONObject();
            json.put("id", item.id);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/lost-items/delete";
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(LostFoundListActivity.this, "Failed to resolve item: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    runOnUiThread(() -> {
                        Toast.makeText(LostFoundListActivity.this, "Item successfully resolved and removed!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        fetchLostItems();
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "JSON Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static class LostItem {
        int id;
        String name, desc, icon, type, contact, image;
        LostItem(int id, String n, String d, String i, String t, String c, String img) {
            this.id = id;
            name = n; desc = d; icon = i; type = t; contact = c; image = img;
        }
    }

    private class LostAdapter extends RecyclerView.Adapter<LostAdapter.ViewHolder> {
        private final List<LostItem> items;
        LostAdapter(List<LostItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_service, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            LostItem item = items.get(position);
            holder.name.setText(item.name);
            holder.desc.setText(item.desc);
            holder.rating.setVisibility(View.GONE);

            if (item.image != null && !item.image.isEmpty()) {
                holder.imgPhoto.setVisibility(View.VISIBLE);
                holder.icon.setVisibility(View.GONE);
                try {
                    byte[] decodedString = Base64.decode(item.image, Base64.DEFAULT);
                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    holder.imgPhoto.setImageBitmap(decodedByte);
                } catch (Exception e) {
                    holder.imgPhoto.setVisibility(View.GONE);
                    holder.icon.setVisibility(View.VISIBLE);
                    holder.icon.setText(item.icon);
                }
            } else {
                holder.imgPhoto.setVisibility(View.GONE);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setText(item.icon);
            }

            holder.itemView.setOnClickListener(v -> showDetailsDialog(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, desc, icon, rating;
            ImageView imgPhoto;
            ViewHolder(View v) {
                super(v);
                name = v.findViewById(R.id.itemName);
                desc = v.findViewById(R.id.itemCategory);
                icon = v.findViewById(R.id.itemIcon);
                rating = v.findViewById(R.id.itemRating);
                imgPhoto = v.findViewById(R.id.imgItemPhoto);
            }
        }
    }
}
