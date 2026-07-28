package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StoreMenuActivity extends BaseActivity {

    private ImageView backBtn;
    private TextView storeNameText;
    private RecyclerView recyclerView;
    private View cartContainer;
    private TextView cartCountText, cartTotalText;
    private AppCompatButton viewCartBtn;

    private long vendorId;
    private String vendorName, email;
    private OkHttpClient client;
    private List<JSONObject> productsList = new ArrayList<>();
    private MenuAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store_menu);

        vendorId = getIntent().getLongExtra("vendor_id", -1);
        vendorName = getIntent().getStringExtra("vendor_name");
        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        storeNameText = findViewById(R.id.storeNameText);
        recyclerView = findViewById(R.id.recyclerView);
        cartContainer = findViewById(R.id.cartContainer);
        cartCountText = findViewById(R.id.cartCountText);
        cartTotalText = findViewById(R.id.cartTotalText);
        viewCartBtn = findViewById(R.id.viewCartBtn);

        if (vendorName != null) {
            storeNameText.setText(vendorName);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MenuAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
        viewCartBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, CartActivity.class);
            intent.putExtra("email", email);
            startActivity(intent);
        });

        fetchProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartUi();
        adapter.notifyDataSetChanged();
    }

    private void fetchProducts() {
        String url = Constants.BACKEND_BASE_URL + "/api/products?vendor_id=" + vendorId;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String res = response.body().string();
                    try {
                        JSONObject json = new JSONObject(res);
                        if ("success".equals(json.optString("status"))) {
                            JSONArray arr = json.getJSONArray("products");
                            productsList.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                productsList.add(arr.getJSONObject(i));
                            }
                            runOnUiThread(() -> adapter.notifyDataSetChanged());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private static String getFallbackProductImage(String name, String category) {
        String query = (name + " " + category).toLowerCase();
        if (query.contains("burger")) return "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500&auto=format&fit=crop";
        if (query.contains("pizza")) return "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500&auto=format&fit=crop";
        if (query.contains("coffee") || query.contains("latte") || query.contains("cappuccino") || query.contains("espresso") || query.contains("tea") || query.contains("beverage")) return "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=500&auto=format&fit=crop";
        if (query.contains("biryani") || query.contains("rice") || query.contains("curry") || query.contains("dosa") || query.contains("thali")) return "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=500&auto=format&fit=crop";
        if (query.contains("wrap") || query.contains("sandwich") || query.contains("sub") || query.contains("roll")) return "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=500&auto=format&fit=crop";
        if (query.contains("juice") || query.contains("smoothie") || query.contains("drink") || query.contains("shake")) return "https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=500&auto=format&fit=crop";
        if (query.contains("salad") || query.contains("healthy") || query.contains("bowl")) return "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=500&auto=format&fit=crop";
        if (query.contains("dessert") || query.contains("cake") || query.contains("sweet") || query.contains("ice cream") || query.contains("donut")) return "https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=500&auto=format&fit=crop";
        return "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=500&auto=format&fit=crop";
    }

    private void updateCartUi() {
        int count = CartHelper.getCartCount();
        if (count > 0) {
            cartContainer.setVisibility(View.VISIBLE);
            cartCountText.setText(count + (count == 1 ? " Item Added" : " Items Added"));
            cartTotalText.setText("â‚¹" + String.format("%.2f", CartHelper.getCartTotal()));
        } else {
            cartContainer.setVisibility(View.GONE);
        }
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Reuse R.layout.item_menu_product directly since it is clean, premium, and reusable
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject prod = productsList.get(position);
            long id = prod.optLong("id");
            String name = prod.optString("name");
            String desc = prod.optString("description");
            String cat = prod.optString("category");
            double price = prod.optDouble("price", 0.0);
            String imgUrl = prod.optString("image_url");
            if (imgUrl == null || imgUrl.trim().isEmpty()) {
                imgUrl = getFallbackProductImage(name, cat);
            }

            holder.nameText.setText(name);
            holder.descText.setText(desc);
            holder.priceText.setText("â‚¹" + String.format("%.2f", price));

            Glide.with(holder.itemView.getContext())
                    .load(imgUrl)
                    .placeholder(R.drawable.certificate_bg)
                    .into(holder.productImage);

            int qty = CartHelper.getProductQuantity(id);
            if (qty > 0) {
                holder.btnAdd.setVisibility(View.GONE);
                holder.qtyContainer.setVisibility(View.VISIBLE);
                holder.qtyText.setText(String.valueOf(qty));
            } else {
                holder.btnAdd.setVisibility(View.VISIBLE);
                holder.qtyContainer.setVisibility(View.GONE);
            }

            holder.btnAdd.setOnClickListener(v -> {
                CartHelper.addItem(prod, vendorId, vendorName);
                updateCartUi();
                notifyItemChanged(position);
            });

            holder.btnPlus.setOnClickListener(v -> {
                CartHelper.addItem(prod, vendorId, vendorName);
                updateCartUi();
                notifyItemChanged(position);
            });

            holder.btnMinus.setOnClickListener(v -> {
                CartHelper.removeItem(prod);
                updateCartUi();
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return productsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, descText, priceText, qtyText, btnMinus, btnPlus;
            AppCompatButton btnAdd;
            LinearLayout qtyContainer;
            ImageView productImage;

            ViewHolder(View v) {
                super(v);
                nameText = v.findViewById(R.id.productName);
                descText = v.findViewById(R.id.productDesc);
                priceText = v.findViewById(R.id.productPrice);
                qtyText = v.findViewById(R.id.qtyText);
                btnMinus = v.findViewById(R.id.btnMinus);
                btnPlus = v.findViewById(R.id.btnPlus);
                btnAdd = v.findViewById(R.id.btnAdd);
                qtyContainer = v.findViewById(R.id.qtyContainer);
                productImage = v.findViewById(R.id.productImage);
            }
        }
    }
}
