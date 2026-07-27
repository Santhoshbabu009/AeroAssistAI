package com.aeroassist.ai;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CartHelper {

    public static class CartItem {
        public JSONObject product;
        public int quantity;

        public CartItem(JSONObject product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }

    private static List<CartItem> cartItems = new ArrayList<>();
    private static long currentVendorId = -1;
    private static String currentVendorName = "";

    public static List<CartItem> getCartItems() {
        return cartItems;
    }

    public static long getCurrentVendorId() {
        return currentVendorId;
    }

    public static String getCurrentVendorName() {
        return currentVendorName;
    }

    public static void addItem(JSONObject product, long vendorId, String vendorName) {
        if (currentVendorId != -1 && currentVendorId != vendorId) {
            // Vendor mismatch, clear cart first
            clearCart();
        }
        currentVendorId = vendorId;
        currentVendorName = vendorName;

        long productId = product.optLong("id");
        for (CartItem item : cartItems) {
            if (item.product.optLong("id") == productId) {
                item.quantity++;
                return;
            }
        }
        cartItems.add(new CartItem(product, 1));
    }

    public static void removeItem(JSONObject product) {
        long productId = product.optLong("id");
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            if (item.product.optLong("id") == productId) {
                item.quantity--;
                if (item.quantity <= 0) {
                    cartItems.remove(i);
                }
                break;
            }
        }
        if (cartItems.isEmpty()) {
            currentVendorId = -1;
            currentVendorName = "";
        }
    }

    public static int getProductQuantity(long productId) {
        for (CartItem item : cartItems) {
            if (item.product.optLong("id") == productId) {
                return item.quantity;
            }
        }
        return 0;
    }

    public static double getCartTotal() {
        double total = 0.0;
        for (CartItem item : cartItems) {
            total += item.product.optDouble("price", 0.0) * item.quantity;
        }
        return total;
    }

    public static int getCartCount() {
        int count = 0;
        for (CartItem item : cartItems) {
            count += item.quantity;
        }
        return count;
    }

    public static void clearCart() {
        cartItems.clear();
        currentVendorId = -1;
        currentVendorName = "";
    }
}
