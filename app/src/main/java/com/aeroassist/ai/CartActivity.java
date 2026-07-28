package com.aeroassist.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.RadioGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.List;

import androidx.appcompat.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.app.ProgressDialog;
import android.os.Handler;

public class CartActivity extends AppCompatActivity {

    private ImageView backBtn;
    private TextView restaurantNameHeader;
    private RecyclerView recyclerView;
    private Spinner terminalSpinner;
    private RadioGroup paymentRadioGroup;
    private EditText gateInput;
    private TextView itemTotalText, taxTotalText, grandTotalText, footerTotalText;
    private AppCompatButton btnCheckout;

    private String email;
    private OkHttpClient client;
    private CartAdapter adapter;

    private String pendingTerminal;
    private String pendingGate;
    private static final int UPI_PAYMENT_REQUEST_CODE = 4321;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        email = getIntent().getStringExtra("email");
        client = new OkHttpClient();

        backBtn = findViewById(R.id.backBtn);
        restaurantNameHeader = findViewById(R.id.restaurantNameHeader);
        recyclerView = findViewById(R.id.recyclerView);
        terminalSpinner = findViewById(R.id.terminalSpinner);
        paymentRadioGroup = findViewById(R.id.paymentRadioGroup);
        gateInput = findViewById(R.id.gateInput);
        itemTotalText = findViewById(R.id.itemTotalText);
        taxTotalText = findViewById(R.id.taxTotalText);
        grandTotalText = findViewById(R.id.grandTotalText);
        footerTotalText = findViewById(R.id.footerTotalText);
        btnCheckout = findViewById(R.id.btnCheckout);

        backBtn.setOnClickListener(v -> finish());

        // Header info
        restaurantNameHeader.setText(CartHelper.getCurrentVendorName() + " Menu");

        // Spinner Setup
        String[] terminals = {"Terminal 1", "Terminal 2", "Terminal 3"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, terminals);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        terminalSpinner.setAdapter(spinnerAdapter);

        // RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CartAdapter();
        recyclerView.setAdapter(adapter);

        btnCheckout.setOnClickListener(v -> placeOrder());

        calculateTotals();
    }

    private void calculateTotals() {
        double subtotal = CartHelper.getCartTotal();
        double deliveryAndTax = subtotal > 0 ? 45.0 : 0.0;
        double grand = subtotal + deliveryAndTax;

        itemTotalText.setText("₹" + String.format("%.2f", subtotal));
        taxTotalText.setText("₹" + String.format("%.2f", deliveryAndTax));
        grandTotalText.setText("₹" + String.format("%.2f", grand));
        footerTotalText.setText("₹" + String.format("%.2f", grand));

        if (subtotal <= 0) {
            btnCheckout.setEnabled(false);
            btnCheckout.setAlpha(0.5f);
        } else {
            btnCheckout.setEnabled(true);
            btnCheckout.setAlpha(1.0f);
        }
    }

    private void placeOrder() {
        String gate = gateInput.getText().toString().trim();
        if (gate.isEmpty()) {
            Toast.makeText(this, "Please enter your Boarding Gate number", Toast.LENGTH_SHORT).show();
            return;
        }

        String terminal = terminalSpinner.getSelectedItem().toString();

        int selectedPaymentId = paymentRadioGroup.getCheckedRadioButtonId();
        String paymentMethod = "Online";
        if (selectedPaymentId == R.id.radioCod) {
            paymentMethod = "COD";
        }

        if ("Online".equals(paymentMethod)) {
            startUpiPaymentFlow(terminal, gate);
        } else {
            executeBackendOrder(terminal, gate, "COD");
        }
    }

    private void startUpiPaymentFlow(String terminal, String gate) {
        double subtotal = CartHelper.getCartTotal();
        double deliveryAndTax = subtotal > 0 ? 45.0 : 0.0;
        double grandTotal = subtotal + deliveryAndTax;

        String amountStr = String.format("%.2f", grandTotal);
        String note = "Food Order - " + CartHelper.getCurrentVendorName();
        String txnId = "TXN" + System.currentTimeMillis();

        // Create standard UPI URI with payee address, name, note, amount, currency and transaction reference ID
        android.net.Uri uri = new android.net.Uri.Builder()
                .scheme("upi")
                .authority("pay")
                .appendQueryParameter("pa", "6380006801@axl")
                .appendQueryParameter("pn", "Santhosh Babu")
                .appendQueryParameter("tn", note)
                .appendQueryParameter("am", amountStr)
                .appendQueryParameter("cu", "INR")
                .appendQueryParameter("tr", txnId)
                .build();

        Intent upiIntent = new Intent(Intent.ACTION_VIEW, uri);

        this.pendingTerminal = terminal;
        this.pendingGate = gate;

        try {
            // Direct launch to invoke native UPI deep link apps
            startActivityForResult(upiIntent, UPI_PAYMENT_REQUEST_CODE);
        } catch (android.content.ActivityNotFoundException e) {
            // Fallback to premium mockup chooser on emulators with no UPI apps
            showMockUpiChooserDialog(terminal, gate, grandTotal);
        } catch (Exception e) {
            showMockUpiChooserDialog(terminal, gate, grandTotal);
        }
    }

    private void showMockUpiChooserDialog(final String terminal, final String gate, final double amount) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Root Layout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
        rootLayout.setBackgroundColor(Color.parseColor("#F8FAFC")); // Clean light slate background
        
        // Title Text
        TextView titleText = new TextView(this);
        titleText.setText("Select UPI Application");
        titleText.setTextSize(18);
        titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleText.setTextColor(Color.parseColor("#0F172A")); // Slate 900
        titleText.setGravity(Gravity.CENTER_HORIZONTAL);
        rootLayout.addView(titleText);
        
        // Subtitle Text
        TextView subText = new TextView(this);
        subText.setText("AeroAssist Premium Checkout • ₹" + String.format("%.2f", amount));
        subText.setTextSize(13);
        subText.setTextColor(Color.parseColor("#64748B")); // Slate 500
        subText.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dpToPx(4);
        subParams.bottomMargin = dpToPx(20);
        rootLayout.addView(subText, subParams);
        
        // Add a line divider
        View divider = new View(this);
        divider.setBackgroundColor(Color.parseColor("#E2E8F0")); // Slate 200
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1));
        divParams.bottomMargin = dpToPx(12);
        rootLayout.addView(divider, divParams);
        
        // UPI App options
        String[] upiApps = {"Google Pay", "PhonePe", "Paytm", "BHIM UPI"};
        String[] upiColors = {"#2563EB", "#7C3AED", "#0052B4", "#10B981"}; // Harmonious curated colors
        
        final AlertDialog dialog = builder.setView(rootLayout).create();
        
        for (int i = 0; i < upiApps.length; i++) {
            final String appName = upiApps[i];
            String appColor = upiColors[i];
            
            // App row container (Card-like layout)
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14));
            row.setClickable(true);
            row.setFocusable(true);
            
            // Set rounded card background programmatically
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.WHITE);
            cardBg.setCornerRadius(dpToPx(12));
            cardBg.setStroke(dpToPx(1), Color.parseColor("#E2E8F0"));
            row.setBackground(cardBg);
            
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(10);
            row.setLayoutParams(rowParams);
            
            // App circle icon placeholder
            TextView appIcon = new TextView(this);
            appIcon.setText(appName.substring(0, 1));
            appIcon.setTextColor(Color.WHITE);
            appIcon.setTextSize(14);
            appIcon.setGravity(Gravity.CENTER);
            appIcon.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            
            GradientDrawable iconBg = new GradientDrawable();
            iconBg.setShape(GradientDrawable.OVAL);
            iconBg.setColor(Color.parseColor(appColor));
            appIcon.setBackground(iconBg);
            
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
            iconParams.rightMargin = dpToPx(12);
            row.addView(appIcon, iconParams);
            
            // App name text
            TextView nameText = new TextView(this);
            nameText.setText(appName);
            nameText.setTextSize(15);
            nameText.setTextColor(Color.parseColor("#1E293B")); // Slate 800
            nameText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
            
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            row.addView(nameText, nameParams);
            
            // Pay action badge
            TextView payBadge = new TextView(this);
            payBadge.setText("Pay");
            payBadge.setTextColor(Color.parseColor("#4F46E5")); // Indigo 600
            payBadge.setTextSize(12);
            payBadge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            payBadge.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(Color.parseColor("#EEF2F6"));
            badgeBg.setCornerRadius(dpToPx(6));
            payBadge.setBackground(badgeBg);
            
            row.addView(payBadge);
            
            // Click action
            row.setOnClickListener(v -> {
                dialog.dismiss();
                simulateUpiPayment(appName, terminal, gate);
            });
            
            rootLayout.addView(row);
        }
        
        // Cancel/Dismiss button
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText("Cancel Payment");
        cancelBtn.setTextSize(14);
        cancelBtn.setTextColor(Color.parseColor("#EF4444")); // Red 500
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        cancelBtn.setClickable(true);
        cancelBtn.setFocusable(true);
        cancelBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.topMargin = dpToPx(12);
        cancelBtn.setLayoutParams(cancelParams);
        
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        rootLayout.addView(cancelBtn);
        
        // Show dialog with rounded window background
        dialog.show();
        if (dialog.getWindow() != null) {
            GradientDrawable windowBg = new GradientDrawable();
            windowBg.setColor(Color.parseColor("#F8FAFC"));
            windowBg.setCornerRadius(dpToPx(20));
            dialog.getWindow().setBackgroundDrawable(windowBg);
        }
    }

    private void simulateUpiPayment(final String appName, final String terminal, final String gate) {
        final ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Contacting " + appName + "...");
        progress.setCancelable(false);
        progress.show();
        
        new Handler().postDelayed(() -> {
            progress.setMessage("Processing payment of ₹" + String.format("%.2f", CartHelper.getCartTotal() + (CartHelper.getCartTotal() > 0 ? 45.0 : 0.0)) + "...");
            
            new Handler().postDelayed(() -> {
                progress.dismiss();
                
                // Show successful checkmark dialog
                GradientDrawable successBg = new GradientDrawable();
                successBg.setColor(Color.WHITE);
                successBg.setCornerRadius(dpToPx(16));
                
                LinearLayout layout = new LinearLayout(CartActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));
                layout.setGravity(Gravity.CENTER);
                
                TextView tick = new TextView(CartActivity.this);
                tick.setText("✔");
                tick.setTextSize(36);
                tick.setTextColor(Color.parseColor("#10B981")); // Tailwind emerald 500
                tick.setGravity(Gravity.CENTER);
                
                GradientDrawable tickBg = new GradientDrawable();
                tickBg.setShape(GradientDrawable.OVAL);
                tickBg.setColor(Color.parseColor("#ECFDF5")); // Tailwind emerald 50
                tickBg.setSize(dpToPx(64), dpToPx(64));
                tick.setBackground(tickBg);
                
                LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(dpToPx(64), dpToPx(64));
                tickParams.bottomMargin = dpToPx(16);
                layout.addView(tick, tickParams);
                
                TextView title = new TextView(CartActivity.this);
                title.setText("Payment Successful!");
                title.setTextSize(18);
                title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                title.setTextColor(Color.parseColor("#0F172A"));
                title.setGravity(Gravity.CENTER);
                
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                titleParams.bottomMargin = dpToPx(8);
                layout.addView(title, titleParams);
                
                TextView sub = new TextView(CartActivity.this);
                sub.setText("Txn ID: TXN" + System.currentTimeMillis() + "\nPaid via " + appName);
                sub.setTextSize(13);
                sub.setTextColor(Color.parseColor("#64748B"));
                sub.setGravity(Gravity.CENTER);
                
                layout.addView(sub);
                
                final AlertDialog successDialog = new AlertDialog.Builder(CartActivity.this)
                        .setView(layout)
                        .setCancelable(false)
                        .create();
                
                successDialog.show();
                if (successDialog.getWindow() != null) {
                    successDialog.getWindow().setBackgroundDrawable(successBg);
                }
                
                new Handler().postDelayed(() -> {
                    successDialog.dismiss();
                    executeBackendOrder(terminal, gate, "Online");
                }, 2000);
                
            }, 1500);
        }, 1200);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UPI_PAYMENT_REQUEST_CODE) {
            boolean paymentSuccess = false;
            String paymentResponse = "";
            
            if (data != null) {
                paymentResponse = data.getStringExtra("response");
                if (paymentResponse == null) {
                    paymentResponse = "";
                }
            }
            
            if (resultCode == RESULT_OK) {
                if (!paymentResponse.isEmpty()) {
                    String[] responseParams = paymentResponse.split("&");
                    for (String param : responseParams) {
                        String[] keyValue = param.split("=");
                        if (keyValue.length >= 2) {
                            String key = keyValue[0].trim().toLowerCase();
                            String value = keyValue[1].trim().toLowerCase();
                            if (key.equals("status") && (value.equals("success") || value.equals("successful"))) {
                                paymentSuccess = true;
                                break;
                            }
                        }
                    }
                } else {
                    paymentSuccess = true;
                }
            }
            
            if (paymentSuccess) {
                Toast.makeText(this, "UPI Payment Successful!", Toast.LENGTH_SHORT).show();
                executeBackendOrder(pendingTerminal, pendingGate, "Online");
            } else {
                Toast.makeText(this, "Payment failed or was cancelled.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void executeBackendOrder(final String terminal, final String gate, final String paymentMethod) {
        android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
        String savedToken = session.getString("auth_token", "");
        String activeEmail = (email != null && !email.isEmpty()) ? email : session.getString("email", "");

        if (savedToken.isEmpty() && activeEmail != null && !activeEmail.isEmpty()) {
            // Self-healing: Fetch fresh token via /api/token-refresh if absent from local session
            fetchTokenAndPlaceOrder(activeEmail, terminal, gate, paymentMethod);
        } else {
            sendOrderWithToken(savedToken, activeEmail, terminal, gate, paymentMethod);
        }
    }

    private void fetchTokenAndPlaceOrder(final String activeEmail, final String terminal, final String gate, final String paymentMethod) {
        try {
            JSONObject refreshJson = new JSONObject();
            refreshJson.put("email", activeEmail);
            RequestBody body = RequestBody.create(
                    refreshJson.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(Constants.API_V1_BASE + "/token-refresh")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    sendOrderWithToken("", activeEmail, terminal, gate, paymentMethod);
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String newToken = "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resJson = new JSONObject(response.body().string());
                            newToken = resJson.optString("token", "");
                            if (!newToken.isEmpty()) {
                                android.content.SharedPreferences session = getSharedPreferences("Session", MODE_PRIVATE);
                                session.edit().putString("auth_token", newToken).apply();
                            }
                        } catch (Exception ignored) {}
                    }
                    sendOrderWithToken(newToken, activeEmail, terminal, gate, paymentMethod);
                }
            });
        } catch (Exception e) {
            sendOrderWithToken("", activeEmail, terminal, gate, paymentMethod);
        }
    }

    private void sendOrderWithToken(final String token, final String activeEmail, final String terminal, final String gate, final String paymentMethod) {
        try {
            JSONObject orderJson = new JSONObject();
            orderJson.put("user_email", activeEmail);
            orderJson.put("vendor_id", CartHelper.getCurrentVendorId());
            orderJson.put("terminal", terminal);
            orderJson.put("gate", gate);
            orderJson.put("payment_method", paymentMethod);

            JSONArray itemsArr = new JSONArray();
            List<CartHelper.CartItem> cartItems = CartHelper.getCartItems();
            for (CartHelper.CartItem item : cartItems) {
                JSONObject itemObj = new JSONObject();
                itemObj.put("product_id", item.product.optLong("id"));
                itemObj.put("quantity", item.quantity);
                itemObj.put("price", item.product.optDouble("price"));
                itemsArr.put(itemObj);
            }
            orderJson.put("items", itemsArr);

            RequestBody body = RequestBody.create(
                    orderJson.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = Constants.BACKEND_BASE_URL + "/api/orders";
            Request.Builder reqBuilder = new Request.Builder().url(url).post(body);
            if (token != null && !token.isEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer " + token);
            }
            Request request = reqBuilder.build();

            runOnUiThread(() -> Toast.makeText(CartActivity.this, "Placing your food order...", Toast.LENGTH_SHORT).show());

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(CartActivity.this, "Order failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    try {
                        JSONObject resJson = new JSONObject(res);
                        if ("success".equals(resJson.optString("status"))) {
                            long orderId = resJson.optLong("order_id");
                            runOnUiThread(() -> {
                                CartHelper.clearCart();
                                Toast.makeText(CartActivity.this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(CartActivity.this, OrderTrackingActivity.class);
                                intent.putExtra("order_id", orderId);
                                intent.putExtra("email", activeEmail);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            String msg = resJson.optString("message", "Could not complete check out");
                            runOnUiThread(() -> Toast.makeText(CartActivity.this, msg, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(CartActivity.this, "Checkout response parse error", Toast.LENGTH_LONG).show());
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_product, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CartHelper.CartItem item = CartHelper.getCartItems().get(position);
            String name = item.product.optString("name");
            double price = item.product.optDouble("price", 0.0);

            holder.nameText.setText(name);
            holder.priceText.setText("₹" + String.format("%.2f", price));
            holder.qtyText.setText(String.valueOf(item.quantity));

            holder.btnPlus.setOnClickListener(v -> {
                CartHelper.addItem(item.product, CartHelper.getCurrentVendorId(), CartHelper.getCurrentVendorName());
                notifyItemChanged(position);
                calculateTotals();
            });

            holder.btnMinus.setOnClickListener(v -> {
                CartHelper.removeItem(item.product);
                if (CartHelper.getCartCount() == 0 || CartHelper.getProductQuantity(item.product.optLong("id")) == 0) {
                    notifyDataSetChanged();
                } else {
                    notifyItemChanged(position);
                }
                calculateTotals();
            });
        }

        @Override
        public int getItemCount() {
            return CartHelper.getCartItems().size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText, priceText, qtyText, btnMinus, btnPlus;

            ViewHolder(View v) {
                super(v);
                nameText = v.findViewById(R.id.productName);
                priceText = v.findViewById(R.id.productPrice);
                qtyText = v.findViewById(R.id.qtyText);
                btnMinus = v.findViewById(R.id.btnMinus);
                btnPlus = v.findViewById(R.id.btnPlus);
            }
        }
    }
}
