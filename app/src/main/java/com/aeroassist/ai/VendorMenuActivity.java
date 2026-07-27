package com.aeroassist.ai;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

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

public class VendorMenuActivity extends AppCompatActivity {

    private ImageView backBtn;
    private RecyclerView recyclerView;
    private FloatingActionButton addItemFab;

    private long vendorId;
    private OkHttpClient client;
    private List<JSONObject> productsList = new ArrayList<>();
    private ProductAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vendor_menu);

        SharedPreferences prefs = getSharedPreferences("VendorSession", MODE_PRIVATE);
        vendorId = prefs.getLong("vendor_id", -1);

        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        recyclerView = findViewById(R.id.recyclerView);
        addItemFab = findViewById(R.id.addItemFab);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProductAdapter();
        recyclerView.setAdapter(adapter);

        backBtn.setOnClickListener(v -> finish());
        addItemFab.setOnClickListener(v -> showProductDialog(null));

        fetchProducts();
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

    private void showProductDialog(JSONObject productToEdit) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.spinner, null); // reusing simple view for layout or create simple custom
        
        // Let's create dialog programmatically to keep it simple and clean
        builder.setTitle(productToEdit == null ? "Add Menu Item" : "Edit Menu Item");
        
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.spinner, null); // Let's avoid XML inflation errors by programmatically setting views or creating simple inputs
        
        LinearLayoutManager ll; // dummy check
        
        // Set up custom dialog views
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Item Name (e.g. Cheese Burger)");
        layout.addView(nameInput);

        final EditText priceInput = new EditText(this);
        priceInput.setHint("Price (e.g. 199.00)");
        priceInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(priceInput);

        final EditText categoryInput = new EditText(this);
        categoryInput.setHint("Category (e.g. Burgers, Sides, Drinks)");
        layout.addView(categoryInput);

        final EditText descInput = new EditText(this);
        descInput.setHint("Description (e.g. Loaded with cheddar cheese)");
        layout.addView(descInput);

        // Pre-fill if editing
        if (productToEdit != null) {
            nameInput.setText(productToEdit.optString("name"));
            priceInput.setText(String.valueOf(productToEdit.optDouble("price")));
            categoryInput.setText(productToEdit.optString("category"));
            descInput.setText(productToEdit.optString("description"));
        }

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String priceStr = priceInput.getText().toString().trim();
            String category = categoryInput.getText().toString().trim();
            String desc = descInput.getText().toString().trim();

            if (name.isEmpty() || priceStr.isEmpty() || category.isEmpty()) {
                Toast.makeText(VendorMenuActivity.this, "Please fill Name, Price, and Category", Toast.LENGTH_SHORT).show();
                return;
            }

            double price = Double.parseDouble(priceStr);
            saveProduct(productToEdit, name, price, category, desc);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void saveProduct(JSONObject productToEdit, String name, double price, String category, String desc) {
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/products";
        try {
            JSONObject json = new JSONObject();
            json.put("vendor_id", vendorId);
            json.put("name", name);
            json.put("price", price);
            json.put("category", category);
            json.put("description", desc);
            json.put("image_url", ""); // Default blank or sample url

            String method = "POST";
            if (productToEdit != null) {
                method = "PUT";
                json.put("id", productToEdit.optLong("id"));
            }

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.get("application/json; charset=utf-8"));

            Request.Builder reqBuilder = new Request.Builder().url(url);
            if ("PUT".equals(method)) {
                reqBuilder.put(body);
            } else {
                reqBuilder.post(body);
            }

            client.newCall(reqBuilder.build()).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {}

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(VendorMenuActivity.this, "Menu item saved!", Toast.LENGTH_SHORT).show();
                            fetchProducts();
                        });
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteProduct(long productId) {
        String url = Constants.BACKEND_BASE_URL + "/api/vendors/products?id=" + productId;
        Request request = new Request.Builder().url(url).delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {}

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(VendorMenuActivity.this, "Menu item deleted!", Toast.LENGTH_SHORT).show();
                        fetchProducts();
                    });
                }
            }
        });
    }

    // RecyclerView Adapter for managing products
    private class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vendor_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            JSONObject product = productsList.get(position);
            long id = product.optLong("id");
            String nameStr = product.optString("name");
            String catStr = product.optString("category");
            double priceVal = product.optDouble("price", 0.0);

            holder.nameText.setText(nameStr);
            holder.categoryText.setText(catStr);
            holder.priceText.setText("₹" + String.format("%.2f", priceVal));

            // Set simple item emoji indicator based on category
            String catLower = catStr.toLowerCase();
            if (catLower.contains("drink") || catLower.contains("coffee") || catLower.contains("tea")) {
                holder.placeholderText.setText("☕");
            } else if (catLower.contains("burger")) {
                holder.placeholderText.setText("🍔");
            } else if (catLower.contains("fries") || catLower.contains("side")) {
                holder.placeholderText.setText("🍟");
            } else if (catLower.contains("croissant") || catLower.contains("bake")) {
                holder.placeholderText.setText("🥐");
            } else {
                holder.placeholderText.setText("🍱");
            }

            holder.btnEdit.setOnClickListener(v -> showProductDialog(product));
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(VendorMenuActivity.this)
                        .setTitle("Delete Item")
                        .setMessage("Are you sure you want to delete this menu item?")
                        .setPositiveButton("Yes", (dialog, which) -> deleteProduct(id))
                        .setNegativeButton("No", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return productsList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, categoryText, priceText, placeholderText;
            ImageView btnEdit, btnDelete;

            ViewHolder(View v) {
                super(v);
                nameText = v.findViewById(R.id.productName);
                categoryText = v.findViewById(R.id.productCategory);
                priceText = v.findViewById(R.id.productPrice);
                placeholderText = v.findViewById(R.id.imgPlaceholder);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnDelete);
            }
        }
    }
}
