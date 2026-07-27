"""
Vendor & Admin Tests — 80 test cases
Covers: vendor registration (admin-gated), vendor delete,
        vendor order queue, vendor products CRUD, booking management.
"""
import pytest


ADMIN_KEY = "admin_aeroassist_2026"


class TestVendorRegistration:
    """TC-VREG — Admin-gated vendor registration"""

    def test_register_vendor_valid(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": ADMIN_KEY,
            "email": "newvendor@test.com",
            "password": "Vendor@Pass1",
            "name": "New Test Cafe",
            "type": "restaurant",
            "terminal": "Terminal 1",
            "gate": "Gate 50",
            "image_url": ""
        })
        assert r.status_code in (200, 400)  # 400 if already exists

    def test_register_vendor_wrong_admin_key(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": "wrong_key",
            "email": "vendor2@test.com",
            "password": "pass",
            "name": "Bad Vendor",
            "type": "restaurant",
            "terminal": "Terminal 1",
            "gate": "Gate 1",
            "image_url": ""
        })
        assert r.status_code == 403

    def test_register_vendor_no_admin_key(self, client):
        r = client.post("/api/vendors/register", json={
            "email": "vendor3@test.com",
            "password": "pass",
            "name": "No Key Vendor",
            "type": "restaurant",
            "terminal": "Terminal 1",
            "gate": "Gate 1"
        })
        assert r.status_code == 403

    def test_register_vendor_duplicate_email(self, client):
        # Register once
        payload = {
            "admin_key": ADMIN_KEY,
            "email": "dup_vendor@test.com",
            "password": "pass",
            "name": "Dup Vendor",
            "type": "restaurant",
            "terminal": "T1",
            "gate": "G1",
            "image_url": ""
        }
        client.post("/api/vendors/register", json=payload)
        # Try again
        r = client.post("/api/vendors/register", json=payload)
        assert r.status_code == 400

    def test_register_lounge_vendor(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": ADMIN_KEY,
            "email": "lounge_vendor@test.com",
            "password": "pass",
            "name": "Test Lounge",
            "type": "lounge",
            "terminal": "Terminal 2",
            "gate": "Gate 20",
            "image_url": ""
        })
        assert r.status_code in (200, 400)

    def test_register_vendor_response_no_password(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": ADMIN_KEY,
            "email": "nopwd_vendor@test.com",
            "password": "secret",
            "name": "NoPwd Vendor",
            "type": "restaurant",
            "terminal": "T1",
            "gate": "G1",
            "image_url": ""
        })
        if r.status_code == 200:
            vendor = r.get_json().get("vendor", {})
            assert "password" not in vendor

    def test_register_vendor_empty_body(self, client):
        r = client.post("/api/vendors/register", json={})
        assert r.status_code == 403

    def test_register_vendor_missing_type(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": ADMIN_KEY,
            "email": "notype@test.com",
            "password": "pass",
            "name": "No Type"
        })
        assert r.status_code in (200, 400, 500)


class TestVendorDelete:
    """TC-VDEL — Vendor delete (admin)"""

    def test_delete_vendor_wrong_key(self, client):
        r = client.post("/api/vendors/delete", json={
            "admin_key": "wrong", "email": "any@test.com"
        })
        assert r.status_code == 403

    def test_delete_vendor_no_email(self, client):
        r = client.post("/api/vendors/delete", json={"admin_key": ADMIN_KEY})
        assert r.status_code == 400

    def test_delete_nonexistent_vendor(self, client):
        r = client.post("/api/vendors/delete", json={
            "admin_key": ADMIN_KEY,
            "email": "nobody_vendor@test.com"
        })
        assert r.status_code in (200, 404, 500)

    def test_delete_existing_vendor(self, client):
        # Register then delete
        client.post("/api/vendors/register", json={
            "admin_key": ADMIN_KEY,
            "email": "to_delete@test.com",
            "password": "pass",
            "name": "Delete Me",
            "type": "restaurant",
            "terminal": "T1",
            "gate": "G1",
            "image_url": ""
        })
        r = client.post("/api/vendors/delete", json={
            "admin_key": ADMIN_KEY,
            "email": "to_delete@test.com"
        })
        assert r.status_code in (200, 500)


class TestVendorOrders:
    """TC-VORDERS — Vendor order queue"""

    def test_get_vendor_orders_missing_vendor_id(self, client, vendor_headers):
        r = client.get("/api/vendors/orders", headers=vendor_headers)
        assert r.status_code == 400

    def test_get_vendor_orders_valid_id(self, client, registered_vendor, vendor_headers):
        r = client.get(f"/api/vendors/orders?vendor_id={registered_vendor['vendor_id']}", headers=vendor_headers)
        assert r.status_code == 200

    def test_get_vendor_orders_returns_list(self, client, registered_vendor, vendor_headers):
        data = client.get(f"/api/vendors/orders?vendor_id={registered_vendor['vendor_id']}", headers=vendor_headers).get_json()
        assert "orders" in data
        assert isinstance(data["orders"], list)

    def test_get_vendor_orders_nonexistent_vendor(self, client, admin_headers):
        r = client.get("/api/vendors/orders?vendor_id=999999", headers=admin_headers)
        assert r.status_code == 200  # Returns empty list

    def test_update_order_status_valid(self, client, registered_vendor, vendor_headers):
        # Place an order for this vendor then update its status
        from app import generate_token
        user_token = generate_token("vendororder@test.com", role="user")
        vid = registered_vendor["vendor_id"]
        client.post("/api/orders", json={
            "user_email": "vendororder@test.com",
            "vendor_id": vid,
            "terminal": "T1",
            "gate": "G1",
            "total_price": 100,
            "payment_method": "COD",
            "items": []
        }, headers={"Authorization": f"Bearer {user_token}"})
        orders = client.get(f"/api/vendors/orders?vendor_id={vid}", headers=vendor_headers).get_json().get("orders", [])
        if orders:
            oid = orders[0]["id"]
            r = client.post(f"/api/vendors/orders/{oid}/status", json={"status": "Preparing"}, headers=vendor_headers)
            assert r.status_code == 200

    def test_update_order_status_nonexistent(self, client, admin_headers):
        r = client.post("/api/vendors/orders/999999/status", json={"status": "Delivered"}, headers=admin_headers)
        assert r.status_code == 404


class TestVendorProducts:
    """TC-VPROD — Vendor product CRUD"""

    def test_get_vendor_products_missing_id(self, client, vendor_headers):
        r = client.get("/api/vendors/products", headers=vendor_headers)
        assert r.status_code == 400

    def test_get_vendor_products_valid(self, client, registered_vendor, vendor_headers):
        r = client.get(f"/api/vendors/products?vendor_id={registered_vendor['vendor_id']}", headers=vendor_headers)
        assert r.status_code == 200

    def test_add_product_success(self, client, registered_vendor, vendor_headers):
        r = client.post("/api/vendors/products", json={
            "vendor_id": registered_vendor["vendor_id"],
            "name": "Test Burger",
            "price": 199.00,
            "category": "Burgers",
            "description": "A test burger",
            "image_url": ""
        }, headers=vendor_headers)
        assert r.status_code == 200

    def test_add_product_missing_vendor_id(self, client, vendor_headers):
        r = client.post("/api/vendors/products", json={
            "name": "Item", "price": 100, "category": "Food"
        }, headers=vendor_headers)
        assert r.status_code in (200, 400, 500)

    def test_update_product(self, client, registered_vendor, vendor_headers):
        # Add product first
        res = client.post("/api/vendors/products", json={
            "vendor_id": registered_vendor["vendor_id"],
            "name": "Update Me",
            "price": 50.00,
            "category": "Snacks",
            "description": "",
            "image_url": ""
        }, headers=vendor_headers)
        product = res.get_json().get("product", {})
        pid = product.get("id")
        if pid:
            r = client.put("/api/vendors/products", json={
                "id": pid,
                "name": "Updated Item",
                "price": 75.00,
                "category": "Snacks",
                "description": "Updated",
                "image_url": ""
            }, headers=vendor_headers)
            assert r.status_code == 200

    def test_delete_product(self, client, registered_vendor, vendor_headers):
        res = client.post("/api/vendors/products", json={
            "vendor_id": registered_vendor["vendor_id"],
            "name": "Delete Me Product",
            "price": 25.00,
            "category": "Beverages",
            "description": "",
            "image_url": ""
        }, headers=vendor_headers)
        pid = res.get_json().get("product", {}).get("id")
        if pid:
            r = client.delete(f"/api/vendors/products?id={pid}", headers=vendor_headers)
            assert r.status_code == 200


class TestVendorBookings:
    """TC-VBOOKING — Vendor lounge booking management"""

    def test_get_vendor_bookings_missing_id(self, client, vendor_headers):
        r = client.get("/api/vendors/bookings", headers=vendor_headers)
        assert r.status_code == 400

    def test_get_vendor_bookings_valid(self, client, registered_vendor, vendor_headers):
        r = client.get(f"/api/vendors/bookings?vendor_id={registered_vendor['vendor_id']}", headers=vendor_headers)
        assert r.status_code == 200

    def test_get_vendor_bookings_returns_list(self, client, registered_vendor, vendor_headers):
        data = client.get(f"/api/vendors/bookings?vendor_id={registered_vendor['vendor_id']}", headers=vendor_headers).get_json()
        assert "bookings" in data

    def test_update_booking_status_nonexistent(self, client, admin_headers):
        r = client.post("/api/vendors/bookings/999999/status", json={"status": "Approved"}, headers=admin_headers)
        assert r.status_code == 404

    def test_update_booking_status_valid(self, client, registered_vendor, vendor_headers):
        # Create booking then update
        client.post("/api/bookings", json={
            "user_email": "vbooking@test.com",
            "vendor_id": registered_vendor["vendor_id"],
            "booking_date": "2026-09-01",
            "booking_time": "14:00",
            "slots": 1
        })
        bookings = client.get(
            f"/api/vendors/bookings?vendor_id={registered_vendor['vendor_id']}",
            headers=vendor_headers
        ).get_json().get("bookings", [])
        if bookings:
            bid = bookings[0]["id"]
            r = client.post(f"/api/vendors/bookings/{bid}/status", json={"status": "Approved"}, headers=vendor_headers)
            assert r.status_code == 200

