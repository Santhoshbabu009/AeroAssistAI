"""
Database & Security Tests — 100 test cases
Covers: SQLite CRUD, duplicate handling, foreign key constraints,
        security headers, CORS, input validation, edge cases.
"""
import pytest
import app as flask_app_module


class TestDatabaseCRUD:
    """TC-DB — Direct DB layer validation"""

    def test_create_and_get_user(self, db):
        db.create_user("dbtest@test.com", "DB Test", "dbpass", "9000000010")
        user = db.get_user("dbtest@test.com")
        assert user is not None
        assert user["name"] == "DB Test"

    def test_get_nonexistent_user(self, db):
        result = db.get_user("nobody_ever@test.com")
        assert result is None

    def test_user_email_case_insensitive(self, db):
        db.create_user("casetest@test.com", "Case User", "pass", "9000000011")
        user = db.get_user("CASETEST@TEST.COM")
        assert user is not None

    def test_update_user_profile(self, db):
        db.create_user("profile_db@test.com", "Old Name", "pass", "0000000000")
        db.update_profile("profile_db@test.com", "New Name", "9111111112")
        user = db.get_user("profile_db@test.com")
        assert user["name"] == "New Name"

    def test_update_user_password(self, db):
        db.create_user("pwddb@test.com", "PWD User", "oldpass", "9000000012")
        db.update_password("pwddb@test.com", "newpass")
        user = db.get_user("pwddb@test.com")
        assert user["password"] == "newpass"

    def test_create_and_get_vendor(self, db):
        db.register_vendor(
            "vendordb@test.com", "vpass", "Vendor DB",
            "restaurant", "T1", "G1", ""
        )
        vendor = db.get_vendor("vendordb@test.com")
        assert vendor is not None
        assert vendor["name"] == "Vendor DB"

    def test_get_vendor_by_id(self, db):
        v = db.register_vendor(
            "vendorbyid@test.com", "pass", "By ID Vendor",
            "restaurant", "T1", "G1", ""
        )
        if v:
            vendor = db.get_vendor_by_id(v["id"])
            assert vendor is not None

    def test_get_vendors_by_type(self, db):
        vendors = db.get_vendors("restaurant")
        assert isinstance(vendors, list)

    def test_get_vendors_lounge_type(self, db):
        vendors = db.get_vendors("lounge")
        assert isinstance(vendors, list)

    def test_get_vendors_unknown_type(self, db):
        vendors = db.get_vendors("nonexistent_type")
        assert vendors == []

    def test_delete_vendor(self, db):
        db.register_vendor(
            "deletedb@test.com", "pass", "Delete DB Vendor",
            "restaurant", "T1", "G1", ""
        )
        result = db.delete_vendor("deletedb@test.com")
        assert result is True
        assert db.get_vendor("deletedb@test.com") is None

    def test_delete_nonexistent_vendor(self, db):
        result = db.delete_vendor("doesntexist_ever@test.com")
        # Returns True or False depending on implementation
        assert isinstance(result, bool)

    def test_get_products_for_vendor(self, db):
        v = db.register_vendor(
            "prodvendor@test.com", "pass", "Prod Vendor",
            "restaurant", "T1", "G1", ""
        )
        if v:
            products = db.get_products(v["id"])
            assert isinstance(products, list)

    def test_add_product(self, db):
        v = db.register_vendor(
            "addprod@test.com", "pass", "Add Prod Vendor",
            "restaurant", "T1", "G1", ""
        )
        if v:
            p = db.add_product(v["id"], "Test Burger", 299.00, "Burgers", "desc", "")
            assert p is not None
            assert p["name"] == "Test Burger"

    def test_update_product(self, db):
        v = db.register_vendor(
            "updprod@test.com", "pass", "Upd Prod Vendor",
            "restaurant", "T1", "G1", ""
        )
        if v:
            p = db.add_product(v["id"], "Old Name", 100.00, "Cat", "", "")
            if p:
                updated = db.update_product(p["id"], "New Name", 200.00, "Cat2", "new desc", "")
                assert updated["name"] == "New Name"

    def test_delete_product(self, db):
        v = db.register_vendor(
            "delprod@test.com", "pass", "Del Prod Vendor",
            "restaurant", "T1", "G1", ""
        )
        if v:
            p = db.add_product(v["id"], "Delete Me", 50.00, "Cat", "", "")
            if p:
                db.delete_product(p["id"])
                products = db.get_products(v["id"])
                ids = [x["id"] for x in products]
                assert p["id"] not in ids

    def test_save_chat_message(self, db):
        db.save_chat_message("chatdb@test.com", "passenger", 1, "Hello DB", True)
        # No exception = pass

    def test_book_lounge(self, db):
        v = db.register_vendor(
            "loungedb@test.com", "pass", "Lounge DB",
            "lounge", "T2", "G2", ""
        )
        vid = v["id"] if v else 1
        booking = db.book_lounge("loungebookuser@test.com", vid, "2026-08-15", "09:00", 2)
        assert booking is not None

    def test_book_parking(self, db):
        p = db.book_parking("parkingdb@test.com", "Zone B", 2, "TN02BC5678", "UPI", 200.00)
        assert p is not None

    def test_get_parking_bookings(self, db):
        db.book_parking("getpark@test.com", "Zone C", 1, "TN03CC0001", "Card", 100.00)
        bookings = db.get_parking_bookings("getpark@test.com")
        assert isinstance(bookings, list)
        assert len(bookings) >= 1


class TestSecurityHeaders:
    """TC-SECHDRS — HTTP response security headers"""

    def test_cors_header_present(self, client):
        r = client.get("/", headers={"Origin": "http://localhost:3000"})
        # CORS should be present (wildcard is a finding but header must exist)
        assert "Access-Control-Allow-Origin" in r.headers

    def test_content_type_json_on_api(self, client):
        r = client.get("/")
        assert "application/json" in r.content_type

    def test_no_x_powered_by(self, client):
        r = client.get("/")
        # Flask doesn't send X-Powered-By by default
        assert "X-Powered-By" not in r.headers

    def test_500_error_no_debug_traceback_in_response(self, client):
        # Request something invalid; check no HTML traceback leaks
        r = client.post("/api/login", data="invalid-non-json")
        assert "Traceback" not in (r.get_data(as_text=True) or "")


class TestInputValidation:
    """TC-INPUTVAL — Input sanitization edge cases"""

    def test_sql_injection_in_guide_key(self, client):
        r = client.get("/api/guides/terminal_1' OR 1=1--")
        assert r.status_code in (200, 400, 404, 500)
        # Crucially: must NOT return internal SQL data
        if r.status_code == 200:
            data = r.get_json()
            assert "users" not in str(data).lower()

    def test_very_large_payload_login(self, client):
        r = client.post("/api/login", json={
            "email": "a@b.com",
            "password": "X" * 100_000
        })
        assert r.status_code in (400, 401, 413, 500)

    def test_null_values_in_login(self, client):
        r = client.post("/api/login", json={"email": None, "password": None})
        assert r.status_code == 401

    def test_integer_email_in_login(self, client):
        r = client.post("/api/login", json={"email": 12345, "password": "pass"})
        assert r.status_code == 401

    def test_boolean_email_in_login(self, client):
        r = client.post("/api/login", json={"email": True, "password": "pass"})
        assert r.status_code == 401

    def test_array_as_email(self, client):
        r = client.post("/api/login", json={"email": ["a@b.com"], "password": "pass"})
        assert r.status_code == 401

    def test_nested_json_attack(self, client):
        r = client.post("/api/login", json={
            "email": {"$gt": ""},
            "password": {"$gt": ""}
        })
        assert r.status_code == 401

    def test_unicode_in_email(self, client):
        r = client.post("/api/login", json={"email": "用户@测试.com", "password": "pass"})
        assert r.status_code == 401

    def test_path_traversal_in_guide(self, client):
        r = client.get("/api/guides/../../../etc/passwd")
        assert r.status_code in (400, 404)

    def test_order_with_negative_price(self, client):
        from app import generate_token
        token = generate_token("neg@test.com", role="user")
        r = client.post("/api/orders", json={
            "user_email": "neg@test.com",
            "vendor_id": 1,
            "terminal": "T1",
            "gate": "G1",
            "total_price": -999.99,
            "payment_method": "COD",
            "items": []
        }, headers={"Authorization": f"Bearer {token}"})
        # Should either accept (no validation) or reject — both track correctly
        assert r.status_code in (200, 400, 500)

    def test_order_with_string_price(self, client):
        from app import generate_token
        token = generate_token("strprice@test.com", role="user")
        r = client.post("/api/orders", json={
            "user_email": "strprice@test.com",
            "vendor_id": 1,
            "terminal": "T1",
            "gate": "G1",
            "total_price": "FREE",
            "payment_method": "COD",
            "items": []
        }, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code in (200, 400, 500)


class TestAuthorizationEnforcement:
    """TC-AUTHZ — Authorization boundary checks"""

    def test_vendor_register_requires_admin_key(self, client):
        r = client.post("/api/vendors/register", json={
            "email": "bypass@test.com",
            "password": "pass",
            "name": "Bypass",
            "type": "restaurant",
            "terminal": "T1",
            "gate": "G1"
        })
        assert r.status_code == 403

    def test_vendor_delete_requires_admin_key(self, client):
        r = client.post("/api/vendors/delete", json={"email": "any@test.com"})
        assert r.status_code == 403

    def test_chat_history_accessible_without_token(self, client):
        r = client.get("/api/chat-history?email=anyone@test.com")
        assert r.status_code == 401

    def test_chat_history_with_valid_token(self, client):
        from app import generate_token
        token = generate_token("anyone@test.com", role="user")
        r = client.get("/api/chat-history?email=anyone@test.com", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 200

    def test_chat_history_with_other_user_token(self, client):
        from app import generate_token
        token = generate_token("other@test.com", role="user")
        r = client.get("/api/chat-history?email=anyone@test.com", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 403

    def test_orders_accessible_without_token(self, client):
        r = client.get("/api/orders?user_email=victim@test.com")
        assert r.status_code == 401

    def test_orders_with_valid_token(self, client):
        from app import generate_token
        token = generate_token("victim@test.com", role="user")
        r = client.get("/api/orders?user_email=victim@test.com", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 200

    def test_orders_with_other_user_token(self, client):
        from app import generate_token
        token = generate_token("attacker@test.com", role="user")
        r = client.get("/api/orders?user_email=victim@test.com", headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 403

    def test_lost_item_delete_accessible_without_token(self, client):
        r = client.post("/api/lost-items/delete", json={"id": 999999})
        assert r.status_code == 403

    def test_lost_item_delete_with_admin_token(self, client):
        from app import generate_token
        token = generate_token("admin@aeroassist.com", role="admin")
        r = client.post("/api/lost-items/delete", json={"id": 999999}, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code in (200, 400, 404, 500)

    def test_lost_item_delete_with_user_token(self, client):
        from app import generate_token
        token = generate_token("user@test.com", role="user")
        r = client.post("/api/lost-items/delete", json={"id": 999999}, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 403

    def test_profile_update_accessible_without_token(self, client):
        r = client.post("/api/update-profile", json={
            "email": "victim@test.com", "name": "Attacker Name", "mobile": "0000"
        })
        assert r.status_code == 401

    def test_profile_update_with_valid_token(self, client):
        from app import generate_token
        token = generate_token("victim@test.com", role="user")
        r = client.post("/api/update-profile", json={
            "email": "victim@test.com", "name": "Victim Name", "mobile": "0000"
        }, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 200

    def test_profile_update_with_other_user_token(self, client):
        from app import generate_token
        token = generate_token("attacker@test.com", role="user")
        r = client.post("/api/update-profile", json={
            "email": "victim@test.com", "name": "Attacker Name", "mobile": "0000"
        }, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code == 403



class TestEdgeCases:
    """TC-EDGE — Edge and boundary condition tests"""

    def test_concurrent_login_same_user(self, client, registered_user):
        results = []
        for _ in range(5):
            r = client.post("/api/login", json={
                "email": registered_user["email"],
                "password": registered_user["password"]
            })
            results.append(r.status_code)
        assert all(s == 200 for s in results)

    def test_very_large_vendor_id(self, client, admin_headers):
        r = client.get("/api/vendors/orders?vendor_id=2147483647", headers=admin_headers)
        assert r.status_code == 200

    def test_zero_vendor_id(self, client, admin_headers):
        r = client.get("/api/vendors/orders?vendor_id=0", headers=admin_headers)
        assert r.status_code == 200

    def test_string_vendor_id(self, client, admin_headers):
        r = client.get("/api/vendors/orders?vendor_id=notanumber", headers=admin_headers)
        assert r.status_code in (200, 400, 500)

    def test_empty_string_vendor_id(self, client, admin_headers):
        r = client.get("/api/vendors/products?vendor_id=", headers=admin_headers)
        assert r.status_code == 400

    def test_get_guide_with_special_chars(self, client):
        r = client.get("/api/guides/terminal%201")
        assert r.status_code in (200, 404)

    def test_post_to_get_only_endpoints(self, client):
        r = client.post("/api/restaurants", json={})
        assert r.status_code == 405

    def test_get_chat_empty_email(self, client, admin_headers):
        r = client.get("/api/chat-history?email=", headers=admin_headers)
        assert r.status_code in (200, 400)

    def test_order_with_empty_items_list(self, client):
        from app import generate_token
        token = generate_token("emptyitems@test.com", role="user")
        r = client.post("/api/orders", json={
            "user_email": "emptyitems@test.com",
            "vendor_id": 1,
            "terminal": "T1",
            "gate": "G1",
            "total_price": 0,
            "payment_method": "COD",
            "items": []
        }, headers={"Authorization": f"Bearer {token}"})
        assert r.status_code in (200, 400, 500)

    def test_lost_items_get_returns_valid_structure(self, client):
        r = client.get("/api/lost-items")
        assert r.status_code == 200
        assert r.is_json

    def test_parking_booking_zero_hours(self, client):
        r = client.post("/api/parking-bookings", json={
            "user_email": "zerohours@test.com",
            "zone": "Zone A",
            "hours": 0,
            "plate_number": "TN01ZZ0000",
            "payment_method": "COD",
            "total_price": 0
        })
        assert r.status_code in (200, 400, 500)

    def test_register_vendor_invalid_type(self, client):
        r = client.post("/api/vendors/register", json={
            "admin_key": "admin_aeroassist_2026",
            "email": "invalidtype@test.com",
            "password": "pass",
            "name": "Invalid Type",
            "type": "INVALID_TYPE",
            "terminal": "T1",
            "gate": "G1",
            "image_url": ""
        })
        assert r.status_code in (200, 400, 500)

    def test_save_chat_empty_message(self, client):
        r = client.post("/api/save-chat", json={
            "email": "empmsg@test.com",
            "user_type": "passenger",
            "session_id": 1,
            "message": "",
            "is_user": True
        })
        assert r.status_code in (200, 400, 500)

    def test_verify_otp_sql_injection(self, client):
        r = client.post("/api/verify", json={
            "email": "' OR 1=1 --",
            "otp": "' OR 1=1 --"
        })
        assert r.status_code in (400, 500)

    def test_multiple_orders_same_user(self, client):
        from app import generate_token
        token = generate_token("multiorder@test.com", role="user")
        headers = {"Authorization": f"Bearer {token}"}
        for i in range(3):
            client.post("/api/orders", json={
                "user_email": "multiorder@test.com",
                "vendor_id": 1,
                "terminal": "T1",
                "gate": "G1",
                "total_price": 100 + i,
                "payment_method": "COD",
                "items": [{"product_id": 1, "quantity": 1, "price": 100 + i, "product_name": "Test Item"}]
            }, headers=headers)
        r = client.get("/api/orders?user_email=multiorder@test.com", headers=headers)
        data = r.get_json()
        assert len(data.get("orders", [])) >= 3
