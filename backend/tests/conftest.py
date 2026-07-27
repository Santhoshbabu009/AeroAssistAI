"""
Shared pytest fixtures for the AeroAssist test suite.
Uses Flask's built-in test client — no live server needed.
"""
import sys
import os
import pytest
import sqlite3
import tempfile

# Ensure the backend module is importable
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

# Force SQLite mode so tests never touch cloud Supabase
os.environ.setdefault("USE_SQLITE_TEST", "1")


@pytest.fixture(scope="session")
def app():
    """Create a Flask test application with a fresh in-memory SQLite DB."""
    # Patch environment before importing app
    os.environ["SUPABASE_URL"] = "https://invalid.supabase.co"
    os.environ["SUPABASE_KEY"] = "dummy_key_for_tests"

    # Create a temp DB file for this session
    db_fd, db_path = tempfile.mkstemp(suffix=".db")
    os.close(db_fd)

    # Import the Flask app and override its DB path
    import importlib
    import app as flask_app_module

    # Mock email sender to prevent external network calls/timeouts
    flask_app_module.send_verification_email = lambda *args, **kwargs: True

    # Patch DB to use temp file
    flask_app_module.db = flask_app_module.LocalSQLiteDB(db_path=db_path)
    flask_app_module.USE_SQLITE = True
    flask_app_module.app.config["TESTING"] = True

    yield flask_app_module.app

    # Cleanup
    os.unlink(db_path)


@pytest.fixture(scope="session")
def client(app):
    """Flask test client for the full session."""
    return app.test_client()


@pytest.fixture(scope="session")
def db(app):
    """Direct access to the LocalSQLiteDB instance used by the running app."""
    import app as flask_app_module
    return flask_app_module.db


@pytest.fixture()
def registered_user(client, db):
    """Register + verify a test user and return their credentials."""
    import app as flask_app_module
    email = "pytest_user@test.com"
    existing = db.get_user(email)
    if existing:
        return {"email": email, "password": "Pytest@123", "name": "Pytest User"}
    # Inject OTP manually so we don't need email delivery
    flask_app_module.otp_store[email] = {
        "otp": "9999",
        "name": "Pytest User",
        "password": "Pytest@123",
        "mobile": "9000000001"
    }
    # Verify to persist
    try:
        client.post("/api/verify", json={"email": email, "otp": "9999"})
    except Exception:
        pass
    return {"email": email, "password": "Pytest@123", "name": "Pytest User"}


@pytest.fixture()
def registered_vendor(db):
    """Register a vendor directly in the DB and return credentials."""
    existing = db.get_vendor("pytest_vendor@test.com")
    if existing:
        return {
            "email": "pytest_vendor@test.com",
            "password": "VendorPass@1",
            "vendor_id": existing["id"]
        }
    try:
        vendor = db.register_vendor(
            "pytest_vendor@test.com", "VendorPass@1",
            "Pytest Cafe", "restaurant", "Terminal 1", "Gate 99", ""
        )
        return {
            "email": "pytest_vendor@test.com",
            "password": "VendorPass@1",
            "vendor_id": vendor["id"] if vendor else 1
        }
    except Exception:
        existing = db.get_vendor("pytest_vendor@test.com")
        return {
            "email": "pytest_vendor@test.com",
            "password": "VendorPass@1",
            "vendor_id": existing["id"] if existing else 1
        }


@pytest.fixture()
def user_headers(registered_user):
    from app import generate_token
    token = generate_token(registered_user["email"], role="user")
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def vendor_headers(registered_vendor):
    from app import generate_token
    token = generate_token(registered_vendor["email"], role="vendor")
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture()
def admin_headers():
    from app import generate_token
    token = generate_token("admin@aeroassist.com", role="admin")
    return {"Authorization": f"Bearer {token}"}

