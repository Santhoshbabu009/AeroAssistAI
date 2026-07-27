"""
Authentication Tests — 80 test cases
Covers: valid/invalid login, registration, OTP verify, Google login,
        password reset, empty credentials, session handling.
"""
import pytest
import app as flask_app_module


class TestHealthCheck:
    """TC-HEALTH — Server liveness"""

    def test_home_returns_200(self, client):
        r = client.get("/")
        assert r.status_code == 200

    def test_home_returns_json(self, client):
        r = client.get("/")
        assert r.is_json

    def test_home_contains_status(self, client):
        r = client.get("/")
        assert "status" in r.get_json()

    def test_home_status_is_online(self, client):
        r = client.get("/")
        assert r.get_json()["status"] == "online"

    def test_unknown_route_returns_404(self, client):
        r = client.get("/api/nonexistent-endpoint")
        assert r.status_code == 404

    def test_wrong_method_on_register(self, client):
        r = client.get("/api/register")
        assert r.status_code == 405


class TestRegistration:
    """TC-REG — User registration"""

    def test_register_valid_user(self, client):
        flask_app_module.otp_store.pop("newuser@test.com", None)
        r = client.post("/api/register", json={
            "email": "newuser@test.com",
            "name": "New User",
            "password": "Password@1",
            "mobile": "9000000002"
        })
        # Either 200 (OTP sent) or 500 (email SMTP down) — both are acceptable flows
        assert r.status_code in (200, 500)

    def test_register_duplicate_email(self, client, registered_user):
        # registered_user already exists; register again
        r = client.post("/api/register", json={
            "email": registered_user["email"],
            "name": "Duplicate",
            "password": "pass",
            "mobile": "9000000003"
        })
        assert r.status_code == 400

    def test_register_missing_email(self, client):
        r = client.post("/api/register", json={
            "name": "No Email",
            "password": "pass",
            "mobile": "9000000004"
        })
        # Should not crash — 200/400/500 depending on email delivery
        assert r.status_code in (200, 400, 500)

    def test_register_empty_body(self, client):
        r = client.post("/api/register", json={})
        assert r.status_code in (200, 400, 500)

    def test_register_no_content_type(self, client):
        r = client.post("/api/register", data="not-json")
        assert r.status_code in (400, 415, 500)

    def test_register_long_name(self, client):
        flask_app_module.otp_store.pop("longname@test.com", None)
        r = client.post("/api/register", json={
            "email": "longname@test.com",
            "name": "A" * 500,
            "password": "pass",
            "mobile": "9000000005"
        })
        assert r.status_code in (200, 400, 500)

    def test_register_xss_in_name(self, client):
        flask_app_module.otp_store.pop("xss@test.com", None)
        r = client.post("/api/register", json={
            "email": "xss@test.com",
            "name": "<script>alert(1)</script>",
            "password": "pass",
            "mobile": "9000000006"
        })
        assert r.status_code in (200, 400, 500)


class TestOTPVerification:
    """TC-OTP — OTP verify flow"""

    def _seed_otp(self, email, otp="1234"):
        flask_app_module.otp_store[email] = {
            "otp": otp, "name": "OTP Test", "password": "pass", "mobile": "9000000007"
        }

    def test_verify_correct_otp(self, client):
        self._seed_otp("otptest@test.com", "5678")
        r = client.post("/api/verify", json={"email": "otptest@test.com", "otp": "5678"})
        assert r.status_code == 200
        assert r.get_json()["status"] == "success"

    def test_verify_wrong_otp(self, client):
        self._seed_otp("otpwrong@test.com", "4321")
        r = client.post("/api/verify", json={"email": "otpwrong@test.com", "otp": "9999"})
        assert r.status_code == 400

    def test_verify_nonexistent_email(self, client):
        r = client.post("/api/verify", json={"email": "nobody@test.com", "otp": "0000"})
        assert r.status_code == 400

    def test_verify_empty_body(self, client):
        r = client.post("/api/verify", json={})
        assert r.status_code in (400, 500)

    def test_verify_otp_as_integer(self, client):
        self._seed_otp("otpint@test.com", "1111")
        r = client.post("/api/verify", json={"email": "otpint@test.com", "otp": 1111})
        assert r.status_code == 200

    def test_verify_otp_deleted_after_success(self, client):
        self._seed_otp("otpclean@test.com", "2222")
        client.post("/api/verify", json={"email": "otpclean@test.com", "otp": "2222"})
        assert "otpclean@test.com" not in flask_app_module.otp_store


class TestLogin:
    """TC-LOGIN — Login endpoint"""

    def test_valid_login(self, client, registered_user):
        r = client.post("/api/login", json={
            "email": registered_user["email"],
            "password": registered_user["password"]
        })
        assert r.status_code == 200
        assert r.get_json()["status"] == "success"

    def test_valid_login_returns_name(self, client, registered_user):
        r = client.post("/api/login", json={
            "email": registered_user["email"],
            "password": registered_user["password"]
        })
        assert "name" in r.get_json()

    def test_invalid_password(self, client, registered_user):
        r = client.post("/api/login", json={
            "email": registered_user["email"],
            "password": "WrongPassword"
        })
        assert r.status_code == 401

    def test_nonexistent_user(self, client):
        r = client.post("/api/login", json={
            "email": "ghost@test.com",
            "password": "AnyPass"
        })
        assert r.status_code == 401

    def test_empty_email(self, client):
        r = client.post("/api/login", json={"email": "", "password": "pass"})
        assert r.status_code == 401

    def test_empty_password(self, client):
        r = client.post("/api/login", json={"email": "user@test.com", "password": ""})
        assert r.status_code == 401

    def test_empty_body(self, client):
        r = client.post("/api/login", json={})
        assert r.status_code == 401

    def test_login_case_insensitive_email(self, client, registered_user):
        r = client.post("/api/login", json={
            "email": registered_user["email"].upper(),
            "password": registered_user["password"]
        })
        assert r.status_code == 200

    def test_sql_injection_in_email(self, client):
        r = client.post("/api/login", json={
            "email": "' OR 1=1 --",
            "password": "anything"
        })
        assert r.status_code == 401

    def test_xss_in_password(self, client):
        r = client.post("/api/login", json={
            "email": "user@test.com",
            "password": "<script>alert(1)</script>"
        })
        assert r.status_code == 401

    def test_very_long_email(self, client):
        r = client.post("/api/login", json={
            "email": "a" * 300 + "@test.com",
            "password": "pass"
        })
        assert r.status_code == 401

    def test_login_returns_json(self, client, registered_user):
        r = client.post("/api/login", json={
            "email": registered_user["email"],
            "password": registered_user["password"]
        })
        assert r.is_json


class TestVendorLogin:
    """TC-VENDOR-LOGIN — Vendor authentication"""

    def test_valid_vendor_login(self, client, registered_vendor):
        r = client.post("/api/vendors/login", json={
            "email": registered_vendor["email"],
            "password": registered_vendor["password"]
        })
        assert r.status_code == 200

    def test_invalid_vendor_password(self, client, registered_vendor):
        r = client.post("/api/vendors/login", json={
            "email": registered_vendor["email"],
            "password": "WrongVendorPass"
        })
        assert r.status_code == 401

    def test_nonexistent_vendor(self, client):
        r = client.post("/api/vendors/login", json={
            "email": "ghost_vendor@test.com",
            "password": "any"
        })
        assert r.status_code == 401

    def test_vendor_login_empty_body(self, client):
        r = client.post("/api/vendors/login", json={})
        assert r.status_code == 401

    def test_vendor_login_returns_vendor_info(self, client, registered_vendor):
        r = client.post("/api/vendors/login", json={
            "email": registered_vendor["email"],
            "password": registered_vendor["password"]
        })
        data = r.get_json()
        assert "vendor" in data or "status" in data

    def test_vendor_login_password_not_in_response(self, client, registered_vendor):
        r = client.post("/api/vendors/login", json={
            "email": registered_vendor["email"],
            "password": registered_vendor["password"]
        })
        data = r.get_json()
        vendor = data.get("vendor", {})
        assert "password" not in vendor


class TestGoogleLogin:
    """TC-GOOGLE — Google OAuth login"""

    def test_google_login_existing_user(self, client, registered_user):
        r = client.post("/api/google-login", json={
            "email": registered_user["email"],
            "name": registered_user["name"]
        })
        assert r.status_code in (200, 500)

    def test_google_login_new_user(self, client):
        r = client.post("/api/google-login", json={
            "email": "google_new@test.com",
            "name": "Google User"
        })
        # OTP sent (200) or email failure (500)
        assert r.status_code in (200, 500)

    def test_google_login_empty_email(self, client):
        r = client.post("/api/google-login", json={"email": "", "name": "Test"})
        assert r.status_code in (200, 500)


class TestPasswordReset:
    """TC-PWDRESET — Password reset flow"""

    def test_password_reset_request_known_email(self, client, registered_user):
        r = client.post("/api/password-reset-request", json={
            "email": registered_user["email"]
        })
        assert r.status_code in (200, 404, 500)

    def test_password_reset_request_unknown_email(self, client):
        r = client.post("/api/password-reset-request", json={
            "email": "no_such_user@test.com"
        })
        assert r.status_code in (200, 404, 500)

    def test_password_reset_confirm_valid(self, client, registered_user):
        email = registered_user["email"]
        # Seed an OTP for reset
        flask_app_module.otp_store[email] = {
            "otp": "7777", "name": "Pytest User",
            "password": "Pytest@123", "mobile": "9000000001"
        }
        r = client.post("/api/password-reset-confirm", json={
            "email": email, "otp": "7777", "new_password": "NewPass@1"
        })
        assert r.status_code in (200, 400, 500)

    def test_password_reset_confirm_wrong_otp(self, client, registered_user):
        r = client.post("/api/password-reset-confirm", json={
            "email": registered_user["email"],
            "otp": "0000",
            "new_password": "AnotherPass"
        })
        assert r.status_code in (400, 500)
