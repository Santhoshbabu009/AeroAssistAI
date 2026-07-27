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

public class RestaurantMenuActivity extends AppCompatActivity {

    private ImageView backBtn;
    private TextView restaurantNameText;
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
        setContentView(R.layout.activity_restaurant_menu);

        vendorId = getIntent().getLongExtra("vendor_id", -1);
        vendorName = getIntent().getStringExtra("vendor_name");
        email = getIntent().getStringExtra("email");

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        restaurantNameText = findViewById(R.id.restaurantNameText);
        recyclerView = findViewById(R.id.recyclerView);
        cartContainer = findViewById(R.id.cartContainer);
        cartCountText = findViewById(R.id.cartCountText);
        cartTotalText = findViewById(R.id.cartTotalText);
        viewCartBtn = findViewById(R.id.viewCartBtn);

        if (vendorName != null) {
            restaurantNameText.setText(vendorName);
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
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/products?vendor_id=" + vendorId;
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

    private void updateCartUi() {
        int count = CartHelper.getCartCount();
        if (count > 0) {
            cartContainer.setVisibility(View.VISIBLE);
            cartCountText.setText(count + (count == 1 ? " Item Added" : " Items Added"));
            cartTotalText.setText("₹" + String.format("%.2f", CartHelper.getCartTotal()));
        } else {
            cartContainer.setVisibility(View.GONE);
        }
    }

    private class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject prod = productsList.get(position);
            long id = prod.optLong("id");
            String name = prod.optString("name");
            String desc = prod.optString("description");
            double price = prod.optDouble("price", 0.0);
            String imgUrl = prod.optString("image_url");

            holder.nameText.setText(name);
            holder.descText.setText(desc);
            holder.priceText.setText("₹" + String.format("%.2f", price));

            if (imgUrl != null && !imgUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(imgUrl)
                        .placeholder(R.drawable.certificate_bg)
                        .into(holder.productImage);
            } else {
                holder.productImage.setImageResource(R.drawable.certificate_bg);
            }

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
