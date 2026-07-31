from flask import Flask, request, jsonify, send_from_directory, abort
from flask_cors import CORS
import random
try:
    from supabase import create_client, Client
except ImportError:
    create_client = None
    Client = None
import os
import sqlite3
import re
import jwt
import datetime
import time
from functools import wraps
from werkzeug.security import generate_password_hash, check_password_hash
try:
    from flask_limiter import Limiter
    from flask_limiter.util import get_remote_address
except ImportError:
    Limiter = None
    get_remote_address = None

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass

from werkzeug.routing import BaseConverter

class RegexConverter(BaseConverter):
    def __init__(self, url_map, *items):
        super().__init__(url_map)
        self.regex = items[0]

app = Flask(__name__)
app.url_map.converters['regex'] = RegexConverter

# Configure CORS for all origins (production Vercel & local development)
CORS(app, resources={r"/*": {"origins": "*"}})

# Configure rate limiting
is_testing = os.environ.get("USE_SQLITE_TEST") == "1" or os.environ.get("TESTING") == "true" or os.environ.get("FLASK_ENV") == "testing" or os.environ.get("DISABLE_RATE_LIMIT") == "1"
if is_testing:
    app.config['RATELIMIT_ENABLED'] = False

if Limiter is not None and get_remote_address is not None:
    limiter = Limiter(
        key_func=get_remote_address,
        default_limits=[] if is_testing else ["200 per day", "50 per hour"],
        storage_uri="memory://",
        enabled=not is_testing
    )
    limiter.init_app(app)
else:
    class DummyLimiter:
        def limit(self, *args, **kwargs):
            def decorator(f):
                return f
            return decorator
    limiter = DummyLimiter()

JWT_SECRET = os.environ.get("JWT_SECRET")
if not JWT_SECRET:
    if os.environ.get("USE_SQLITE_TEST") == "1" or os.environ.get("TESTING") == "true":
        JWT_SECRET = "test-only-dummy-secret-key"
    else:
        raise RuntimeError("JWT_SECRET environment variable is not set!")

ADMIN_SECRET_KEY = os.environ.get("ADMIN_SECRET_KEY")
if not ADMIN_SECRET_KEY:
    if os.environ.get("USE_SQLITE_TEST") == "1" or os.environ.get("TESTING") == "true":
        ADMIN_SECRET_KEY = "test-only-admin-key"
    else:
        raise RuntimeError("ADMIN_SECRET_KEY environment variable is not set!")

def generate_token(email, role="user"):
    payload = {
        "sub": email,
        "role": role,
        "exp": datetime.datetime.utcnow() + datetime.timedelta(hours=24)
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")

def decode_token(token):
    try:
        payload = jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
        return payload
    except Exception:
        return None

def check_password(stored, input_pwd):
    if not stored or not input_pwd:
        return False
    if stored.startswith(('pbkdf2:sha256:', 'scrypt:', 'sha256:')):
        return check_password_hash(stored, input_pwd)
    return stored == input_pwd

def token_required(role=None):
    def decorator(f):
        @wraps(f)
        def decorated(*args, **kwargs):
            token = None
            if 'Authorization' in request.headers:
                auth_header = request.headers['Authorization']
                if auth_header.startswith('Bearer '):
                    token = auth_header.split(" ")[1]
            
            if not token:
                return jsonify({"status": "error", "message": "Missing or invalid token"}), 401
                
            payload = decode_token(token)
            if not payload:
                return jsonify({"status": "error", "message": "Missing or invalid token"}), 401
                
            if role:
                if isinstance(role, list):
                    if payload.get('role') not in role:
                        return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403
                elif payload.get('role') != role:
                    return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403
            
            request.user_email = payload.get('sub')
            request.user_role = payload.get('role')
            return f(*args, **kwargs)
        return decorated
    return decorator

def sanitize_vendor(vendor):
    if not vendor:
        return vendor
    v = dict(vendor) if not isinstance(vendor, dict) else vendor.copy()
    v.pop('password', None)
    v.pop('password_hash', None)
    return v

def sanitize_user(user):
    if not user:
        return user
    u = dict(user) if not isinstance(user, dict) else user.copy()
    u.pop('password', None)
    u.pop('password_hash', None)
    return u

# --- LOCAL SQLITE PERSISTENT STORAGE FALLBACK LAYER ---
class LocalSQLiteDB:
    def __init__(self, db_path=None):
        if db_path is None:
            db_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'aeroassist.db')
        self.db_path = db_path
        self.init_db()

    def get_conn(self):
        conn = sqlite3.connect(self.db_path, timeout=30.0)
        conn.row_factory = sqlite3.Row
        return conn

    def init_db(self):
        conn = self.get_conn()
        cursor = conn.cursor()
        
        # users table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS users (
                email TEXT PRIMARY KEY,
                uid TEXT,
                name TEXT NOT NULL,
                password TEXT NOT NULL,
                mobile TEXT,
                profile_photo TEXT,
                nationality TEXT DEFAULT 'Indian',
                preferred_language TEXT DEFAULT 'en',
                account_type TEXT DEFAULT 'Passenger',
                security_preferences TEXT DEFAULT '{}',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("PRAGMA table_info(users)")
        u_cols = [row['name'] for row in cursor.fetchall()]
        for col_name, col_type in [
            ('profile_photo', 'TEXT DEFAULT NULL'),
            ('uid', 'TEXT DEFAULT NULL'),
            ('nationality', "TEXT DEFAULT 'Indian'"),
            ('preferred_language', "TEXT DEFAULT 'en'"),
            ('account_type', "TEXT DEFAULT 'Passenger'"),
            ('security_preferences', "TEXT DEFAULT '{}'")
        ]:
            if col_name not in u_cols:
                try:
                    cursor.execute(f"ALTER TABLE users ADD COLUMN {col_name} {col_type}")
                    conn.commit()
                except Exception:
                    pass
        
        # vendors table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS vendors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                terminal TEXT NOT NULL,
                gate TEXT NOT NULL,
                rating REAL DEFAULT 5.0,
                image_url TEXT,
                availability TEXT DEFAULT 'Available'
            )
        """)
        
        # products table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                vendor_id INTEGER,
                name TEXT NOT NULL,
                price REAL NOT NULL,
                rating REAL DEFAULT 5.0,
                image_url TEXT,
                category TEXT NOT NULL,
                description TEXT,
                FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE CASCADE
            )
        """)
        
        # orders table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                booking_id TEXT,
                user_email TEXT NOT NULL,
                vendor_id INTEGER,
                terminal TEXT NOT NULL,
                gate TEXT NOT NULL,
                pickup_counter TEXT,
                status TEXT DEFAULT 'Pending',
                total_price REAL NOT NULL,
                payment_method TEXT DEFAULT 'COD',
                payment_status TEXT DEFAULT 'Completed',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE CASCADE
            )
        """)
        cursor.execute("PRAGMA table_info(orders)")
        o_cols = [row['name'] for row in cursor.fetchall()]
        for col_name, col_type in [
            ('booking_id', 'TEXT DEFAULT NULL'),
            ('pickup_counter', 'TEXT DEFAULT NULL'),
            ('payment_status', "TEXT DEFAULT 'Completed'")
        ]:
            if col_name not in o_cols:
                try:
                    cursor.execute(f"ALTER TABLE orders ADD COLUMN {col_name} {col_type}")
                    conn.commit()
                except Exception:
                    pass
        
        # order_items table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER,
                product_id INTEGER,
                quantity INTEGER NOT NULL,
                price REAL NOT NULL,
                product_name TEXT NOT NULL,
                FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
            )
        """)
        
        # lounge_bookings table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS lounge_bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_email TEXT NOT NULL,
                vendor_id INTEGER,
                booking_date TEXT NOT NULL,
                booking_time TEXT NOT NULL,
                slots INTEGER NOT NULL,
                status TEXT DEFAULT 'Pending',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE CASCADE
            )
        """)

        # parking_bookings table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS parking_bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                booking_id TEXT,
                user_email TEXT NOT NULL,
                zone TEXT NOT NULL,
                slot_number TEXT,
                terminal TEXT DEFAULT 'Terminal 1',
                duration_hours INTEGER DEFAULT 2,
                entry_time TEXT,
                exit_time TEXT,
                hours INTEGER NOT NULL,
                plate_number TEXT NOT NULL,
                payment_method TEXT NOT NULL,
                total_price REAL NOT NULL,
                status TEXT DEFAULT 'Confirmed',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        cursor.execute("PRAGMA table_info(parking_bookings)")
        p_cols = [row['name'] for row in cursor.fetchall()]
        for col_name, col_type in [
            ('booking_id', 'TEXT DEFAULT NULL'),
            ('slot_number', 'TEXT DEFAULT NULL'),
            ('terminal', "TEXT DEFAULT 'Terminal 1'"),
            ('duration_hours', 'INTEGER DEFAULT 2'),
            ('entry_time', 'TEXT DEFAULT NULL'),
            ('exit_time', 'TEXT DEFAULT NULL')
        ]:
            if col_name not in p_cols:
                try:
                    cursor.execute(f"ALTER TABLE parking_bookings ADD COLUMN {col_name} {col_type}")
                    conn.commit()
                except Exception:
                    pass

        # flight_bookings table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS flight_bookings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_email TEXT NOT NULL,
                booking_id TEXT NOT NULL UNIQUE,
                pnr TEXT NOT NULL,
                payment_id TEXT NOT NULL,
                transaction_id TEXT NOT NULL,
                ticket_number TEXT NOT NULL,
                invoice_number TEXT NOT NULL,
                origin TEXT NOT NULL,
                destination TEXT NOT NULL,
                departure_date TEXT NOT NULL,
                flight_details TEXT NOT NULL,
                passenger_details TEXT NOT NULL,
                amount REAL NOT NULL,
                payment_method TEXT NOT NULL,
                payment_status TEXT DEFAULT 'Demo Success',
                booking_status TEXT DEFAULT 'Confirmed',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # flight_seats central inventory table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS flight_seats (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                flight_number TEXT NOT NULL,
                departure_date TEXT NOT NULL,
                seat_number TEXT NOT NULL,
                is_booked INTEGER DEFAULT 1,
                booked_by TEXT NOT NULL,
                booking_id TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(flight_number, departure_date, seat_number)
            )
        """)

        # lost_items table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS lost_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                category TEXT DEFAULT 'General',
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                contact TEXT NOT NULL,
                type TEXT DEFAULT 'Lost',
                icon TEXT DEFAULT '📦',
                image TEXT DEFAULT NULL,
                reporter_name TEXT DEFAULT 'Anonymous',
                status TEXT DEFAULT 'Pending',
                user_email TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        
        # Schema migration check
        cursor.execute("PRAGMA table_info(lost_items)")
        columns = [row['name'] for row in cursor.fetchall()]
        for col_name, col_type in [
            ('type', "TEXT DEFAULT 'Lost'"),
            ('image', 'TEXT DEFAULT NULL'),
            ('category', "TEXT DEFAULT 'General'"),
            ('reporter_name', "TEXT DEFAULT 'Anonymous'"),
            ('status', "TEXT DEFAULT 'Pending'"),
            ('user_email', 'TEXT DEFAULT NULL')
        ]:
            if col_name not in columns:
                try:
                    cursor.execute(f"ALTER TABLE lost_items ADD COLUMN {col_name} {col_type}")
                    conn.commit()
                except Exception:
                    pass
        
        cursor.execute("SELECT COUNT(*) FROM lost_items")
        if cursor.fetchone()[0] == 0:
            cursor.execute("""
                INSERT INTO lost_items (name, category, description, location, contact, type, icon, reporter_name, status) VALUES
                ('iPhone 13 Pro', 'Electronics', 'Blue case', 'Gate 14', '+1234567890', 'Lost', '📱', 'Santhosh Babu', 'Pending'),
                ('Leather Wallet', 'Personal Items', 'Brown', 'Terminal 2', '+1234567890', 'Lost', '👛', 'Rahul Sharma', 'Pending'),
                ('MacBook Air', 'Electronics', 'Silver', 'Food Court', '+1234567890', 'Lost', '💻', 'Priya Singh', 'Pending'),
                ('Spectacles', 'Accessories', 'RayBan', 'Lounge 1', '+1234567890', 'Lost', '👓', 'Alex Vance', 'Resolved')
            """)
            conn.commit()

        # wallet table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS wallet (
                user_email TEXT PRIMARY KEY,
                balance REAL DEFAULT 500.0,
                transactions TEXT DEFAULT '[]'
            )
        """)

        # quiz_scores table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS quiz_scores (
                user_email TEXT PRIMARY KEY,
                user_name TEXT,
                score INTEGER DEFAULT 0,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # notifications table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_email TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                type TEXT DEFAULT 'info',
                is_read INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # chat_history table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT NOT NULL,
                user_type TEXT NOT NULL,
                session_id INTEGER,
                message TEXT NOT NULL,
                is_user BOOLEAN NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)
        # guides table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS guides (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                key TEXT UNIQUE NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL
            )
        """)
        conn.commit()

        # Seed guides if empty
        cursor.execute("SELECT COUNT(*) FROM guides")
        if cursor.fetchone()[0] == 0:
            cursor.execute("""
                INSERT INTO guides (key, title, content) VALUES
                ('terminal_1', 'Terminal 1', 'Terminal 1 primarily handles domestic operations. It consists of three levels:

• Level 1: Arrivals and Baggage Claim.
• Level 2: Departures and Security Check.
• Level 3: Lounges and Food Court.

Gates A1 to A20 are located in this terminal. Walking time from security to the farthest gate is approximately 12 minutes.'),
                ('terminal_2', 'Terminal 2', 'Terminal 2 is the main hub for international flights. It features state-of-the-art architecture and premium services.

• Level 1: Ground Transportation & International Arrivals.
• Level 2: Duty-Free Shopping & Boarding Gates.
• Level 3: Premium Lounges & Fine Dining.

Gates B1 to B50 are located here. Automated People Movers (APM) connect different zones within the terminal.'),
                ('transfer_guide', 'Inter-Terminal Transfers', '• Free Shuttle Bus: Operates every 10 minutes between T1 and T2. Follow signs for "Terminal Shuttle".

• Airside Transfer: If you have a connecting flight, use the airside transfer bus to avoid re-clearing immigration.

• Walking Path: A covered walkway connects T1 and T2 (approx. 15 mins walk).

• Buggy Service: Elderly and disabled passengers can request a buggy at the information desks.

• Luggage: If your bags are not checked through, you must collect them before transferring between terminals.')
            """)
            conn.commit()
        
        conn.commit()
        
        # Seed default vendors if empty
        cursor.execute("SELECT COUNT(*) FROM vendors")
        if cursor.fetchone()[0] == 0:
            cursor.execute("""
                INSERT INTO vendors (email, password, name, type, terminal, gate, rating, image_url, availability) VALUES
                ('bk@airport.com', 'vendor123', 'Burger King', 'restaurant', 'Terminal 1', 'Gate 9', 4.2, 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500', 'Available'),
                ('starbucks@airport.com', 'vendor123', 'Starbucks Coffee', 'restaurant', 'Terminal 1', 'Gate 14', 4.5, 'https://images.unsplash.com/photo-1544787219-7f47ccb76574?w=500', 'Available'),
                ('greatkabab@airport.com', 'vendor123', 'The Great Kabab Factory', 'restaurant', 'Terminal 2', 'Gate 25', 4.7, 'https://images.unsplash.com/photo-1603360946369-dc9bb6258143?w=500', 'Available'),
                ('plaza@airport.com', 'vendor123', 'Plaza Premium Lounge', 'lounge', 'Terminal 1', 'Near Gate 12', 4.8, 'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=500', 'Available'),
                ('airindia@airport.com', 'vendor123', 'Air India Lounge', 'lounge', 'Terminal 2', 'Near Gate 18', 4.1, 'https://images.unsplash.com/photo-1582719508461-905c673771fd?w=500', 'Available')
            """)
            conn.commit()
            
            # Seed default products
            cursor.execute("SELECT id FROM vendors WHERE email = 'bk@airport.com'")
            bk_id = cursor.fetchone()[0]
            cursor.execute("SELECT id FROM vendors WHERE email = 'starbucks@airport.com'")
            sb_id = cursor.fetchone()[0]
            cursor.execute("SELECT id FROM vendors WHERE email = 'greatkabab@airport.com'")
            gk_id = cursor.fetchone()[0]
            
            cursor.execute("""
                INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
                (?, 'Whopper Burger', 299.00, 4.5, 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=200', 'Burgers', 'Flame-grilled beef patty topped with juicy tomatoes, fresh lettuce, and creamy mayo.'),
                (?, 'Crispy Chicken Burger', 249.00, 4.2, 'https://images.unsplash.com/photo-1625813506062-0aeb1d7a094b?w=200', 'Burgers', 'Tender crispy chicken breast patty topped with shredded lettuce and mayo.'),
                (?, 'Golden Fries (Large)', 129.00, 4.0, 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=200', 'Sides', 'Hot, crispy, and perfectly salted golden potato fries.'),
                (?, 'Coca-Cola (Regular)', 89.00, 4.1, 'https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=200', 'Drinks', 'Refreshing Coca-Cola classic beverage served cold.')
            """, (bk_id, bk_id, bk_id, bk_id))
            
            cursor.execute("""
                INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
                (?, 'Caffe Latte', 345.00, 4.6, 'https://images.unsplash.com/photo-1541167760496-1628856ab772?w=200', 'Hot Coffee', 'Rich espresso combined with steamed milk and a light layer of foam.'),
                (?, 'Java Chip Frappuccino', 395.00, 4.8, 'https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=200', 'Cold Coffee', 'Tall beverage of coffee blended with chocolate chips, milk, and ice, topped with whipped cream.'),
                (?, 'Chocolate Croissant', 220.00, 4.3, 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=200', 'Bakery', 'Buttery, flaky croissant stuffed with rich dark chocolate fields.')
            """, (sb_id, sb_id, sb_id))
            
            cursor.execute("""
                INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
                (?, 'Galouti Kabab Platters', 699.00, 4.8, 'https://images.unsplash.com/photo-1603360946369-dc9bb6258143?w=200', 'Mains', 'Melt-in-your-mouth minced mutton kebabs served with mint chutney and rumali roti.'),
                (?, 'Tandoori Chicken Tikka', 549.00, 4.6, 'https://images.unsplash.com/photo-1599487488170-d11ec9c172f0?w=200', 'Appetizers', 'Spicy yogurt-marinated chicken chunks grilled in a traditional clay oven.')
            """, (gk_id, gk_id))
            
            conn.commit()
            
        conn.close()

    # User operations
    def get_user(self, email):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM users WHERE LOWER(email) = ?", (email.lower(),))
        row = cursor.fetchone()
        conn.close()
        return dict(row) if row else None

    def create_user(self, email, name, password, mobile):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("INSERT OR REPLACE INTO users (email, name, password, mobile) VALUES (?, ?, ?, ?)",
                           (email.lower(), name, password, mobile))
            conn.commit()
        finally:
            conn.close()

    def update_profile(self, email, name=None, mobile=None, profile_photo=None, nationality=None, preferred_language=None, account_type=None, security_preferences=None):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            fields = []
            params = []
            if name is not None: fields.append("name = ?"); params.append(name)
            if mobile is not None: fields.append("mobile = ?"); params.append(mobile)
            if profile_photo is not None: fields.append("profile_photo = ?"); params.append(profile_photo)
            if nationality is not None: fields.append("nationality = ?"); params.append(nationality)
            if preferred_language is not None: fields.append("preferred_language = ?"); params.append(preferred_language)
            if account_type is not None: fields.append("account_type = ?"); params.append(account_type)
            if security_preferences is not None: fields.append("security_preferences = ?"); params.append(security_preferences)
            
            if fields:
                params.append(email.lower())
                sql = f"UPDATE users SET {', '.join(fields)} WHERE LOWER(email) = ?"
                cursor.execute(sql, params)
                conn.commit()
        finally:
            conn.close()

    # Seat Inventory Operations
    def get_booked_seats(self, flight_number, departure_date):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT seat_number, booked_by FROM flight_seats WHERE flight_number = ? AND departure_date = ? AND is_booked = 1", (flight_number, departure_date))
            rows = cursor.fetchall()
            return [dict(row) for row in rows]
        finally:
            conn.close()

    def book_seat(self, flight_number, departure_date, seat_number, booked_by, booking_id=None):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO flight_seats (flight_number, departure_date, seat_number, is_booked, booked_by, booking_id)
                VALUES (?, ?, ?, 1, ?, ?)
            """, (flight_number, departure_date, seat_number, booked_by, booking_id))
            conn.commit()
            return True
        except Exception:
            return False
        finally:
            conn.close()

    def release_seat(self, flight_number, departure_date, seat_number):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM flight_seats WHERE flight_number = ? AND departure_date = ? AND seat_number = ?", (flight_number, departure_date, seat_number))
            conn.commit()
            return True
        finally:
            conn.close()

    def update_password(self, email, password):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("UPDATE users SET password = ? WHERE LOWER(email) = ?",
                           (password, email.lower()))
            conn.commit()
        finally:
            conn.close()

    # Vendor operations
    def get_vendor(self, email):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM vendors WHERE LOWER(email) = ?", (email.lower(),))
        row = cursor.fetchone()
        conn.close()
        return dict(row) if row else None

    def get_vendor_by_id(self, vendor_id):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM vendors WHERE id = ?", (vendor_id,))
        row = cursor.fetchone()
        conn.close()
        return dict(row) if row else None

    def get_vendors(self, type_filter):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM vendors WHERE type = ?", (type_filter,))
        rows = cursor.fetchall()
        conn.close()
        return [dict(row) for row in rows]

    def register_vendor(self, email, password, name, type_filter, terminal, gate, image_url):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO vendors (email, password, name, type, terminal, gate, rating, image_url, availability)
                VALUES (?, ?, ?, ?, ?, ?, 5.0, ?, 'Available')
            """, (email.lower(), password, name, type_filter, terminal, gate, image_url))
            conn.commit()
            last_id = cursor.lastrowid
            cursor.execute("SELECT * FROM vendors WHERE id = ?", (last_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def delete_vendor(self, email):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM vendors WHERE LOWER(email) = ?", (email.lower(),))
            conn.commit()
            return True
        except Exception as e:
            print(f"[SQLITE ERROR] delete_vendor failed: {e}")
            return False
        finally:
            conn.close()

    # Product operations
    def get_products(self, vendor_id=None):
        conn = self.get_conn()
        cursor = conn.cursor()
        if vendor_id:
            cursor.execute("SELECT * FROM products WHERE vendor_id = ?", (vendor_id,))
        else:
            cursor.execute("SELECT * FROM products")
        rows = cursor.fetchall()
        conn.close()
        return [dict(row) for row in rows]

    def add_product(self, vendor_id, name, price, category, description, image_url):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO products (vendor_id, name, price, rating, category, description, image_url)
                VALUES (?, ?, ?, 5.0, ?, ?, ?)
            """, (vendor_id, name, price, category, description, image_url))
            conn.commit()
            last_id = cursor.lastrowid
            cursor.execute("SELECT * FROM products WHERE id = ?", (last_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def update_product(self, product_id, name, price, category, description, image_url):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                UPDATE products SET name = ?, price = ?, category = ?, description = ?, image_url = ?
                WHERE id = ?
            """, (name, price, category, description, image_url, product_id))
            conn.commit()
            cursor.execute("SELECT * FROM products WHERE id = ?", (product_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def delete_product(self, product_id):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM products WHERE id = ?", (product_id,))
            conn.commit()
        finally:
            conn.close()

    # Order operations
    def place_order(self, user_email, vendor_id, terminal, gate, total_price, items, payment_method='COD'):
        if total_price is None:
            try:
                total_price = sum(float(item.get('price', 0)) * int(item.get('quantity', 1)) for item in items)
            except Exception:
                total_price = 0.0

        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT COUNT(*) FROM orders")
            count = cursor.fetchone()[0] + 1
            booking_id = f"FOOD-{count:06d}"
            pickup_counter = gate

            cursor.execute("""
                INSERT INTO orders (booking_id, user_email, vendor_id, terminal, gate, pickup_counter, status, total_price, payment_method, payment_status)
                VALUES (?, ?, ?, ?, ?, ?, 'Confirmed', ?, ?, 'Completed')
            """, (booking_id, user_email, vendor_id, terminal, gate, pickup_counter, total_price, payment_method))
            order_id = cursor.lastrowid
            
            for item in items:
                p_id = item.get('product_id')
                raw_qty = item.get('quantity')
                if raw_qty is None or raw_qty == '':
                    raw_qty = item.get('qty')
                try:
                    qty = int(raw_qty) if raw_qty is not None else 1
                except Exception:
                    qty = 1
                price = item.get('price', 0.0)
                
                # Resilient product name resolution with lookups
                p_name = item.get('product_name') or item.get('name') or item.get('productName')
                if not p_name and p_id:
                    cursor.execute("SELECT name FROM products WHERE id = ?", (p_id,))
                    p_row = cursor.fetchone()
                    if p_row:
                        p_name = p_row['name']
                if not p_name:
                    p_name = "Unknown Item"

                cursor.execute("""
                    INSERT INTO order_items (order_id, product_id, quantity, price, product_name)
                    VALUES (?, ?, ?, ?, ?)
                """, (order_id, p_id, qty, price, p_name))
                
            conn.commit()
            cursor.execute("SELECT * FROM orders WHERE id = ?", (order_id,))
            order_row = cursor.fetchone()
            return dict(order_row) if order_row else None
        except Exception as e:
            try:
                conn.rollback()
            except Exception:
                pass
            print(f"[SQLITE ERROR] place_order failed: {e}")
            raise e
        finally:
            conn.close()

    def get_orders(self, user_email):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM orders WHERE user_email = ? ORDER BY id DESC", (user_email,))
        orders = [dict(row) for row in cursor.fetchall()]
        for order in orders:
            # get vendor name
            cursor.execute("SELECT name FROM vendors WHERE id = ?", (order['vendor_id'],))
            v_row = cursor.fetchone()
            order['vendor_name'] = v_row['name'] if v_row else "Unknown Restaurant"
            
            # get order items
            cursor.execute("SELECT * FROM order_items WHERE order_id = ?", (order['id'],))
            order['items'] = [dict(row) for row in cursor.fetchall()]
        conn.close()
        return orders

    def get_order(self, order_id):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM orders WHERE id = ?", (order_id,))
            row = cursor.fetchone()
            if not row:
                return None
            order = dict(row)
            # get vendor name
            cursor.execute("SELECT name FROM vendors WHERE id = ?", (order['vendor_id'],))
            v_row = cursor.fetchone()
            order['vendor_name'] = v_row['name'] if v_row else "Unknown Restaurant"
            
            # get order items
            cursor.execute("SELECT * FROM order_items WHERE order_id = ?", (order['id'],))
            order['items'] = [dict(row) for row in cursor.fetchall()]
            return order
        finally:
            conn.close()

    def get_vendor_orders(self, vendor_id):
        conn = self.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM orders WHERE vendor_id = ? ORDER BY id DESC", (vendor_id,))
        orders = [dict(row) for row in cursor.fetchall()]
        for order in orders:
            cursor.execute("SELECT * FROM order_items WHERE order_id = ?", (order['id'],))
            order['items'] = [dict(row) for row in cursor.fetchall()]
        conn.close()
        return orders

    def update_order_status(self, order_id, new_status):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("UPDATE orders SET status = ? WHERE id = ?", (new_status, order_id))
            conn.commit()
            cursor.execute("SELECT * FROM orders WHERE id = ?", (order_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def get_order_status(self, order_id):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT status FROM orders WHERE id = ?", (order_id,))
            row = cursor.fetchone()
            return row['status'] if row else None
        finally:
            conn.close()

    # Lounge bookings operations
    def book_lounge(self, user_email, vendor_id, booking_date, booking_time, slots):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO lounge_bookings (user_email, vendor_id, booking_date, booking_time, slots, status)
                VALUES (?, ?, ?, ?, ?, 'Pending')
            """, (user_email, vendor_id, booking_date, booking_time, slots))
            conn.commit()
            last_id = cursor.lastrowid
            cursor.execute("SELECT * FROM lounge_bookings WHERE id = ?", (last_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def get_bookings(self, user_email):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM lounge_bookings WHERE user_email = ? ORDER BY id DESC", (user_email,))
            bookings = [dict(row) for row in cursor.fetchall()]
            for booking in bookings:
                cursor.execute("SELECT name, terminal, gate, image_url FROM vendors WHERE id = ?", (booking['vendor_id'],))
                v_row = cursor.fetchone()
                if v_row:
                    booking['vendor_name'] = v_row['name']
                    booking['terminal'] = v_row['terminal']
                    booking['gate'] = v_row['gate']
                    booking['image_url'] = v_row['image_url']
                else:
                    booking['vendor_name'] = "Unknown Lounge"
                    booking['terminal'] = "-"
                    booking['gate'] = "-"
            return bookings
        finally:
            conn.close()

    def get_vendor_bookings(self, vendor_id):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM lounge_bookings WHERE vendor_id = ? ORDER BY id DESC", (vendor_id,))
            rows = cursor.fetchall()
            return [dict(row) for row in rows]
        finally:
            conn.close()

    def update_booking_status(self, booking_id, new_status):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("UPDATE lounge_bookings SET status = ? WHERE id = ?", (new_status, booking_id))
            conn.commit()
            cursor.execute("SELECT * FROM lounge_bookings WHERE id = ?", (booking_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def book_parking(self, user_email, zone, hours, plate_number, payment_method, total_price, booking_id=None, slot_number=None, terminal=None):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            if not booking_id:
                cursor.execute("SELECT COUNT(*) FROM parking_bookings")
                count = cursor.fetchone()[0] + 1
                booking_id = f"PRK-{count:06d}"
            if not slot_number:
                slot_number = f"Slot {zone}-{(abs(hash(str(plate_number) + str(user_email))) % 40) + 1:02d}"
            if not terminal:
                terminal = "Terminal 1" if "1" in str(zone) or "A" in str(zone) else "Terminal 2"
            
            now = datetime.datetime.now()
            entry_time = now.strftime("%Y-%m-%d %H:%M")
            exit_time = (now + datetime.timedelta(hours=int(hours or 2))).strftime("%Y-%m-%d %H:%M")

            cursor.execute("""
                INSERT INTO parking_bookings (booking_id, user_email, zone, slot_number, terminal, duration_hours, entry_time, exit_time, hours, plate_number, payment_method, total_price, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Confirmed')
            """, (booking_id, user_email, zone, slot_number, terminal, hours, entry_time, exit_time, hours, plate_number, payment_method, total_price))
            conn.commit()
            last_id = cursor.lastrowid
            cursor.execute("SELECT * FROM parking_bookings WHERE id = ?", (last_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def get_parking_bookings(self, user_email):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM parking_bookings WHERE user_email = ? ORDER BY id DESC", (user_email,))
            rows = cursor.fetchall()
            return [dict(row) for row in rows]
        finally:
            conn.close()

    # Flight Bookings operations
    def create_flight_booking(self, user_email, booking_id, pnr, payment_id, transaction_id, ticket_number, invoice_number, origin, destination, departure_date, flight_details, passenger_details, amount, payment_method):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO flight_bookings (
                    user_email, booking_id, pnr, payment_id, transaction_id, ticket_number, invoice_number,
                    origin, destination, departure_date, flight_details, passenger_details, amount, payment_method,
                    payment_status, booking_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Demo Success', 'Confirmed')
            """, (user_email, booking_id, pnr, payment_id, transaction_id, ticket_number, invoice_number,
                  origin, destination, departure_date, str(flight_details), str(passenger_details), amount, payment_method))
            conn.commit()
            last_id = cursor.lastrowid
            cursor.execute("SELECT * FROM flight_bookings WHERE id = ?", (last_id,))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def get_flight_bookings(self, user_email):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM flight_bookings WHERE LOWER(user_email) = LOWER(?) ORDER BY id DESC", (user_email.strip(),))
            rows = cursor.fetchall()
            return [dict(row) for row in rows]
        finally:
            conn.close()

    def get_flight_booking_by_id_or_pnr(self, id_or_pnr):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM flight_bookings WHERE booking_id = ? OR pnr = ? OR ticket_number = ?", (id_or_pnr, id_or_pnr, id_or_pnr))
            row = cursor.fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    # Chat history operations
    def save_chat_message(self, email, user_type, session_id, message, is_user):
        conn = self.get_conn()
        try:
            cursor = conn.cursor()
            cursor.execute("""
                INSERT INTO chat_history (email, user_type, session_id, message, is_user)
                VALUES (?, ?, ?, ?, ?)
            """, (email, user_type, session_id, message, is_user))
            conn.commit()
        finally:
            conn.close()

        if supabase is not None:
            try:
                supabase.table('chat_history').insert({
                    "email": email.lower(),
                    "user_type": user_type,
                    "session_id": session_id,
                    "message": message,
                    "is_user": is_user
                }).execute()
            except Exception as se:
                print("[SUPABASE CHAT] Save message error:", str(se))

db = LocalSQLiteDB()

# --- DATABASE INITIALIZATION ---
SUPABASE_URL = os.environ.get("SUPABASE_URL")
SUPABASE_KEY = os.environ.get("SUPABASE_KEY")

USE_SQLITE = False
try:
    if not SUPABASE_URL or not SUPABASE_KEY:
        raise Exception("SUPABASE_URL or SUPABASE_KEY environment variables not set")
    if create_client is None:
        raise Exception("Supabase module failed to import (Pydantic version conflict)")
    supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)
    # Check if Supabase DNS resolves & table queries succeed
    supabase.table('users').select('name').limit(1).execute()
    print("[DATABASE STATUS] Connected to Supabase Cloud Storage.")
except Exception as e:
    print(f"[DATABASE STATUS] Supabase cloud is unreachable or not configured ({e}). Seamlessly activated Local SQLite Persistent Fallback.")
    USE_SQLITE = True

@app.route('/', methods=['GET'])
def home():
    if app.config.get('TESTING') or 'text/html' not in request.headers.get('Accept', ''):
        return jsonify({"status": "online", "server": "AeroAssist AI Backend API is Alive and Running!"})
    try:
        return send_from_directory('../web', 'index.html')
    except Exception:
        return jsonify({"status": "online", "server": "AeroAssist AI Backend API is Alive and Running!"})

@app.route('/<regex(r"(?!api/|chat/).*"):path>')
def send_static(path):
    return send_from_directory('../web', path)


@app.route('/api/chat', methods=['POST'])
@app.route('/chat', methods=['POST'])
@limiter.limit("30 per minute")
def chat():
    data = request.json or {}
    message = (data.get('userQuery') or data.get('message') or '').strip()
    email = data.get('email', 'visitor@aeroassist.com').strip().lower()
    user_type = data.get('user_type', 'Passenger')
    session_id = data.get('session_id', 1)
    lang = (data.get('selectedLanguage') or data.get('lang') or 'en').strip()
    current_location = data.get('currentLocation', 'Terminal 1 Main Entrance').strip()
    airport = data.get('airport', 'Chennai International Airport (MAA)').strip()

    if not message:
        return jsonify({"status": "error", "message": "Message parameter is required"}), 400

    # Save user message to history
    try:
        db.save_chat_message(email, user_type, session_id, message, is_user=True)
    except Exception as e:
        print("[CHAT] Save user message error:", str(e))

    GROQ_API_KEY = os.environ.get("GROQ_API_KEY", "")
    reply = None

    # Mapping language codes to names for the AI system prompt
    lang_names = {
        'en': 'English',
        'english': 'English',
        'ta': 'Tamil',
        'tamil': 'Tamil',
        'hi': 'Hindi',
        'hindi': 'Hindi',
        'te': 'Telugu',
        'telugu': 'Telugu',
        'ml': 'Malayalam',
        'malayalam': 'Malayalam',
        'es': 'Spanish',
        'spanish': 'Spanish',
        'fr': 'French',
        'french': 'French',
        'de': 'German',
        'german': 'German'
    }
    target_lang = lang_names.get(lang.lower(), 'English')

    if GROQ_API_KEY:
        headers = {
            "Authorization": f"Bearer {GROQ_API_KEY}",
            "Content-Type": "application/json"
        }

        system_prompt = (
            "AEROASSIST AI – AEROPILOT AI SYSTEM PROMPT\n\n"
            "You are the official AI assistant for AeroAssist AI.\n"
            "Your assistant name is: ✈️ AeroPilot AI\n"
            "Tagline: 'Your Intelligent Airport Travel Companion'\n\n"
            "You are a specialized Airport AI Assistant designed exclusively to assist passengers, visitors, airport employees, vendors, and airport administrators with airport navigation, flight information, airport services, and travel assistance.\n\n"
            f"CURRENT SESSION CONTEXT:\n"
            f"- Selected App Language: {target_lang} (code: '{lang}')\n"
            f"- User Current Location: {current_location}\n"
            f"- Airport: {airport}\n\n"
            "=========================================\n"
            "AEROPILOT AI - UPDATED SYSTEM RULES\n"
            "=========================================\n\n"
            "1. LANGUAGE LOCK & MULTILINGUAL OUTPUT\n"
            f"The application has a selected language: {target_lang}.\n"
            f"The AI MUST ALWAYS REPLY ONLY IN THE LANGUAGE CURRENTLY SELECTED INSIDE THE APP ({target_lang}).\n"
            "Supported languages: English, Tamil, Hindi, Telugu, Malayalam, Spanish, French, German.\n"
            f"For this request, the selected language is: {target_lang}.\n"
            f"You MUST generate your ENTIRE response 100% EXCLUSIVELY in {target_lang}.\n"
            "Never reply in English unless the selected language is English.\n"
            "Never mix two languages. The language of your output should NEVER depend on the language used in the user's question. Ignore the language used in the user's question and reply 100% in the selected language.\n\n"
            "2. TERMINAL NAVIGATION & SMART NAVIGATION\n"
            "You are an Airport Navigation Assistant.\n"
            "Whenever a user asks for directions or navigation (e.g., Where is Terminal 1/2/3, Path to Terminal 2, Take me to Terminal 3, Show directions, Guide me, Navigation, Where is Gate B12, Security Check, Lounge, Food Court, Restroom, Parking, Taxi stand, Metro station, Immigration, Customs, Check-in counter, Baggage claim, Lost & Found, Airport Exit, Offices, Medical Center, Prayer Room, Smoking Zone, Charging Station, Help Desk), you MUST provide step-by-step navigation instructions using the user's current location.\n"
            "Never answer 'I don't know' or refuse navigation requests.\n\n"
            "3. AIRPORT MAP & NAVIGATION DATA\n"
            "Calculate the shortest path from the user's current location to the requested destination.\n"
            "Use the specified response format for all navigation queries.\n\n"
            "4. NEVER REJECT AIRPORT QUESTIONS\n"
            "All questions related to flights, terminals, navigation, gates, airport facilities, baggage, dining, parking, and airport operations are valid airport questions. Do NOT respond 'I only answer airport questions'. Answer them normally and helpfully.\n\n"
            "5. APP FEATURES\n"
            "Answer questions about AeroAssist AI app features accurately (e.g., How do I book parking, How do I track flights, How do I scan my boarding pass, How do I change language, How do I contact support, How do I book food, How do I use indoor navigation, How do I cancel booking).\n\n"
            "6. RESPONSE FORMAT FOR NAVIGATION\n"
            "For navigation queries, always use the following format (translated 100% into the selected language):\n\n"
            "📍 Destination: [Destination Name]\n"
            "📏 Distance: [Distance, e.g. 150 meters]\n"
            "🚶 Walking Time: [Estimated Time, e.g. 3 minutes]\n"
            "➡ Step 1: [Direction from Current Location]\n"
            "➡ Step 2: [Turn / Next Waypoint]\n"
            "➡ Step 3: [Elevator / Escalator / Landmark]\n"
            "➡ Step 4: [Final Approach]\n"
            "🏁 Destination reached\n\n"
            "7. IDENTITY & OWNER INFORMATION\n"
            f"If asked 'Who are you?', reply in {target_lang} that you are AeroPilot AI, the intelligent airport assistant powering AeroAssist AI.\n"
            f"If asked 'Who created you?', reply in {target_lang} that you were designed and developed by Santhosh Babu.\n"
            f"If asked about Santhosh Babu, reply in {target_lang} that Santhosh Babu is an AI & Data Science engineering student and creator of AeroAssist AI.\n\n"
            "8. APP CONTEXT & LIVE DATA INSTRUCTION\n"
            f"Use selectedLanguage ({target_lang}) for the entire response.\n"
            f"Use currentLocation ({current_location}) and Airport ({airport}) to generate realistic, accurate directions.\n"
            "If live navigation data is unavailable, clearly state that live route data isn't available for the specific sub-node instead of fabricating impossible routes, but still provide standard step-by-step terminal guidance."
        )

        payload = {
            "model": "llama-3.1-8b-instant",
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": message}
            ],
            "temperature": 0.7
        }

        try:
            import requests
            resp = requests.post("https://api.groq.com/openai/v1/chat/completions", headers=headers, json=payload)
            resp_data = resp.json()
            reply = resp_data['choices'][0]['message']['content']
        except Exception as e:
            print("Groq API Error:", str(e))

    # Multilingual Fallback Dictionary if Groq API is unavailable
    if not reply:
        fallback_map = {
            'Hindi': {
                'who_are_you': "मैं एरोपायलट एआई (AeroPilot AI) हूँ, एरोअसिस्ट एआई द्वारा संचालित आपका स्मार्ट एयरपोर्ट सहायक। मैं हवाई अड्डे के नेविगेशन, उड़ान की जानकारी और सेवाओं में सहायता करता हूँ।",
                'creator': "मुझे एरोअसिस्ट एआई प्लेटफॉर्म के हिस्से के रूप में संतोष बाबू द्वारा डिजाइन और विकसित किया गया था।",
                'santhosh': "संतोष बाबू एरोअसिस्ट एआई के निर्माता और डेवलपर हैं। वह एआई और डेटा साइंस इंजीनियरिंग के छात्र हैं।",
                'aeroassist': "एरोअसिस्ट एआई एक एआई-संचालित स्मार्ट एयरपोर्ट सहायता प्लेटफॉर्म है जो हवाई अड्डे के अनुभव को बेहतर बनाता है।",
                'flight': "✈️ उड़ान की स्थिति की जानकारी प्राप्त हुई। लाइव स्थिति के लिए कृपया अपना उड़ान नंबर (जैसे AI432) निर्दिष्ट करें।\n\nआपकी यात्रा शुभ और सुरक्षित हो! ✈️",
                'baggage': "🧳 मानक सामान भत्ता: 7 किग्रा केबिन बैग और 15 किग्रा चेक-इन बैग। तरल पदार्थ <= 100ml होने चाहिए।\n\nआपकी यात्रा शुभ और सुरक्षित हो! ✈️",
                'food': "🍔 टर्मिनलों पर बोर्डिंग गेट्स के पास विभिन्न रेस्तरां और कैफे उपलब्ध हैं। भोजन अनुभाग देखें!",
                'lounge': "🛋️ प्रीमियम लाउंज बुफे डाइनिंग, वाई-फाई और शांत विश्राम स्थान प्रदान करते हैं।",
                'parking': "🅿️ अल्पावधि और दीर्घकालिक हवाई अड्डा पार्किंग उपलब्ध है।",
                'lost': "📦 हवाई अड्डे में खोई हुई वस्तुओं की रिपोर्ट करने के लिए खोया और पाया अनुभाग देखें।",
                'greeting': "नमस्कार! ✈️ मैं एरोपायलट एआई हूँ, आपका एयरपोर्ट यात्रा साथी। आज मैं आपकी क्या सहायता कर सकता हूँ?",
                'default': "मैं एरोपायलट एआई के माध्यम से हवाई अड्डे और विमानन सहायता में विशेषज्ञता रखता हूँ। कृपया उड़ानों, हवाई अड्डों और सेवाओं से संबंधित प्रश्न पूछें।"
            },
            'Tamil': {
                'who_are_you': "நான் ஏரோபைலட் AI (AeroPilot AI), ஏரோஅசிஸ்ட் AI இன் அதிகாரப்பூர்வ விமான நிலைய உதவியாளராவேன்.",
                'creator': "நான் ஏரோஅசிஸ்ட் AI தளத்தின் ஒரு பகுதியாக சந்தோஷ் பாபு என்பவரால் வடிவமைக்கப்பட்டு உருவாக்கப் பட்டேன்.",
                'santhosh': "சந்தோஷ் பாபு ஏரோஅசிஸ்ட் AI இன் உருவாக்குனர் மற்றும் பொறியியல் மாணவர் ஆவார்.",
                'aeroassist': "ஏரோஅசிஸ்ட் AI என்பது விமான நிலைய சேவைகளை எளிதாக்கும் AI தளமாகும்.",
                'flight': "✈️ விமான நிலைத் தகவல் பெறப்பட்டது. உங்கள் விமான எண்ணைக் குறிப்பிடவும் (எ.கா. AI432).\n\nபாதுகாப்பான பயணம் அமைய வாழ்த்துகிறோம்! ✈️",
                'baggage': "🧳 கைப்பிடி பை: 7 கிலோ, பதிவுசெய்த பை: 15 கிலோ. திரவப் பொருட்கள் 100ml க்குள் இருக்க வேண்டும்.\n\nபாதுகாப்பான பயணம் அமைய வாழ்த்துகிறோம்! ✈️",
                'food': "🍔 முனையங்களில் பல்வேறு உணவகங்கள் உள்ளன. பயன்பாட்டில் உள்ள உணவுப் பகுதியைப் பார்க்கவும்!",
                'lounge': "🛋️ பிரீமியம் ஓய்வறைகள் உணவக வசதி மற்றும் வைஃபை சேவைகளை வழங்குகின்றன.",
                'parking': "🅿️ விமான நிலைய வாகன நிறுத்துமிடம் கிடைக்கிறது.",
                'lost': "📦 தவறவிட்ட பொருட்களைத் தேட இழப்பு மற்றும் மீட்புப் பகுதியைப் பார்வையிடவும்.",
                'greeting': "வணக்கம்! ✈️ நான் ஏரோபைலட் AI. இன்று உங்களுக்கு எவ்வாறு உதவ முடியும்?",
                'default': "நான் விமான நிலைய உதவியில் நிபுணத்துவம் பெற்றவன். விமானங்கள் மற்றும் சேவைகள் பற்றிய கேள்விகளைக் கேட்கவும்."
            },
            'Telugu': {
                'who_are_you': "నేను ఏరోపైలట్ AI (AeroPilot AI), ఏరోఅసిస్ట్ AI ద్వారా నడిచే అధికారిక విమానాశ్రయ సహాయకుడిని.",
                'creator': "నేను ఏరోఅసిస్ట్ AI ప్లాట్‌ఫారమ్‌లో భాగంగా సంతోష్ బాబు ద్వారా రూపొందించబడ్డాను.",
                'santhosh': "సంతోష్ బాబు ఏరోఅసిస్ట్ AI సృష్టికర్త మరియు డెవలపర్.",
                'aeroassist': "ఏరోఅసిస్ట్ AI అనేది స్మార్ట్ విమానాశ్రయ సహాయ వేదిక.",
                'flight': "✈️ విమాన స్థితి విచారణ సమర్పించబడింది. దయచేసి మీ విమాన సంఖ్యను తెలుపండి (ఉదా. AI432).\n\nసురక్షితమైన ప్రయాణం కలగాలని కోరుకుంటున్నాము! ✈️",
                'baggage': "🧳 లగేజ్ పరిమితి: 7 కేజీల కేబిన్ బ్యాగేజ్ మరియు 15 కేజీల చెక్-ఇన్ బ్యాగేజ్.",
                'food': "🍔 టెర్మినల్స్‌లో అనేక రకాల రెస్టారెంట్లు అందుబాటులో ఉన్నాయి.",
                'lounge': "🛋️ ప్రీమియం లాంజ్‌లు విశ్రాంతి ప్రదేశాలు మరియు వైఫైని అందిస్తాయి.",
                'parking': "🅿️ విమానాశ్రయ పార్కింగ్ సదుపాయం అందుబాటులో ఉంది.",
                'lost': "📦 పోగొట్టుకున్న వస్తువుల కోసం లాస్ట్ & ఫౌండ్ విభాగం చూడండి.",
                'greeting': "నమస్కారం! ✈️ నేను ఏరోపైలట్ AI. నేడు మీకు ఎలా సహాయపడగలను?",
                'default': "నేను విమానాశ్రయ సేవల్లో నిపుణుడిని. దయచేసి విమానాలు మరియు విమానాశ్రయ సేవల గురించి అడగండి."
            },
            'Malayalam': {
                'who_are_you': "ഞാൻ ഏറോപൈലറ്റ് AI (AeroPilot AI), ഏറോഅസിസ്റ്റ് AI നൽകുന്ന ഔദ്യോഗിക എയർപോർട്ട് അസിസ്റ്റന്റാണ്.",
                'creator': "സന്തോഷ് ബാബുവാണ് ഏറോഅസിസ്റ്റ് AI പ്ലാറ്റ്‌ഫോമിന്റെ ഭാഗമായി എന്നെ രൂപകൽപ്പന ചെയ്തത്.",
                'santhosh': "സന്തോഷ് ബാബു ഏറോഅസിസ്റ്റ് AI യുടെ സ്രഷ്ടാവും ഡെവലപ്പറുമാണ്.",
                'aeroassist': "ഏറോഅസിസ്റ്റ് AI എന്നത് എയർപോർട്ട് സേവനങ്ങൾ നൽകുന്ന ഒരു സ്മാർട്ട് പ്ലാറ്റ്‌ഫോമാണ്.",
                'flight': "✈️ ഫ്ലൈറ്റ് വിവരങ്ങൾ ലഭിച്ചു. നിങ്ങളുടെ ഫ്ലൈറ്റ് നമ്പർ നൽകുക (ഉദാ: AI432).\n\nസുരക്ഷിതമായ യാത്ര ആശംസിക്കുന്നു! ✈️",
                'baggage': "🧳 ലഗേജ് പരിധി: 7 കിലോഗ്രാം കാബിൻ ബാഗും 15 കിലോഗ്രാം ചെക്ക്-ഇൻ ബാഗും.",
                'food': "🍔 ടെർമിനലുകളിൽ ഭക്ഷണശാലകൾ ലഭ്യമാണ്.",
                'lounge': "🛋️ പ്രീമിയം ലോഞ്ചുകൾ വിശ്രമകേന്ദ്രങ്ങളും വൈഫൈയും നൽകുന്നു.",
                'parking': "🅿️ എയർപോർട്ട് പാർക്കിംഗ് സൗകര്യം ലഭ്യമാണ്.",
                'lost': "📦 നഷ്ടപ്പെട്ട സാധനങ്ങൾക്കായി ലോസ്റ്റ് & ഫൗണ്ട് സെക്ഷൻ സന്ദർശിക്കുക.",
                'greeting': "നമസ്കാരം! ✈️ ഞാൻ ഏറോപൈലറ്റ് AI ആണ്. ഇന്ന് ഞാൻ നിങ്ങളെ എങ്ങനെ സഹായിക്കണം?",
                'default': "ഞാൻ എയർപോർട്ട് സേവനങ്ങളിൽ സഹായിക്കുന്ന ആളാണ്. ഫ്ലൈറ്റുകളെക്കുറിച്ചും സേവനങ്ങളെക്കുറിച്ചും ചോദിക്കുക."
            },
            'Spanish': {
                'who_are_you': "Soy AeroPilot AI, el asistente oficial de aeropuerto que impulsa AeroAssist AI. Ayudo a pasajeros y usuarios con navegación, vuelos y servicios aeroportuarios.",
                'creator': "Fui diseñado y desarrollado por Santhosh Babu como parte de la plataforma AeroAssist AI.",
                'santhosh': "Santhosh Babu es el creador y desarrollador de AeroAssist AI y estudiante de ingeniería en IA y Ciencia de Datos.",
                'aeroassist': "AeroAssist AI es una plataforma inteligente de asistencia aeroportuaria impulsada por IA.",
                'flight': "✈️ Consulta de vuelo recibida. Por favor especifique su número de vuelo (ej. AI432) para ver estado en vivo y puerta.\n\n¡Que tenga un viaje seguro y agradable! ✈️",
                'baggage': "🧳 Equipaje estándar: 7 kg en mano y 15 kg facturado. Los líquidos deben ser <= 100ml.\n\n¡Que tenga un viaje seguro y agradable! ✈️",
                'food': "🍔 Las terminales ofrecen gran variedad de restaurantes cerca de las puertas de embarque.",
                'lounge': "🛋️ Salones Premium con buffet, Wi-Fi y áreas de descanso.",
                'parking': "🅿️ Estacionamiento disponible a corto y largo plazo.",
                'lost': "📦 Visite Objetos Perdidos para buscar o reportar artículos.",
                'greeting': "¡Saludos! ✈️ Soy AeroPilot AI, su compañero de viaje. ¿Cómo puedo ayudarle hoy con su vuelo o servicios?",
                'default': "Me especializo en asistencia aeroportuaria. Por favor haga preguntas sobre vuelos, aeropuertos y servicios."
            },
            'French': {
                'who_are_you': "Je suis AeroPilot AI, l'assistant aéroportuaire intelligent d'AeroAssist AI. J'aide les passagers pour la navigation, les vols et les services aéroportuaires.",
                'creator': "J'ai été conçu et développé par Santhosh Babu dans le cadre de la plateforme AeroAssist AI.",
                'santhosh': "Santhosh Babu est le créateur et développeur d'AeroAssist AI et étudiant en IA et Science des Données.",
                'aeroassist': "AeroAssist AI est une plateforme intelligente d'assistance aéroportuaire propulsée par l'IA.",
                'flight': "✈️ Demande de vol reçue. Veuillez indiquer votre numéro de vol (ex. AI432) pour les détails en direct.\n\nBon voyage et bon vol ! ✈️",
                'baggage': "🧳 Bagage standard : 7 kg en cabine et 15 kg en soute. Liquides <= 100ml.\n\nBon voyage et bon vol ! ✈️",
                'food': "🍔 Les aérogares proposent divers restaurants et cafés près des portes d'embarquement.",
                'lounge': "🛋️ Salons Premium avec buffet, Wi-Fi et espaces de détente.",
                'parking': "🅿️ Stationnement courte et longue durée disponible.",
                'lost': "📦 Visitez la section Objets Trouvés pour signaler ou chercher des objets.",
                'greeting': "Bonjour ! ✈️ Je suis AeroPilot AI. Comment puis-je vous aider aujourd'hui ?",
                'default': "Je suis spécialisé dans l'assistance aéroportuaire. Posez-moi des questions sur les vols et services."
            },
            'German': {
                'who_are_you': "Ich bin AeroPilot AI, der intelligente Flughafen-Assistent von AeroAssist AI. Ich helfe Passagieren bei Navigation, Flügen und Flughafen-Services.",
                'creator': "Ich wurde von Santhosh Babu als Teil der Plattform AeroAssist AI entwickelt.",
                'santhosh': "Santhosh Babu ist der Entwickler von AeroAssist AI und Ingenieurstudent für KI und Datenwissenschaft.",
                'aeroassist': "AeroAssist AI ist eine KI-gestützte Plattform für intelligente Flughafen-Unterstützung.",
                'flight': "✈️ Flugstatus-Anfrage erhalten. Bitte geben Sie Ihre Flugnummer an (z. B. AI432).\n\nGute und sichere Reise! ✈️",
                'baggage': "🧳 Freigepäck: 7 kg Handgepäck und 15 kg Aufgabegepäck. Flüssigkeiten <= 100ml.\n\nGute und sichere Reise! ✈️",
                'food': "🍔 Terminals bieten vielfältige Gastronomie in der Nähe der Gates.",
                'lounge': "🛋️ Premium Lounges bieten Buffet, WLAN und Ruhebereiche.",
                'parking': "🅿️ Kurz- und Langzeitparkplätze stehen zur Verfügung.",
                'lost': "📦 Besuchen Sie den Bereich Fundbüro für verlorene Gegenstände.",
                'greeting': "Guten Tag! ✈️ Ich bin AeroPilot AI. Wie kann ich Ihnen heute helfen?",
                'default': "Ich bin auf Flughafen-Unterstützung spezialisiert. Fragen Sie mich gerne zu Flügen und Services."
            },
            'English': {
                'who_are_you': "I am AeroPilot AI, the intelligent airport assistant powering AeroAssist AI. I help passengers and airport users with airport navigation, flight information, airport services, and travel assistance.",
                'creator': "I was designed and developed by Santhosh Babu as part of the AeroAssist AI platform.",
                'santhosh': "Santhosh Babu is the creator and developer of AeroAssist AI. He is an Artificial Intelligence and Data Science engineering student who designed AeroAssist AI to enhance airport experiences using AI-powered assistance, smart airport services, and intelligent travel support.",
                'aeroassist': "AeroAssist AI is an AI-powered smart airport assistance platform designed to improve the passenger experience by providing airport navigation, flight information, airport services, multilingual assistance, travel guidance, and intelligent airport support through a single application.",
                'flight': "✈️ Flight status query received. Please specify your flight number (e.g. AI432) for live status, gate, and schedule details.\n\nHave a safe and pleasant journey! ✈️",
                'baggage': "🧳 Standard baggage allowance: 7 kg cabin baggage and 15 kg checked baggage. Liquids in carry-on must be <= 100ml.\n\nHave a safe and pleasant journey! ✈️",
                'food': "🍔 Terminals offer a variety of dining and cafes near boarding gates. Check the Food & Dining section in app to explore menus and pre-order!\n\nHave a safe and pleasant journey! ✈️",
                'lounge': "🛋️ Premium Lounges offer buffet dining, Wi-Fi, and quiet relaxation spaces near gates. Reserve access via the Lounges section!\n\nHave a safe and pleasant journey! ✈️",
                'parking': "🅿️ Short-term and long-term airport parking is available. You can reserve parking slots directly in the app!\n\nHave a safe and pleasant journey! ✈️",
                'lost': "📦 Visit the Lost & Found section to report or search for items misplaced within airport terminals.",
                'greeting': "Greetings! ✈️ I am AeroPilot AI, your intelligent airport travel companion. How can I assist with your flight, baggage, terminal navigation, or airport services today?",
                'default': "I specialize in airport and aviation assistance through AeroPilot AI. Please ask me questions related to flights, airports, terminals, baggage, boarding, airport services, or travel assistance."
            }
        }
        
        lang_dict = fallback_map.get(target_lang, fallback_map['English'])
        msg_lower = message.lower()
        if "who are you" in msg_lower:
            reply = lang_dict['who_are_you']
        elif "who created you" in msg_lower or "who made you" in msg_lower or "who is your owner" in msg_lower or "who developed you" in msg_lower or "who built aeroassist ai" in msg_lower:
            reply = lang_dict['creator']
        elif "santhosh babu" in msg_lower:
            reply = lang_dict['santhosh']
        elif "what is aeroassist ai" in msg_lower or "what is aeroassist" in msg_lower:
            reply = lang_dict['aeroassist']
        elif "flight" in msg_lower or "status" in msg_lower or "gate" in msg_lower:
            reply = lang_dict['flight']
        elif "baggage" in msg_lower or "luggage" in msg_lower:
            reply = lang_dict['baggage']
        elif "food" in msg_lower or "restaurant" in msg_lower or "dine" in msg_lower or "eat" in msg_lower:
            reply = lang_dict['food']
        elif "lounge" in msg_lower:
            reply = lang_dict['lounge']
        elif "parking" in msg_lower:
            reply = lang_dict['parking']
        elif "lost" in msg_lower or "found" in msg_lower:
            reply = lang_dict['lost']
        elif "hello" in msg_lower or "hi" in msg_lower or "hey" in msg_lower:
            reply = lang_dict['greeting']
        else:
            reply = lang_dict['default']

    # Save AI reply to history
    try:
        db.save_chat_message(email, user_type, session_id, reply, is_user=False)
    except Exception as e:
        print("[CHAT] Save AI message error:", str(e))

    return jsonify({"status": "success", "reply": reply})

# In-memory storage for temporary OTPs before they are officially verified
otp_store = {}

def send_verification_email(to_email, otp, name="Valued User", custom_message=None):
    # Dynamic headers and description
    if custom_message:
        pre_title = "SECURITY VERIFICATION"
        desc_text = custom_message
    else:
        pre_title = "ACCOUNT REGISTRATION VERIFICATION"
        desc_text = "Use the following secure code to complete your <strong>Account Registration</strong> request on our platform."

    html_content = f"""
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
    @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap');
    body {{ font-family: 'Inter', 'Segoe UI', sans-serif; margin: 0; padding: 30px 20px;
           background: linear-gradient(135deg, #0a0f1e 0%, #0d1b3e 40%, #0a2a5e 70%, #0e3a6e 100%); min-height: 100vh; }}
    .container {{ max-width: 580px; margin: 0 auto;
                  background: linear-gradient(160deg, rgba(13,30,64,0.97) 0%, rgba(8,18,45,0.99) 100%);
                  border-radius: 20px; overflow: hidden;
                  box-shadow: 0 20px 60px rgba(0,0,0,0.6), 0 0 0 1px rgba(255,255,255,0.07); }}
    .header {{ background: linear-gradient(135deg, #0b2d6b 0%, #0d3b8f 50%, #0a52c4 100%);
               padding: 40px 20px; text-align: center; position: relative; overflow: hidden; }}
    .header::before {{ content: '✈'; position: absolute; font-size: 120px; opacity: 0.1;
                        top: -20px; right: -10px; transform: rotate(-30deg); }}
    .header h1 {{ color: #ffffff; margin: 0; font-size: 28px; font-weight: 800; letter-spacing: 2px; }}
    .header p {{ color: #7fb3ff; margin: 8px 0 0 0; font-size: 11px; font-weight: 600; letter-spacing: 3px; text-transform: uppercase; }}
    .content {{ padding: 40px 30px; }}
    .pre-title {{ color: #4a9eff; font-size: 11px; font-weight: 700; letter-spacing: 2px; text-transform: uppercase; margin-bottom: 10px; }}
    .title {{ color: #ffffff; font-size: 30px; font-weight: 800; margin: 0 0 20px 0; line-height: 1.2; }}
    .greeting {{ color: #c8d8ff; font-size: 17px; margin-bottom: 15px; }}
    .greeting strong {{ color: #ffffff; font-weight: 700; }}
    .desc {{ color: #8fa8cc; font-size: 15px; line-height: 1.6; margin-bottom: 30px; }}
    .desc strong {{ color: #c8d8ff; }}
    .otp-card {{ background: linear-gradient(135deg, #0d2d5e 0%, #0a1f45 100%);
                  border: 1px solid rgba(74,158,255,0.3); border-radius: 16px;
                  padding: 30px; text-align: center; margin-bottom: 30px;
                  box-shadow: 0 0 30px rgba(10,82,196,0.3); }}
    .otp-subtitle {{ color: #4a9eff; font-size: 11px; font-weight: 700; letter-spacing: 3px; text-transform: uppercase; margin-bottom: 20px; }}
    .otp-code {{ color: #ffffff; font-size: 52px; font-weight: 800; letter-spacing: 18px; margin: 0 0 20px 18px;
                  font-family: 'Courier New', monospace; text-shadow: 0 0 20px rgba(74,158,255,0.5); }}
    .otp-divider {{ height: 1px; background: linear-gradient(90deg, transparent, rgba(74,158,255,0.4), transparent); margin-bottom: 15px; }}
    .otp-timer {{ color: #ff6b6b; font-size: 13px; font-weight: 600; }}
    .notice {{ background: rgba(255,100,100,0.08); border: 1px solid rgba(255,100,100,0.2);
               border-radius: 10px; padding: 15px; font-size: 13px; color: #ff9a9a; line-height: 1.5; }}
    .notice strong {{ font-weight: 700; color: #ffb3b3; }}
    td {{ color: #8fa8cc !important; }}
</style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>AeroAssist AI</h1>
            <p>INTELLIGENT AIRPORT & FLIGHT NAVIGATION PLATFORM</p>
        </div>
        <div class="content">
            <div class="pre-title">{pre_title}</div>
            <h2 class="title">Your One-Time<br>Password</h2>
            
            <div class="greeting">Hello, <strong>{name}</strong> 👋</div>
            <div class="desc">{desc_text}</div>
            
            <div class="otp-card">
                <div class="otp-subtitle">SECURE VERIFICATION CODE</div>
                <div class="otp-code">{otp}</div>
                <div class="otp-divider"></div>
                <div class="otp-timer">⏱️ Valid for 5 minutes only</div>
            </div>
            
            <table width="100%" cellpadding="0" cellspacing="0" style="margin-bottom: 30px;">
                <tr>
                    <td width="31%" align="center" style="border: 1px solid #e2e8f0; border-radius: 8px; padding: 15px 5px; font-size: 12px; font-weight: 700; color: #7f99b2;">
                        <span style="font-size: 18px;">🔒</span><br>Secure
                    </td>
                    <td width="3%"></td>
                    <td width="32%" align="center" style="background-color: #fffdf5; border: 1px solid #fceea7; border-radius: 8px; padding: 15px 5px; font-size: 12px; font-weight: 700; color: #b58d00;">
                        <span style="font-size: 18px;">⚡</span><br>Single Use
                    </td>
                    <td width="3%"></td>
                    <td width="31%" align="center" style="background-color: #f0fbff; border: 1px solid #bce6f5; border-radius: 8px; padding: 15px 5px; font-size: 12px; font-weight: 700; color: #0087b5;">
                        <span style="font-size: 18px;">✈️</span><br>Aero Assist
                    </td>
                </tr>
            </table>
            
            <div class="notice">
                <strong>Security Notice:</strong> If you did not request this code, please ignore this email. Your account remains protected.
            </div>
        </div>
    </div>
</body>
</html>
"""

    resend_key = os.environ.get("RESEND_API_KEY")
    resend_sender = os.environ.get("RESEND_SENDER_EMAIL", "noreply@aeroassistai.in").strip()

    if resend_key:
        resend_key = resend_key.strip()
        import requests
        try:
            url = "https://api.resend.com/emails"
            headers = {
                "Authorization": f"Bearer {resend_key}",
                "Content-Type": "application/json"
            }
            data = {
                "from": f"AeroAssist Security <{resend_sender}>",
                "to": to_email,
                "subject": f"🔒 {otp} is your AeroAssist Verification Code",
                "html": html_content
            }
            response = requests.post(url, json=data, headers=headers, timeout=5)
            if response.status_code in [200, 201]:
                return True, "Sent via Resend"
            else:
                return False, f"Resend API Error {response.status_code}: {response.text}"
        except Exception as e_resend:
            return False, f"Resend HTTPS dispatch failed: {e_resend}"

    # SMTP Fallback (Direct SSL Port 465 for max speed and reliability)
    import smtplib
    from email.mime.multipart import MIMEMultipart
    from email.mime.text import MIMEText

    smtp_email = os.environ.get("SMTP_SENDER_EMAIL")
    smtp_pass = os.environ.get("SMTP_SENDER_PASSWORD")

    if not smtp_email or not smtp_pass:
        msg = "No SMTP credentials configured in environment variables."
        print(f"[SMTP ERROR] {msg}")
        return False, msg

    print(f"[SMTP] Attempting SMTP SSL delivery via '{smtp_email}' to: {to_email}...")
    try:
        msg = MIMEMultipart('alternative')
        msg['Subject'] = f"🔒 {otp} is your AeroAssist Verification Code"
        msg['From'] = f"AeroAssist Security <{smtp_email}>"
        msg['To'] = to_email

        part = MIMEText(html_content, 'html')
        msg.attach(part)

        # Bypass Port 587 entirely. Direct SSL Port 465 is much faster on cloud networks.
        server = smtplib.SMTP_SSL('smtp.gmail.com', 465, timeout=10)
        server.login(smtp_email, smtp_pass)
        server.sendmail(smtp_email, to_email, msg.as_string())
        server.quit()
        print(f"[SMTP SUCCESS] Email successfully delivered via SMTP SSL to {to_email}!")
        return True, "Sent via SMTP SSL"
    except Exception as e_smtp:
        err = f"SMTP SSL delivery failed: {str(e_smtp)}"
        print(f"[SMTP EXCEPTION] {err}")
        return False, err


@app.route('/api/google-login', methods=['POST'])
@limiter.limit("30 per minute")
def google_login():
    """Handles Google Sign-In: direct login for existing users, OTP for new ones."""
    data = request.json or {}
    email = (data.get('email') or '').strip().lower()
    name = data.get('name', 'Google User')

    existing_user = None
    if USE_SQLITE:
        existing_user = db.get_user(email)
    else:
        try:
            response = supabase.table('users').select('name, mobile').eq('email', email).execute()
            existing_user = response.data[0] if response.data else None
        except Exception as e:
            print("[FALLBACK] Supabase error in google_login:", str(e))
            existing_user = db.get_user(email)

    if not existing_user:
        # Require OTP verification for brand new Google accounts
        display_name = name if name and name != 'Google User' else email.split('@')[0].capitalize()
        import secrets
        otp = str(secrets.randbelow(900000) + 100000)
        otp_store[email] = {
            "otp": otp,
            "name": display_name,
            "password": f"google_oauth_{email}",
            "mobile": "",
            "attempts": 0
        }
        send_verification_email(email, otp, display_name)
        print(f"\n[SERVER SECURE LOG] -> Sent OTP '{otp}' for new Google account target: {email}")
        return jsonify({
            "status": "success",
            "existing": False,
            "message": f"New account detected! An OTP verification code has been sent to {email}."
        })

    token = generate_token(email, role="user")
    photo = existing_user.get('profile_photo')
    if photo and not photo.startswith('data:') and not photo.startswith('http'):
        photo = f"data:image/jpeg;base64,{photo}"
    return jsonify({
        "status": "success",
        "existing": True,
        "name": existing_user.get('name') or email.split('@')[0].capitalize(),
        "mobile": existing_user.get('mobile', ""),
        "profile_photo": photo,
        "token": token,
        "message": "Google Login Successful"
    })

@app.route('/api/config', methods=['GET'])
def get_public_config():
    """Returns public environment configuration for OAuth clients."""
    return jsonify({
        "status": "success",
        "google_client_id": os.environ.get("GOOGLE_CLIENT_ID", "")
    })

@app.route('/api/google-callback', methods=['GET'])
def google_callback():
    """OAuth 2.0 callback endpoint rendering postMessage script for parent popup handlers."""
    html_content = """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Google Authentication Success</title>
    <style>
        body { background: #0F172A; color: #F8FAFC; font-family: system-ui, sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
        .card { background: rgba(30, 41, 59, 0.8); border: 1px solid rgba(255,255,255,0.1); border-radius: 16px; padding: 28px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.5); }
        .spinner { width: 36px; height: 36px; border: 3px solid rgba(255,255,255,0.1); border-top-color: #38BDF8; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 16px auto; }
        @keyframes spin { to { transform: rotate(360deg); } }
    </style>
</head>
<body>
    <div class="card">
        <div class="spinner"></div>
        <h2>Completing Google Sign-In...</h2>
        <p style="color:#94A3B8; font-size:14px;">Connecting your account with AeroAssist AI</p>
    </div>
    <script>
        (function() {
            const searchParams = new URLSearchParams(window.location.search.substring(1));
            const hashParams = new URLSearchParams(window.location.hash.substring(1));
            const code = searchParams.get('code') || hashParams.get('code');
            const idToken = searchParams.get('id_token') || hashParams.get('id_token') || searchParams.get('access_token') || hashParams.get('access_token');
            let email = searchParams.get('email') || hashParams.get('email');
            
            if (window.opener) {
                window.opener.postMessage({
                    type: 'google_auth',
                    code: code,
                    idToken: idToken,
                    email: email
                }, '*');
                setTimeout(function() { window.close(); }, 800);
            } else {
                window.location.href = '/web/index.html';
            }
        })();
    </script>
</body>
</html>"""
    return html_content, 200, {'Content-Type': 'text/html; charset=utf-8'}

@app.route('/api/register', methods=['POST'])
@limiter.limit("30 per minute")
def register():
    data = request.json or {}
    email = data.get('email')
    if not isinstance(email, str):
        email = ''
    email = email.strip().lower()

    if not email:
        return jsonify({"status": "error", "message": "Missing email parameter"}), 400

    import re
    # Validate email format
    if (not isinstance(email, str) or
        not re.match(r'^[a-zA-Z0-9\._%+-]+@[a-zA-Z0-9\.-]+\.[a-zA-Z]{2,6}$', email) or
        '..' in email or
        email.startswith('.') or
        '@.' in email or
        '@-' in email or
        '.@' in email or
        email.endswith('.') or
        email.endswith('_') or
        email.endswith('-')):
        return jsonify({"status": "error", "message": "Invalid email format"}), 400

    # Validate password length
    password = data.get('password')
    if not isinstance(password, str) or len(password) < 6:
        return jsonify({"status": "error", "message": "Password must be at least 6 characters long"}), 400

    # Validate mobile number format if provided
    mobile = data.get('mobile')
    if mobile:
        if not isinstance(mobile, str) or not re.match(r'^\+?[0-9\s-]{10,15}$', mobile):
            return jsonify({"status": "error", "message": "Invalid mobile number format"}), 400
    
    existing_user = None
    if USE_SQLITE:
        existing_user = db.get_user(email)
    else:
        try:
            response = supabase.table('users').select('email').eq('email', email).execute()
            existing_user = response.data[0] if response.data else None
        except Exception as e:
            print("[FALLBACK] Supabase error in register check:", str(e))
            existing_user = db.get_user(email)

    if existing_user:
         return jsonify({"status": "error", "message": "Email already registered"}), 400

    # Generate secure 6-digit code
    import secrets
    otp = str(secrets.randbelow(900000) + 100000)
    
    # Store registration temporarily pending verification
    otp_store[email] = {
        "otp": otp,
        "name": data.get('name'),
        "password": data.get('password'),
        "mobile": data.get('mobile'),
        "attempts": 0
    }
    
    # Send email synchronously so WSGI worker guarantees delivery
    send_verification_email(email, otp, name=data.get('name', 'Valued User'))

    print(f"\n[SERVER SECURE LOG] -> Sent OTP '{otp}' to target: {email}")
    return jsonify({"status": "success", "message": "OTP blasted to user email inbox."})

@app.route('/api/verify', methods=['POST'])
def verify():
    data = request.json or {}
    email = data.get('email')
    otp = data.get('otp')
    
    if not email or not otp:
        return jsonify({"status": "error", "message": "Missing email or otp"}), 400
        
    email = email.strip().lower()
    
    if email not in otp_store:
        return jsonify({"status": "error", "message": "Invalid OTP Code or Email mismatch."}), 400
        
    store_data = otp_store[email]
    
    if store_data.get('attempts', 0) >= 3:
        return jsonify({"status": "error", "message": "Too many invalid attempts. OTP blocked."}), 429
        
    if store_data['otp'] == str(otp):
        # Validate and formally migrate to SQLite persistent storage
        user_data = otp_store[email]
        hashed_password = generate_password_hash(user_data['password'])
        
        if USE_SQLITE:
            db.create_user(email, user_data['name'], hashed_password, user_data['mobile'])
        else:
            try:
                supabase.table('users').upsert({
                    'email': email,
                    'name': user_data['name'],
                    'password': hashed_password,
                    'mobile': user_data['mobile']
                }).execute()
            except Exception as e:
                print("[FALLBACK] Supabase error in verify upsert:", str(e))
                db.create_user(email, user_data['name'], hashed_password, user_data['mobile'])
        
        del otp_store[email]
        token = generate_token(email, role="user")
        return jsonify({
            "status": "success", 
            "message": "User Authenticated and Verified.", 
            "name": user_data['name'],
            "token": token
        })
    else:
        store_data['attempts'] = store_data.get('attempts', 0) + 1
        return jsonify({"status": "error", "message": "Invalid OTP Code."}), 400

@app.route('/api/login', methods=['POST'])
@limiter.limit("5 per minute")
def login():
    """
    Supabase-first login.
    Always tries Supabase for authentication, falls back to SQLite.
    Returns profile_photo so the client can display it immediately without an extra round-trip.
    """
    data = request.json or {}
    email = data.get('email')
    password = data.get('password')
    
    if not isinstance(email, str):
        email = ''
    if not isinstance(password, str):
        password = ''
        
    email = email.strip().lower()
    password = password.strip()
    
    user = None

    # Step 1: Always try Supabase first
    if supabase is not None:
        try:
            response = supabase.table('users').select('*').ilike('email', email).execute()
            if response.data:
                user = response.data[0]
        except Exception as e:
            print("[LOGIN] Supabase error, falling back to SQLite:", str(e))

    # Step 2: Fallback to SQLite if Supabase unavailable or user not found there
    if not user:
        user = db.get_user(email)

    print(f"[LOGIN] attempt for: '{email}' | DB match: {user is not None}")
    
    if user and check_password(user.get('password'), password):
        token = generate_token(email, role="user")
        photo = user.get('profile_photo')
        if photo and not photo.startswith('data:') and not photo.startswith('http'):
            photo = f"data:image/jpeg;base64,{photo}"
        return jsonify({
            "status": "success", 
            "message": "Login validated securely.", 
            "name": user.get('name'),
            "mobile": user.get('mobile'),
            "profile_photo": photo,
            "token": token
        })
        
    return jsonify({"status": "error", "message": "Invalid credentials. If you are a new user, please create an account first."}), 401
@app.route('/api/token-refresh', methods=['POST'])
@limiter.limit("20 per minute")
def token_refresh():
    """Issue a fresh JWT for a registered user by verifying their email exists in the DB."""
    data = request.json or {}
    email = data.get('email', '').strip().lower()
    if not email:
        return jsonify({"status": "error", "message": "Missing email"}), 400

    # Verify user exists
    user = None
    if USE_SQLITE:
        user = db.get_user(email)
    else:
        try:
            response = supabase.table('users').select('name').ilike('email', email).execute()
            user = response.data[0] if response.data else None
        except Exception:
            user = db.get_user(email)

    if not user:
        return jsonify({"status": "error", "message": "User not found"}), 404

    token = generate_token(email, role="user")
    return jsonify({"status": "success", "token": token})


@app.route('/api/save-chat', methods=['POST'])
def save_chat():
    data = request.json or {}
    email = (data.get('email') or '').strip().lower()
    user_type = data.get('user_type', 'Passenger')
    session_id = data.get('session_id')
    message = data.get('message')
    is_user = data.get('is_user', True)

    if not email or message is None:
        return jsonify({"status": "error", "message": "email and message are required"}), 400

    # DUAL WRITE: Always save to SQLite (local) for reliability
    try:
        db.save_chat_message(email, user_type, session_id, message, is_user)
    except Exception as e:
        print("[SAVE CHAT] SQLite error:", str(e))

    # Also write to Supabase if available (for cross-device sync)
    if supabase is not None:
        try:
            supabase.table('chat_history').insert({
                'email': email,
                'user_type': user_type,
                'session_id': str(session_id) if session_id else None,
                'message': message,
                'is_user': bool(is_user)
            }).execute()
        except Exception as e:
            print("[SAVE CHAT] Supabase write error (non-fatal):", str(e))

    return jsonify({"status": "success"})


def fetch_user_table(table_name, email):
    """
    Supabase-first, SQLite-fallback unified reader.
    Always tries Supabase first so all devices see the same data.
    Falls back to SQLite if Supabase is unavailable.
    """
    email_norm = email.strip().lower()
    # Try Supabase first (source of truth)
    if supabase is not None:
        try:
            res = supabase.table(table_name).select('*').eq('user_email', email_norm).execute()
            if res and res.data:
                return res.data
        except Exception as e:
            print(f"[SUPABASE READ] {table_name} error:", str(e))
    # Fallback to SQLite
    method_map = {
        'chat_history': lambda: db.get_chat_history(email_norm),
        'flight_bookings': lambda: db.get_flight_bookings(email_norm),
        'orders': lambda: db.get_orders(email_norm),
        'lounge_bookings': lambda: db.get_lounge_bookings(email_norm),
        'parking_bookings': lambda: db.get_parking_bookings(email_norm),
        'lost_items': lambda: db.get_lost_items(email_norm),
    }
    if table_name in method_map:
        try:
            return method_map[table_name]()
        except Exception as e:
            print(f"[SQLITE READ] {table_name} error:", str(e))
    return []

@app.route('/api/chat-history', methods=['GET'])
def get_chat_history():
    """
    Returns chat history for a user.
    Supabase is the primary source – its data will overwrite any SQLite duplicates.
    SQLite data is merged in only for messages not already present in Supabase.
    """
    email = request.args.get('email')
    if not email:
        return jsonify({"status": "error", "message": "Email is required"}), 400

    email = email.strip().lower()
    print(f"[CHAT HISTORY] Fetching history for: {email}")

    history = []
    supabase_loaded = False

    # Step 1: Always try Supabase first
    if supabase is not None:
        try:
            res = supabase.table('chat_history')\
                .select('*')\
                .ilike('email', email)\
                .order('id', desc=False)\
                .execute()
            if res and res.data:
                for item in res.data:
                    history.append({
                        "email": item.get("email"),
                        "user_type": item.get("user_type"),
                        "session_id": item.get("session_id"),
                        "message": item.get("message"),
                        "is_user": bool(item.get("is_user")),
                        "created_at": str(item.get("created_at"))
                    })
                supabase_loaded = True
                print(f"[CHAT HISTORY] Loaded {len(history)} messages from Supabase")
        except Exception as e:
            print("[CHAT HISTORY] Supabase Fetch Error:", str(e))

    # Step 2: Merge SQLite records (catches any messages not yet synced to Supabase)
    try:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute(
            "SELECT email, user_type, session_id, message, is_user, created_at FROM chat_history WHERE LOWER(email) = ? ORDER BY id ASC",
            (email,)
        )
        rows = cursor.fetchall()
        conn.close()
        existing_msgs = set((h["message"], bool(h["is_user"])) for h in history)
        for r in rows:
            msg_key = (r["message"], bool(r["is_user"]))
            if msg_key not in existing_msgs:
                history.append({
                    "email": r["email"],
                    "user_type": r["user_type"],
                    "session_id": r["session_id"],
                    "message": r["message"],
                    "is_user": bool(r["is_user"]),
                    "created_at": str(r["created_at"])
                })
                existing_msgs.add(msg_key)
        if not supabase_loaded:
            print(f"[CHAT HISTORY] Loaded {len(history)} messages from SQLite (Supabase unavailable)")
    except Exception as e:
        print("[CHAT HISTORY] SQLite Fetch Error:", str(e))

    return jsonify({"status": "success", "history": history})

@app.route('/api/get-profile', methods=['GET'])
def get_profile():
    """
    Supabase-first profile fetch.
    Always tries Supabase for the most up-to-date data
    (e.g. profile photo changed on another device), then falls back to SQLite.
    """
    email = (request.args.get('email') or '').strip().lower()
    if not email:
        return jsonify({"status": "error", "message": "email parameter is required"}), 400

    user = None

    # Step 1: Always try Supabase first (cross-device source of truth)
    if supabase is not None:
        try:
            res = supabase.table('users').select('*').ilike('email', email).execute()
            if res and res.data:
                user = res.data[0]
                print(f"[GET PROFILE] Loaded from Supabase for {email}")
        except Exception as e:
            print(f"[GET PROFILE] Supabase error, falling back to SQLite: {str(e)}")

    # Step 2: Fallback to SQLite if Supabase didn't return data
    if not user:
        user = db.get_user(email)
        if user:
            print(f"[GET PROFILE] Loaded from SQLite for {email}")

    if user:
        photo = user.get('profile_photo')
        # Ensure photo has proper data URI prefix for display
        if photo and not photo.startswith('data:') and not photo.startswith('http'):
            photo = f"data:image/jpeg;base64,{photo}"
        return jsonify({
            "status": "success",
            "email": user.get('email'),
            "name": user.get('name'),
            "mobile": user.get('mobile'),
            "profile_photo": photo,
            "nationality": user.get('nationality') or 'Indian',
            "preferred_language": user.get('preferred_language') or 'en',
            "account_type": user.get('account_type') or 'Passenger',
            "security_preferences": user.get('security_preferences') or '{}'
        })
    else:
        return jsonify({
            "status": "success",
            "email": email,
            "name": email.split('@')[0].capitalize(),
            "mobile": "",
            "profile_photo": None,
            "nationality": 'Indian',
            "preferred_language": 'en',
            "account_type": 'Passenger',
            "security_preferences": '{}'
        })

@app.route('/api/update-profile', methods=['POST'])
def update_profile():
    data = request.json or {}
    email = (data.get('email') or '').strip().lower()
    name = data.get('name')
    mobile = data.get('mobile')
    profile_photo = data.get('profile_photo')
    nationality = data.get('nationality')
    preferred_language = data.get('preferred_language') or data.get('language')
    account_type = data.get('account_type') or data.get('user_type')
    security_preferences = data.get('security_preferences')
    if isinstance(security_preferences, dict):
        import json
        security_preferences = json.dumps(security_preferences)

    if not email:
        return jsonify({"status": "error", "message": "email is required"}), 400

    # Normalize profile_photo: strip data URI prefix for DB storage, re-add when reading
    if profile_photo and profile_photo.startswith('data:'):
        try:
            profile_photo_clean = profile_photo.split(',', 1)[1]
        except Exception:
            profile_photo_clean = profile_photo
    else:
        profile_photo_clean = profile_photo

    # DUAL WRITE: Always write to SQLite
    try:
        db.update_profile(
            email=email,
            name=name,
            mobile=mobile,
            profile_photo=profile_photo_clean,
            nationality=nationality,
            preferred_language=preferred_language,
            account_type=account_type,
            security_preferences=security_preferences
        )
    except Exception as e:
        print("[UPDATE PROFILE] SQLite error:", str(e))

    # Also write to Supabase if available
    if supabase is not None:
        try:
            update_dict = {}
            if name is not None: update_dict['name'] = name
            if mobile is not None: update_dict['mobile'] = mobile
            if profile_photo_clean is not None: update_dict['profile_photo'] = profile_photo_clean
            if nationality is not None: update_dict['nationality'] = nationality
            if preferred_language is not None: update_dict['preferred_language'] = preferred_language
            if account_type is not None: update_dict['account_type'] = account_type
            if security_preferences is not None: update_dict['security_preferences'] = security_preferences
            if update_dict:
                supabase.table('users').update(update_dict).ilike('email', email).execute()
        except Exception as e:
            print("[UPDATE PROFILE] Supabase write error (non-fatal):", str(e))

    return jsonify({"status": "success", "message": "Profile updated successfully"})

@app.route('/api/vendors/orders/<order_id>/status', methods=['PUT'])
@limiter.limit("20 per minute")
def update_vendor_order_status(order_id):
    pass # Implementation details omitted for brevity

@app.route('/api/test-env', methods=['GET'])
def test_env():
    import os
    return {"smtp_email_set": bool(os.environ.get("SMTP_SENDER_EMAIL")), "smtp_pass_set": bool(os.environ.get("SMTP_SENDER_PASSWORD")), "resend_key_set": bool(os.environ.get("RESEND_API_KEY"))}

@app.route('/api/password-reset-request', methods=['POST'])
@limiter.limit("30 per minute")
def password_reset_request():
    data = request.json or {}
    email = data.get('email')
    if not email:
        return jsonify({"status": "error", "message": "email parameter is required"}), 400
    email = email.strip().lower()
    
    user = None
    if USE_SQLITE:
        user = db.get_user(email)
    else:
        try:
            response = supabase.table('users').select('name').ilike('email', email).execute()
            user = response.data[0] if response.data else None
        except Exception as e:
            print("[FALLBACK] Supabase password reset request error:", str(e))
            user = db.get_user(email)
            
    if not user:
        return jsonify({"status": "error", "message": "Email not recognized"}), 404
        
    import secrets
    otp = str(secrets.randbelow(900000) + 100000)
    otp_store[email + "_reset"] = {
        "otp": otp, 
        "name": user.get('name'),
        "attempts": 0,
        "expires_at": time.time() + 300
    }
    
    # Custom message as per User Request
    message = "This is your OTP to change password. Please verify this code to securely update your account credentials."
    success, reason = send_verification_email(email, otp, name=user.get('name'), custom_message=message)
    
    if not success:
        # Prevent the user from proceeding if the OTP failed to send
        del otp_store[email + "_reset"]
        return jsonify({"status": "error", "message": f"Email delivery failed: {reason}"}), 500
        
    return jsonify({"status": "success", "message": "Verification code sent to email"})

@app.route('/api/password-reset-confirm', methods=['POST'])
@limiter.limit("5 per minute")
def password_reset_confirm():
    data = request.json or {}
    email = data.get('email')
    otp = data.get('otp')
    new_password = data.get('new_password')
    
    if not email or not otp or not new_password:
        return jsonify({"status": "error", "message": "Missing email, otp, or new_password"}), 400
        
    email = email.strip().lower()
    key = email + "_reset"
    if key not in otp_store:
        return jsonify({"status": "error", "message": "Invalid OTP Code or expired"}), 400
        
    reset_data = otp_store[key]
    if reset_data.get('expires_at', 0) < time.time():
        del otp_store[key]
        return jsonify({"status": "error", "message": "OTP expired"}), 400
        
    if reset_data.get('attempts', 0) >= 3:
        return jsonify({"status": "error", "message": "Too many invalid attempts. OTP blocked."}), 429
        
    if reset_data['otp'] == str(otp):
        hashed_password = generate_password_hash(new_password)
        if USE_SQLITE:
            db.update_password(email, hashed_password)
        else:
            try:
                supabase.table('users').update({
                    'password': hashed_password
                }).ilike('email', email).execute()
            except Exception as e:
                print("[FALLBACK] Supabase password reset confirm error:", str(e))
                db.update_password(email, hashed_password)
                
        del otp_store[key]
        return jsonify({"status": "success", "message": "Password changed successfully"})
    else:
        reset_data['attempts'] = reset_data.get('attempts', 0) + 1
        return jsonify({"status": "error", "message": "Invalid OTP Code"}), 400
        


# --- NEW VENDOR & ORDER / LOUNGE BOOKING SYSTEM ENDPOINTS ---

@app.route('/api/vendors/register', methods=['POST'])
def vendor_register():
    data = request.json or {}
    admin_key = data.get('admin_key')
    
    # Enforce Admin-only restriction
    if admin_key != ADMIN_SECRET_KEY:
        return jsonify({"status": "error", "message": "Unauthorized: Only the admin can register vendor accounts"}), 403

    email = data.get('email')
    password = data.get('password')
    name = data.get('name')
    v_type = data.get('type')  # 'restaurant' or 'lounge'
    terminal = data.get('terminal', 'Terminal 1')
    gate = data.get('gate', 'Gate 1')
    image_url = data.get('image_url', '')

    if not email or not password or not name or not v_type:
        return jsonify({"status": "error", "message": "Missing required fields"}), 400

    hashed_password = generate_password_hash(password)

    if USE_SQLITE:
        existing = db.get_vendor(email)
        if existing:
            return jsonify({"status": "error", "message": "Vendor email already registered"}), 400
        vendor = db.register_vendor(email, hashed_password, name, v_type, terminal, gate, image_url)
        return jsonify({"status": "success", "message": "Vendor registered successfully", "vendor": sanitize_vendor(vendor)})
    else:
        try:
            response = supabase.table('vendors').select('email').eq('email', email).execute()
            if response.data:
                return jsonify({"status": "error", "message": "Vendor email already registered"}), 400

            ins_resp = supabase.table('vendors').insert({
                'email': email,
                'password': hashed_password,
                'name': name,
                'type': v_type,
                'terminal': terminal,
                'gate': gate,
                'rating': 5.0,
                'image_url': image_url,
                'availability': 'Available'
            }).execute()
            return jsonify({"status": "success", "message": "Vendor registered successfully", "vendor": sanitize_vendor(ins_resp.data[0])})
        except Exception as e:
            print("[FALLBACK] Supabase register error:", str(e))
            existing = db.get_vendor(email)
            if existing:
                return jsonify({"status": "error", "message": "Vendor email already registered"}), 400
            vendor = db.register_vendor(email, hashed_password, name, v_type, terminal, gate, image_url)
            return jsonify({"status": "success", "message": "Vendor registered successfully", "vendor": sanitize_vendor(vendor)})

@app.route('/api/vendors/delete', methods=['POST'])
def delete_vendor():
    data = request.json or {}
    admin_key = data.get('admin_key')
    email = data.get('email')

    # Enforce Admin-only restriction
    if admin_key != ADMIN_SECRET_KEY:
        return jsonify({"status": "error", "message": "Unauthorized: Only the admin can remove vendor accounts"}), 403

    if not email:
        return jsonify({"status": "error", "message": "Missing email parameter"}), 400

    if USE_SQLITE:
        success = db.delete_vendor(email)
        if success:
            return jsonify({"status": "success", "message": "Vendor account removed successfully"})
        return jsonify({"status": "error", "message": "Failed to remove vendor"}), 500
    try:
        supabase.table('vendors').delete().eq('email', email).execute()
        db.delete_vendor(email)
        return jsonify({"status": "success", "message": "Vendor account removed successfully"})
    except Exception as e:
        print("[FALLBACK] Supabase delete vendor error:", str(e))
        success = db.delete_vendor(email)
        if success:
            return jsonify({"status": "success", "message": "Vendor account removed successfully"})
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/vendors/login', methods=['POST'])
@limiter.limit("5 per minute")
def vendor_login():
    data = request.json or {}
    email = data.get('email')
    password = data.get('password')

    if not isinstance(email, str):
        email = ''
    if not isinstance(password, str):
        password = ''

    email = email.strip().lower()
    password = password.strip()

    vendor = None
    if USE_SQLITE:
        vendor = db.get_vendor(email)
    else:
        try:
            response = supabase.table('vendors').select('*').eq('email', email).execute()
            vendor = response.data[0] if response.data else None
        except Exception as e:
            print("[FALLBACK] Supabase vendor login error:", str(e))
            vendor = db.get_vendor(email)

    if not vendor or not check_password(vendor.get('password'), password):
        return jsonify({"status": "error", "message": "Invalid credentials"}), 401

    token = generate_token(email, role="vendor")
    vendor_data = sanitize_vendor(vendor)
    vendor_data["token"] = token
    return jsonify({
        "status": "success",
        "message": "Vendor login successful",
        "vendor": vendor_data,
        "token": token
    })

@app.route('/api/restaurants', methods=['GET'])
def get_restaurants():
    def strip_pwd(v): v.pop('password', None); return v
    if USE_SQLITE:
        return jsonify({"status": "success", "restaurants": [strip_pwd(v) for v in db.get_vendors('restaurant')]})
    try:
        response = supabase.table('vendors').select('id,email,name,type,terminal,gate,rating,image_url,availability').eq('type', 'restaurant').execute()
        return jsonify({"status": "success", "restaurants": response.data})
    except Exception as e:
        print("[FALLBACK] Supabase restaurants fetch error:", str(e))
        return jsonify({"status": "success", "restaurants": [strip_pwd(v) for v in db.get_vendors('restaurant')]})

@app.route('/api/shopping', methods=['GET'])
def get_shopping():
    # Dynamic self-seeding of Shopping Vendors and Products if empty
    conn = db.get_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM vendors WHERE type = 'shopping'")
    count = cursor.fetchone()[0]
    if count == 0:
        cursor.execute("""
            INSERT INTO vendors (email, password, name, type, terminal, gate, rating, image_url, availability) VALUES
            ('dutyfree@airport.com', 'vendor123', 'Duty Free Americas', 'shopping', 'Terminal 1', 'Gate 18', 4.5, 'https://images.unsplash.com/photo-1544816155-12df9643f363?w=500', 'Available'),
            ('relay@airport.com', 'vendor123', 'Relay Books & Travel', 'shopping', 'Terminal 1', 'Gate 12', 4.2, 'https://images.unsplash.com/photo-1507842217343-583bb7270b66?w=500', 'Available'),
            ('tech2go@airport.com', 'vendor123', 'Tech2Go', 'shopping', 'Terminal 2', 'Gate 28', 4.6, 'https://images.unsplash.com/photo-1531297484001-80022131f5a1?w=500', 'Available')
        """)
        conn.commit()
        
        cursor.execute("SELECT id FROM vendors WHERE email = 'dutyfree@airport.com'")
        df_id = cursor.fetchone()[0]
        cursor.execute("SELECT id FROM vendors WHERE email = 'relay@airport.com'")
        rl_id = cursor.fetchone()[0]
        cursor.execute("SELECT id FROM vendors WHERE email = 'tech2go@airport.com'")
        tg_id = cursor.fetchone()[0]
        
        cursor.execute("""
            INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
            (?, 'Chanel No. 5 Perfume', 8500.00, 4.5, 'https://images.unsplash.com/photo-1544816155-12df9643f363?w=200', 'Fragrances', 'The ultimate luxury fragrance for women, a timeless classic.'),
            (?, 'Macallan 12 Year Single Malt', 7200.00, 4.7, 'https://images.unsplash.com/photo-1527061011665-3652c757a4d4?w=200', 'Liquor', 'Premium single malt scotch whisky matured in sherry seasoned oak casks.'),
            (?, 'Swiss Lindt Dark Truffles', 1200.00, 4.3, 'https://images.unsplash.com/photo-1548907040-4d42b52115ca?w=200', 'Chocolates', 'Decadent dark chocolate truffles with a smooth melting center.')
        """, (df_id, df_id, df_id))
        
        cursor.execute("""
            INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
            (?, 'Atomic Habits (James Clear)', 399.00, 4.8, 'https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?w=200', 'Books', 'An easy & proven way to build good habits & break bad ones.'),
            (?, 'Travel Neck Pillow (Memory Foam)', 999.00, 4.2, 'https://images.unsplash.com/photo-1520038410233-7141be7e6f97?w=200', 'Travel Gear', 'Ergonomic memory foam neck support pillow for comfortable long flights.'),
            (?, 'AeroAssist Notebook', 299.00, 4.5, 'https://images.unsplash.com/photo-1531346878377-a5be20888e57?w=200', 'Stationery', 'Sleek, faux-leather travel notebook with high-quality cream pages.')
        """, (rl_id, rl_id, rl_id))
        
        cursor.execute("""
            INSERT INTO products (vendor_id, name, price, rating, image_url, category, description) VALUES
            (?, 'Sony WH-1000XM4 Headphones', 19990.00, 4.9, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=200', 'Audio', 'Industry-leading noise cancelling overhead headphones with premium sound.'),
            (?, 'Anker PowerCore 20000mAh', 2499.00, 4.6, 'https://images.unsplash.com/photo-1609592424087-434a6efc687e?w=200', 'Accessories', 'Ultra-high capacity power bank with fast-charging technology.'),
            (?, 'Universal Travel Adapter', 899.00, 4.4, 'https://images.unsplash.com/photo-1583863788434-e58a36330cf0?w=200', 'Accessories', 'All-in-one international plug adapter covering over 150 countries.')
        """, (tg_id, tg_id, tg_id))
        
        conn.commit()
    conn.close()

    if USE_SQLITE:
        return jsonify({"status": "success", "shopping": db.get_vendors('shopping')})
    try:
        response = supabase.table('vendors').select('*').eq('type', 'shopping').execute()
        return jsonify({"status": "success", "shopping": response.data})
    except Exception as e:
        print("[FALLBACK] Supabase shopping fetch error:", str(e))
        return jsonify({"status": "success", "shopping": db.get_vendors('shopping')})

@app.route('/api/lounges', methods=['GET'])
def get_lounges():
    def strip_pwd(v): v.pop('password', None); return v
    if USE_SQLITE:
        return jsonify({"status": "success", "lounges": [strip_pwd(v) for v in db.get_vendors('lounge')]})
    try:
        response = supabase.table('vendors').select('id,email,name,type,terminal,gate,rating,image_url,availability').eq('type', 'lounge').execute()
        return jsonify({"status": "success", "lounges": response.data})
    except Exception as e:
        print("[FALLBACK] Supabase lounges fetch error:", str(e))
        return jsonify({"status": "success", "lounges": [strip_pwd(v) for v in db.get_vendors('lounge')]})

@app.route('/api/products', methods=['GET'])
def get_products():
    vendor_id = request.args.get('vendor_id')
    if not vendor_id:
        return jsonify({"status": "error", "message": "vendor_id parameter is required"}), 400
    if USE_SQLITE:
        return jsonify({"status": "success", "products": db.get_products(vendor_id)})
    try:
        response = supabase.table('products').select('*').eq('vendor_id', vendor_id).execute()
        return jsonify({"status": "success", "products": response.data})
    except Exception as e:
        print("[FALLBACK] Supabase products fetch error:", str(e))
        return jsonify({"status": "success", "products": db.get_products(vendor_id)})

@app.route('/api/orders', methods=['POST'])
@token_required(['user', 'visitor', 'admin', 'vendor'])
def place_order():
    data = request.json or {}
    print("[POST /api/orders] Received raw order payload:", data)
    
    user_email = data.get('user_email')
    if not user_email:
        return jsonify({"status": "error", "message": "Missing user_email parameter"}), 400
    user_email = user_email.strip().lower()
    
    if request.user_email and request.user_role in ['user', 'visitor'] and request.user_email.strip().lower() != user_email:
        if user_email != 'visitor@aeroassist.com' and not user_email.startswith('visitor'):
            return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403
    vendor_id = data.get('vendor_id')
    terminal = data.get('terminal')
    gate = data.get('gate')
    total_price = data.get('total_price')
    payment_method = data.get('payment_method', 'COD')
    items = data.get('items', [])

    # Validate required parameters
    if not user_email or not vendor_id or not terminal or not gate or not items:
        error_msg = f"Missing required fields: email={user_email}, vendor_id={vendor_id}, terminal={terminal}, gate={gate}, items_count={len(items)}"
        print("[POST /api/orders] Validation failed:", error_msg)
        return jsonify({"status": "error", "message": error_msg}), 400

    # Calculate total price if missing or 0
    if total_price is None or float(total_price) == 0.0:
        try:
            total_price = sum(float(item.get('price', 0.0)) * int(item.get('quantity') or item.get('qty') or 1) for item in items)
            print(f"[POST /api/orders] Calculated missing total_price: {total_price}")
        except Exception as e:
            total_price = 0.0
            print("[POST /api/orders] Failed to calculate total_price:", str(e))

    # Always persist in Local SQLite as well for reliable cross-platform syncing
    sqlite_order = None
    try:
        sqlite_order = db.place_order(user_email, vendor_id, terminal, gate, total_price, items, payment_method)
    except Exception as e:
        print("[SQLITE SYNC ERROR]:", str(e))

    if supabase is not None:
        try:
            order_resp = supabase.table('orders').insert({
                'user_email': user_email,
                'vendor_id': vendor_id,
                'terminal': terminal,
                'gate': gate,
                'status': 'Pending',
                'total_price': total_price,
                'payment_method': payment_method
            }).execute()
            
            if order_resp.data:
                order_id = order_resp.data[0]['id']
                
                order_items_data = []
                for item in items:
                    p_id = item.get('product_id')
                    qty = item.get('quantity') or item.get('qty') or 1
                    try:
                        qty = int(qty)
                    except Exception:
                        qty = 1
                    price = item.get('price', 0.0)
                    
                    p_name = item.get('product_name') or item.get('name') or item.get('productName')
                    if not p_name and p_id:
                        try:
                            p_resp = supabase.table('products').select('name').eq('id', p_id).execute()
                            if p_resp.data:
                                p_name = p_resp.data[0]['name']
                        except Exception:
                            pass
                    
                    if not p_name:
                        p_name = "Unknown Item"
                        
                    order_items_data.append({
                        'order_id': order_id,
                        'product_id': p_id,
                        'quantity': qty,
                        'price': price,
                        'product_name': p_name
                    })
                    
                if order_items_data:
                    supabase.table('order_items').insert(order_items_data).execute()
                    
                return jsonify({
                    "status": "success", 
                    "message": "Order placed successfully", 
                    "order_id": order_id,
                    "order": order_resp.data[0]
                })
        except Exception as e:
            print("[SUPABASE SYNC ERROR]:", str(e))

    if sqlite_order:
        return jsonify({"status": "success", "message": "Order placed successfully", "order_id": sqlite_order['id'], "order": sqlite_order})
    return jsonify({"status": "error", "message": "Failed to create order"}), 500
@app.route('/api/orders/history', methods=['GET'])
@app.route('/api/orders', methods=['GET'])
def order_history():
    user_email = request.args.get('user_email') or request.args.get('email')
    if not user_email or not user_email.strip():
        return jsonify({"status": "success", "orders": []})
        
    user_email = user_email.strip().lower()
    raw_sqlite = db.get_orders(user_email)
    seen_keys = set()
    combined = []

    if supabase is not None:
        try:
            orders_resp = supabase.table('orders').select('*').ilike('user_email', user_email).order('id', desc=True).execute()
            orders = orders_resp.data or []
            if orders:
                order_ids = [order['id'] for order in orders]
                vendor_ids = list(set([order['vendor_id'] for order in orders if order.get('vendor_id')]))
                
                vendors_by_id = {}
                if vendor_ids:
                    try:
                        v_resp = supabase.table('vendors').select('id,name').in_('id', vendor_ids).execute()
                        for v in (v_resp.data or []):
                            vendors_by_id[v['id']] = v['name']
                    except Exception:
                        pass
                        
                items_by_order = {}
                try:
                    items_resp = supabase.table('order_items').select('*').in_('order_id', order_ids).execute()
                    for item in (items_resp.data or []):
                        oid = item.get('order_id')
                        items_by_order.setdefault(oid, []).append(item)
                except Exception:
                    pass
                    
                for order in orders:
                    order['vendor_name'] = vendors_by_id.get(order.get('vendor_id'), "Airport Outlet")
                    order['items'] = items_by_order.get(order['id'], [])
                    
                    key = f"{order.get('id')}_{order.get('total_price')}"
                    seen_keys.add(key)
                    combined.append(order)
        except Exception as e:
            print("[SUPABASE QUERY NOTICE]:", str(e))

    for o in raw_sqlite:
        key = f"{o.get('id')}_{o.get('total_price')}"
        if key not in seen_keys:
            seen_keys.add(key)
            combined.append(dict(o))
            
    combined.sort(key=lambda x: str(x.get('created_at', '')), reverse=True)
    return jsonify({"status": "success", "orders": combined})

@app.route('/api/orders/<int:order_id>', methods=['GET'])
def get_order_details(order_id):
    order = None
    if supabase is not None:
        try:
            order_resp = supabase.table('orders').select('*').eq('id', order_id).execute()
            order = order_resp.data[0] if order_resp.data else None
        except Exception:
            pass
    if not order:
        order = db.get_order(order_id)
            
    if not order:
        return jsonify({"status": "error", "message": "Order not found"}), 404
        
    if supabase is not None:
        try:
            v_resp = supabase.table('vendors').select('name').eq('id', order['vendor_id']).execute()
            order['vendor_name'] = v_resp.data[0]['name'] if v_resp.data else "Airport Outlet"
            items_resp = supabase.table('order_items').select('*').eq('order_id', order['id']).execute()
            order['items'] = items_resp.data or []
        except Exception:
            pass

    return jsonify({"status": "success", "order": order})

@app.route('/api/orders/<int:order_id>/status', methods=['GET'])
def get_order_status(order_id):
    if USE_SQLITE:
        status = db.get_order_status(order_id)
        if status:
            return jsonify({"status": "success", "order_id": order_id, "order_status": status})
        return jsonify({"status": "error", "message": "Order not found"}), 404
    try:
        response = supabase.table('orders').select('status').eq('id', order_id).execute()
        if response.data:
            return jsonify({"status": "success", "order_id": order_id, "order_status": response.data[0]['status']})
        return jsonify({"status": "error", "message": "Order not found"}), 404
    except Exception as e:
        print("[FALLBACK] Supabase get order status error:", str(e))
        status = db.get_order_status(order_id)
        if status:
            return jsonify({"status": "success", "order_id": order_id, "order_status": status})
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/lounge_bookings', methods=['POST'])
@app.route('/api/bookings', methods=['POST'])
def book_lounge():
    data = request.json or {}
    user_email = data.get('user_email')
    vendor_id = data.get('vendor_id')
    booking_date = data.get('booking_date')
    booking_time = data.get('booking_time')
    slots = data.get('slots', 1)

    if not user_email or not vendor_id or not booking_date or not booking_time:
        return jsonify({"status": "error", "message": "Missing required booking fields"}), 400

    # Dual-persistence logic
    sqlite_booking = None
    try:
        sqlite_booking = db.book_lounge(user_email, vendor_id, booking_date, booking_time, slots)
    except Exception as e:
        print("[SQLITE SYNC ERROR]:", str(e))

    if supabase is not None:
        try:
            booking_resp = supabase.table('lounge_bookings').insert({
                'user_email': user_email,
                'vendor_id': vendor_id,
                'booking_date': booking_date,
                'booking_time': booking_time,
                'slots': slots,
                'status': 'Pending'
            }).execute()
            if booking_resp.data:
                return jsonify({"status": "success", "message": "Lounge booked successfully", "booking": booking_resp.data[0]})
        except Exception as e:
            print("[SUPABASE SYNC ERROR]:", str(e))
            
    if sqlite_booking:
        return jsonify({"status": "success", "message": "Lounge booked successfully", "booking": sqlite_booking})
    return jsonify({"status": "error", "message": "Failed to create booking"}), 500

@app.route('/api/lounge_bookings/history', methods=['GET'])
@app.route('/api/bookings', methods=['GET'])
def lounge_booking_history():
    user_email = request.args.get('user_email') or request.args.get('email')
    if not user_email:
        return jsonify({"status": "error", "message": "Missing user_email parameter"}), 400
    raw_sqlite = db.get_bookings(user_email)
    seen_keys = set()
    combined = []

    if supabase is not None:
        try:
            bookings_resp = supabase.table('lounge_bookings').select('*').eq('user_email', user_email).order('id', desc=True).execute()
            bookings = bookings_resp.data or []
            for booking in bookings:
                v_resp = supabase.table('vendors').select('name', 'terminal', 'gate', 'image_url').eq('id', booking['vendor_id']).execute()
                if v_resp.data:
                    booking['vendor_name'] = v_resp.data[0]['name']
                    booking['terminal'] = v_resp.data[0]['terminal']
                    booking['gate'] = v_resp.data[0]['gate']
                    booking['image_url'] = v_resp.data[0]['image_url']
                else:
                    booking['vendor_name'] = "Unknown Lounge"
                    booking['terminal'] = "-"
                    booking['gate'] = "-"
                
                key = f"{booking.get('vendor_id')}_{booking.get('booking_date')}_{booking.get('booking_time')}_{str(booking.get('created_at'))[:16]}"
                seen_keys.add(key)
                combined.append(booking)
        except Exception as e:
            print("[SUPABASE QUERY NOTICE]:", str(e))

    for b in raw_sqlite:
        key = f"{b.get('vendor_id')}_{b.get('booking_date')}_{b.get('booking_time')}_{str(b.get('created_at'))[:16]}"
        if key not in seen_keys:
            seen_keys.add(key)
            combined.append(dict(b))
            
    combined.sort(key=lambda x: str(x.get('created_at', '')), reverse=True)
    return jsonify({"status": "success", "bookings": combined})

# --- PARKING BOOKING SYSTEM ENDPOINTS ---

def generate_unique_parking_slot(zone):
    occupied_slots = set()
    if supabase is not None:
        try:
            res = supabase.table('parking_bookings').select('slot_number').execute()
            for r in (res.data or []):
                if r.get('slot_number'):
                    occupied_slots.add(str(r.get('slot_number')).strip())
        except Exception:
            pass
    try:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT slot_number FROM parking_bookings")
        for r in cursor.fetchall():
            if r['slot_number']:
                occupied_slots.add(str(r['slot_number']).strip())
        conn.close()
    except Exception:
        pass

    zone_code = "A" if "1" in str(zone) or "A" in str(zone) else "B" if "2" in str(zone) or "B" in str(zone) else "C"
    
    for _ in range(100):
        num = random.randint(1, 99)
        candidate = f"Slot {zone_code}-{num:02d}"
        if candidate not in occupied_slots:
            return candidate

    rnd_extra = random.randint(100, 999)
    return f"Slot {zone_code}-{rnd_extra}"

@app.route('/api/parking-bookings', methods=['POST', 'GET'])
def book_parking():
    # GET → alias for /api/parking-bookings/history (email-filtered list)
    if request.method == 'GET':
        raw_email = request.args.get('user_email') or request.args.get('email')
        if not raw_email or not raw_email.strip():
            return jsonify({"status": "success", "bookings": []})
        user_email = raw_email.strip().lower()
        raw_sqlite = db.get_parking_bookings(user_email)
        seen_keys = set()
        combined = []
        if supabase is not None:
            try:
                bookings_resp = supabase.table('parking_bookings').select('*').ilike('user_email', user_email).order('id', desc=True).execute()
                for booking in (bookings_resp.data or []):
                    key = f"{booking.get('zone')}_{booking.get('plate_number')}_{booking.get('id')}"
                    seen_keys.add(key)
                    combined.append(booking)
            except Exception as e:
                print("[SUPABASE QUERY NOTICE]:", str(e))
        for b in raw_sqlite:
            key = f"{b.get('zone')}_{b.get('plate_number')}_{b.get('id')}"
            if key not in seen_keys:
                seen_keys.add(key)
                combined.append(dict(b))
        combined.sort(key=lambda x: str(x.get('created_at', '')), reverse=True)
        return jsonify({"status": "success", "bookings": combined})

    data = request.json or {}
    user_email = data.get('user_email')
    zone = data.get('zone')
    hours = data.get('hours')
    plate_number = data.get('plate_number')
    payment_method = data.get('payment_method')
    total_price = data.get('total_price')

    if not user_email or not zone or hours is None or not plate_number or total_price is None:
        return jsonify({"status": "error", "message": "Missing required parking booking fields"}), 400

    rnd_suffix = "".join(random.choices("0123456789", k=6))
    booking_id = f"PRK-{rnd_suffix}"
    slot_number = data.get('slot_number') or generate_unique_parking_slot(zone)
    terminal = data.get('terminal') or ("Terminal 1" if "1" in str(zone) or "A" in str(zone) else "Terminal 2")

    now = datetime.datetime.now()
    entry_time = now.strftime("%Y-%m-%d %H:%M")
    exit_time = (now + datetime.timedelta(hours=int(hours or 2))).strftime("%Y-%m-%d %H:%M")

    sqlite_booking = None
    try:
        sqlite_booking = db.book_parking(user_email, zone, hours, plate_number, payment_method, total_price, booking_id, slot_number, terminal)
    except Exception as e:
        print("[SQLITE SYNC ERROR]:", str(e))

    booking_record = {
        'booking_id': booking_id,
        'user_email': user_email.strip().lower(),
        'zone': zone,
        'slot_number': slot_number,
        'terminal': terminal,
        'duration_hours': hours,
        'entry_time': entry_time,
        'exit_time': exit_time,
        'hours': hours,
        'plate_number': plate_number,
        'payment_method': payment_method,
        'total_price': total_price,
        'status': 'Confirmed'
    }

    if supabase is not None:
        try:
            booking_resp = supabase.table('parking_bookings').insert(booking_record).execute()
            if booking_resp.data:
                return jsonify({"status": "success", "message": "Parking booked successfully", "booking": booking_resp.data[0]})
        except Exception as e:
            print("[SUPABASE SYNC ERROR]:", str(e))

    if sqlite_booking:
        return jsonify({"status": "success", "message": "Parking booked successfully", "booking": sqlite_booking})
    return jsonify({"status": "success", "message": "Parking booked successfully", "booking": booking_record})

@app.route('/api/parking-bookings/history', methods=['GET'])
def parking_booking_history():
    raw_email = request.args.get('user_email') or request.args.get('email')
    if not raw_email or not raw_email.strip():
        return jsonify({"status": "success", "bookings": []})
    user_email = raw_email.strip().lower()
    raw_sqlite = db.get_parking_bookings(user_email)
    seen_keys = set()
    combined = []
    
    if supabase is not None:
        try:
            bookings_resp = supabase.table('parking_bookings').select('*').ilike('user_email', user_email).order('id', desc=True).execute()
            for booking in (bookings_resp.data or []):
                key = f"{booking.get('zone')}_{booking.get('plate_number')}_{booking.get('id')}"
                seen_keys.add(key)
                combined.append(booking)
        except Exception as e:
            print("[SUPABASE QUERY NOTICE]:", str(e))
            
    for b in raw_sqlite:
        key = f"{b.get('zone')}_{b.get('plate_number')}_{b.get('id')}"
        if key not in seen_keys:
            seen_keys.add(key)
            combined.append(dict(b))
            
    combined.sort(key=lambda x: str(x.get('created_at', '')), reverse=True)
    return jsonify({"status": "success", "bookings": combined})

# --- LOST AND FOUND ENDPOINTS ---

@app.route('/api/lost-items', methods=['GET'])
def get_lost_items():
    search = (request.args.get('search') or '').strip().lower()
    category_filter = (request.args.get('category') or '').strip().lower()
    status_filter = (request.args.get('status') or '').strip().lower()
    type_filter = (request.args.get('type') or '').strip().lower()

    items_map = {}

    # 1. Fetch from local SQLite
    try:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM lost_items ORDER BY id DESC")
        rows = cursor.fetchall()
        conn.close()
        for row in rows:
            d = dict(row)
            key = (str(d.get('name', '')).lower(), str(d.get('location', '')).lower(), str(d.get('contact', '')).lower())
            items_map[key] = d
    except Exception as e:
        print("[SQLITE LOST ITEMS]:", str(e))

    # 2. Fetch from Supabase cloud database if available
    if supabase is not None:
        try:
            res = supabase.table('lost_items').select('*').order('id', desc=True).execute()
            for item in (res.data or []):
                key = (str(item.get('name', '')).lower(), str(item.get('location', '')).lower(), str(item.get('contact', '')).lower())
                items_map[key] = item
        except Exception as e:
            print("[SUPABASE LOST ITEMS]:", str(e))

    items = list(items_map.values())

    # Apply Filtering
    filtered = []
    for item in items:
        name = str(item.get('name', '')).lower()
        desc = str(item.get('description', '')).lower()
        loc = str(item.get('location', '')).lower()
        cat = str(item.get('category', '')).lower()
        st = str(item.get('status', 'Pending')).lower()
        tp = str(item.get('type', 'Lost')).lower()

        if search and (search not in name and search not in desc and search not in loc):
            continue
        if category_filter and category_filter != 'all' and category_filter not in cat:
            continue
        if status_filter and status_filter != 'all' and status_filter != st:
            continue
        if type_filter and type_filter != 'all' and type_filter != tp:
            continue
        filtered.append(item)

    return jsonify({"status": "success", "items": filtered})

@app.route('/api/lost-items', methods=['POST'])
def add_lost_item():
    data = request.json or {}
    name = data.get('name')
    category = data.get('category', 'General')
    description = data.get('description', '')
    location = data.get('location') or data.get('terminal', 'Terminal 1')
    contact = data.get('contact') or data.get('contact_method', '')
    v_type = data.get('type', 'Lost')
    icon = data.get('icon', '📦')
    image = data.get('image') or data.get('photo', '')
    reporter_name = data.get('reporter_name') or data.get('name', 'Anonymous')
    user_email = data.get('user_email') or data.get('email') or getattr(request, 'user_email', None)

    if not name or not location or not contact:
        return jsonify({"status": "error", "message": "Missing required fields: name, location/terminal, contact"}), 400

    conn = db.get_conn()
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO lost_items (name, category, description, location, contact, type, icon, image, reporter_name, status, user_email)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Pending', ?)
    """, (name, category, description, location, contact, v_type, icon, image, reporter_name, user_email))
    conn.commit()
    conn.close()

    if supabase is not None:
        try:
            supabase.table('lost_items').insert({
                "name": name,
                "category": category,
                "description": description,
                "location": location,
                "contact": contact,
                "type": v_type,
                "icon": icon,
                "image": image,
                "reporter_name": reporter_name,
                "status": "Pending",
                "user_email": user_email
            }).execute()
        except Exception as se:
            print("[SUPABASE LOST ITEM SYNC ERROR]:", str(se))

    return jsonify({"status": "success", "message": "Lost & Found report submitted successfully"})

@app.route('/api/lost-items/status', methods=['POST', 'PUT'])
def update_lost_item_status():
    data = request.json or {}
    item_id = data.get('id')
    new_status = data.get('status', 'Resolved')
    if not item_id:
        return jsonify({"status": "error", "message": "Missing item id"}), 400

    try:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("UPDATE lost_items SET status = ? WHERE id = ?", (new_status, item_id))
        conn.commit()
        conn.close()

        if supabase is not None:
            try:
                supabase.table('lost_items').update({'status': new_status}).eq('id', item_id).execute()
            except Exception as se:
                print("[UPDATE LOST ITEM STATUS] Supabase sync notice:", str(se))

        return jsonify({"status": "success", "message": f"Item status updated to {new_status}"})
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/lost-items/delete', methods=['POST'])
def delete_lost_item():
    data = request.json or {}
    item_id = data.get('id')
    if not item_id:
        return jsonify({"status": "error", "message": "Missing item id"}), 400

    try:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("DELETE FROM lost_items WHERE id = ?", (item_id,))
        conn.commit()
        conn.close()

        if supabase is not None:
            try:
                supabase.table('lost_items').delete().eq('id', item_id).execute()
            except Exception as se:
                print("[DELETE LOST ITEM] Supabase sync notice:", str(se))

        return jsonify({"status": "success", "message": "Item resolved and removed successfully"})
    except Exception as e:
        print("[DELETE LOST ITEM] Error:", str(e))
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/guides/<key>', methods=['GET'])
def get_guide_by_key(key):
    conn = db.get_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM guides WHERE key = ?", (key,))
    row = cursor.fetchone()
    conn.close()
    if row:
        return jsonify({"status": "success", "guide": dict(row)})
    else:
        return jsonify({"status": "error", "message": "Guide not found"}), 404

# --- VENDOR MANAGEMENT ENDPOINTS ---

def get_vendor_by_email(email):
    if USE_SQLITE:
        return db.get_vendor(email)
    else:
        try:
            resp = supabase.table('vendors').select('*').eq('email', email).execute()
            return resp.data[0] if resp.data else None
        except Exception:
            return db.get_vendor(email)

def get_lounge_booking_by_id(booking_id):
    if USE_SQLITE:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM lounge_bookings WHERE id = ?", (booking_id,))
        row = cursor.fetchone()
        conn.close()
        return dict(row) if row else None
    else:
        try:
            resp = supabase.table('lounge_bookings').select('*').eq('id', booking_id).execute()
            return resp.data[0] if resp.data else None
        except Exception:
            conn = db.get_conn()
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM lounge_bookings WHERE id = ?", (booking_id,))
            row = cursor.fetchone()
            conn.close()
            return dict(row) if row else None

@app.route('/api/vendors/orders', methods=['GET'])
@token_required(['vendor', 'admin'])
def get_vendor_orders():
    vendor_id = request.args.get('vendor_id')
    if not vendor_id:
        return jsonify({"status": "error", "message": "Missing vendor_id parameter"}), 400
        
    if request.user_role == 'vendor':
        vendor = get_vendor_by_email(request.user_email)
        if not vendor or str(vendor.get('id')) != str(vendor_id):
            return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403
            
    if USE_SQLITE:
        return jsonify({"status": "success", "orders": db.get_vendor_orders(vendor_id)})
    try:
        orders_resp = supabase.table('orders').select('*').eq('vendor_id', vendor_id).order('id', desc=True).execute()
        orders = orders_resp.data or []
        if orders:
            order_ids = [order['id'] for order in orders]
            items_resp = supabase.table('order_items').select('*').in_('order_id', order_ids).execute()
            all_items = items_resp.data or []
            items_by_order = {}
            for item in all_items:
                oid = item.get('order_id')
                items_by_order.setdefault(oid, []).append(item)
            for order in orders:
                order['items'] = items_by_order.get(order['id'], [])
        return jsonify({"status": "success", "orders": orders})
    except Exception as e:
        print("[FALLBACK] Supabase vendor orders fetch error:", str(e))
        return jsonify({"status": "success", "orders": db.get_vendor_orders(vendor_id)})

@app.route('/api/vendors/orders/<int:order_id>/status', methods=['POST'])
@token_required(['vendor', 'admin'])
def update_order_status(order_id):
    data = request.json or {}
    new_status = data.get('status')
    
    order = None
    if USE_SQLITE:
        order = db.get_order(order_id)
    else:
        try:
            orders_resp = supabase.table('orders').select('*').eq('id', order_id).execute()
            order = orders_resp.data[0] if orders_resp.data else None
        except Exception:
            order = db.get_order(order_id)
            
    if not order:
        return jsonify({"status": "error", "message": "Order not found"}), 404
        
    if request.user_role == 'vendor':
        vendor = get_vendor_by_email(request.user_email)
        if not vendor or order.get('vendor_id') != vendor.get('id'):
            return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

    if USE_SQLITE:
        updated_order = db.update_order_status(order_id, new_status)
        if updated_order:
            return jsonify({"status": "success", "message": "Order status updated", "order": updated_order})
        return jsonify({"status": "error", "message": "Order not found"}), 404
    try:
        response = supabase.table('orders').update({'status': new_status}).eq('id', order_id).execute()
        if response.data:
            return jsonify({"status": "success", "message": "Order status updated", "order": response.data[0]})
        return jsonify({"status": "error", "message": "Order not found"}), 404
    except Exception as e:
        print("[FALLBACK] Supabase update order status error:", str(e))
        updated_order = db.update_order_status(order_id, new_status)
        if updated_order:
            return jsonify({"status": "success", "message": "Order status updated", "order": updated_order})
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/api/vendors/bookings', methods=['GET'])
@token_required(['vendor', 'admin'])
def get_vendor_bookings():
    vendor_id = request.args.get('vendor_id')
    if not vendor_id:
        return jsonify({"status": "error", "message": "Missing vendor_id parameter"}), 400
        
    if request.user_role == 'vendor':
        vendor = get_vendor_by_email(request.user_email)
        if not vendor or str(vendor.get('id')) != str(vendor_id):
            return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403
            
    if USE_SQLITE:
        return jsonify({"status": "success", "bookings": db.get_vendor_bookings(vendor_id)})
    try:
        bookings_resp = supabase.table('lounge_bookings').select('*').eq('vendor_id', vendor_id).order('id', desc=True).execute()
        return jsonify({"status": "success", "bookings": bookings_resp.data or []})
    except Exception as e:
        print("[FALLBACK] Supabase vendor bookings fetch error:", str(e))
        return jsonify({"status": "success", "bookings": db.get_vendor_bookings(vendor_id)})

@app.route('/api/vendors/bookings/<int:booking_id>/status', methods=['POST'])
@token_required(['vendor', 'admin'])
def update_booking_status(booking_id):
    data = request.json or {}
    new_status = data.get('status')
    
    booking = get_lounge_booking_by_id(booking_id)
    if not booking:
        return jsonify({"status": "error", "message": "Booking not found"}), 404
        
    if request.user_role == 'vendor':
        vendor = get_vendor_by_email(request.user_email)
        if not vendor or booking.get('vendor_id') != vendor.get('id'):
            return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

    if USE_SQLITE:
        updated_booking = db.update_booking_status(booking_id, new_status)
        if updated_booking:
            return jsonify({"status": "success", "message": "Booking status updated", "booking": updated_booking})
        return jsonify({"status": "error", "message": "Booking not found"}), 404
    try:
        response = supabase.table('lounge_bookings').update({'status': new_status}).eq('id', booking_id).execute()
        if response.data:
            return jsonify({"status": "success", "message": "Booking status updated", "booking": response.data[0]})
        return jsonify({"status": "error", "message": "Booking not found"}), 404
    except Exception as e:
        print("[FALLBACK] Supabase update booking status error:", str(e))
        updated_booking = db.update_booking_status(booking_id, new_status)
        if updated_booking:
            return jsonify({"status": "success", "message": "Booking status updated", "booking": updated_booking})
        return jsonify({"status": "error", "message": str(e)}), 500

def get_product_by_id(product_id):
    if USE_SQLITE:
        conn = db.get_conn()
        cursor = conn.cursor()
        cursor.execute("SELECT * FROM products WHERE id = ?", (product_id,))
        row = cursor.fetchone()
        conn.close()
        return dict(row) if row else None
    else:
        try:
            resp = supabase.table('products').select('*').eq('id', product_id).execute()
            return resp.data[0] if resp.data else None
        except Exception:
            conn = db.get_conn()
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM products WHERE id = ?", (product_id,))
            row = cursor.fetchone()
            conn.close()
            return dict(row) if row else None

@app.route('/api/vendors/products', methods=['GET', 'POST', 'PUT', 'DELETE'])
@token_required(['vendor', 'admin'])
def manage_vendor_products():
    if request.method == 'GET':
        vendor_id = request.args.get('vendor_id')
        if not vendor_id:
            return jsonify({"status": "error", "message": "Missing vendor_id parameter"}), 400
            
        if request.user_role == 'vendor':
            vendor = get_vendor_by_email(request.user_email)
            if not vendor or str(vendor.get('id')) != str(vendor_id):
                return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

        if USE_SQLITE:
            return jsonify({"status": "success", "products": db.get_products(vendor_id)})
        try:
            response = supabase.table('products').select('*').eq('vendor_id', vendor_id).execute()
            return jsonify({"status": "success", "products": response.data or []})
        except Exception as e:
            print("[FALLBACK] Supabase products query error:", str(e))
            return jsonify({"status": "success", "products": db.get_products(vendor_id)})

    elif request.method == 'POST':
        data = request.json or {}
        vendor_id = data.get('vendor_id')
        if not vendor_id:
            return jsonify({"status": "error", "message": "Missing vendor_id parameter"}), 400
            
        if request.user_role == 'vendor':
            vendor = get_vendor_by_email(request.user_email)
            if not vendor or str(vendor.get('id')) != str(vendor_id):
                return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

        if USE_SQLITE:
            product = db.add_product(
                vendor_id,
                data.get('name'),
                data.get('price'),
                data.get('category'),
                data.get('description', ''),
                data.get('image_url', '')
            )
            return jsonify({"status": "success", "message": "Product added successfully", "product": product})
        try:
            ins_resp = supabase.table('products').insert({
                'vendor_id': vendor_id,
                'name': data.get('name'),
                'price': data.get('price'),
                'rating': 5.0,
                'image_url': data.get('image_url', ''),
                'category': data.get('category'),
                'description': data.get('description', '')
            }).execute()
            return jsonify({"status": "success", "message": "Product added successfully", "product": ins_resp.data[0]})
        except Exception as e:
            print("[FALLBACK] Supabase product insert error:", str(e))
            product = db.add_product(
                vendor_id,
                data.get('name'),
                data.get('price'),
                data.get('category'),
                data.get('description', ''),
                data.get('image_url', '')
            )
            return jsonify({"status": "success", "message": "Product added successfully", "product": product})

    elif request.method == 'PUT':
        data = request.json or {}
        product_id = data.get('id')
        if not product_id:
            return jsonify({"status": "error", "message": "Missing product id"}), 400
            
        product = get_product_by_id(product_id)
        if not product:
            return jsonify({"status": "error", "message": "Product not found"}), 404
            
        if request.user_role == 'vendor':
            vendor = get_vendor_by_email(request.user_email)
            if not vendor or str(vendor.get('id')) != str(product.get('vendor_id')):
                return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

        if USE_SQLITE:
            updated_product = db.update_product(
                product_id,
                data.get('name'),
                data.get('price'),
                data.get('category'),
                data.get('description'),
                data.get('image_url')
            )
            return jsonify({"status": "success", "message": "Product updated successfully", "product": updated_product})
        try:
            upd_resp = supabase.table('products').update({
                'name': data.get('name'),
                'price': data.get('price'),
                'category': data.get('category'),
                'description': data.get('description'),
                'image_url': data.get('image_url')
            }).eq('id', product_id).execute()
            return jsonify({"status": "success", "message": "Product updated successfully", "product": upd_resp.data[0]})
        except Exception as e:
            print("[FALLBACK] Supabase product update error:", str(e))
            updated_product = db.update_product(
                product_id,
                data.get('name'),
                data.get('price'),
                data.get('category'),
                data.get('description'),
                data.get('image_url')
            )
            return jsonify({"status": "success", "message": "Product updated successfully", "product": updated_product})

    elif request.method == 'DELETE':
        product_id = request.args.get('id')
        if not product_id:
            return jsonify({"status": "error", "message": "Missing product id"}), 400
            
        product = get_product_by_id(product_id)
        if not product:
            return jsonify({"status": "error", "message": "Product not found"}), 404
            
        if request.user_role == 'vendor':
            vendor = get_vendor_by_email(request.user_email)
            if not vendor or str(vendor.get('id')) != str(product.get('vendor_id')):
                return jsonify({"status": "error", "message": "Access Forbidden: Insufficient permissions"}), 403

        if USE_SQLITE:
            db.delete_product(product_id)
            return jsonify({"status": "success", "message": "Product deleted successfully"})
        try:
            supabase.table('products').delete().eq('id', product_id).execute()
            db.delete_product(product_id)
            return jsonify({"status": "success", "message": "Product deleted successfully"})
        except Exception as e:
            print("[FALLBACK] Supabase product delete error:", str(e))
            db.delete_product(product_id)
            return jsonify({"status": "success", "message": "Product deleted successfully"})

# --- IN-APP FLIGHT BOOKING & PAYMENT ENGINE ENDPOINTS ---
import urllib.request
import urllib.parse
import json

class AmadeusFlightClient:
    def __init__ (self):
        self.client_id = os.environ.get("AMADEUS_CLIENT_ID", "").strip()
        self.client_secret = os.environ.get("AMADEUS_CLIENT_SECRET", "").strip()
        self.env = os.environ.get("AMADEUS_ENV", "test").strip().lower()
        self.base_url = "https://api.amadeus.com" if self.env == "production" else "https://test.api.amadeus.com"
        self._access_token = None
        self._token_expires_at = 0

    def is_configured(self):
        return bool(self.client_id and self.client_secret)

    def _get_access_token(self):
        if not self.is_configured():
            return None
        now = time.time()
        if self._access_token and now < self._token_expires_at - 30:
            return self._access_token

        url = f"{self.base_url}/v1/security/oauth2/token"
        data = urllib.parse.urlencode({
            'grant_type': 'client_credentials',
            'client_id': self.client_id,
            'client_secret': self.client_secret
        }).encode('utf-8')

        req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/x-www-form-urlencoded'})
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                res_data = json.loads(resp.read().decode('utf-8'))
                self._access_token = res_data.get('access_token')
                expires_in = res_data.get('expires_in', 1799)
                self._token_expires_at = now + expires_in
                return self._access_token
        except Exception as e:
            print("[AMADEUS AUTH ERROR]:", str(e))
            return None

    def search_flights(self, origin, destination, date_str, passengers=1, cabin_class="Economy"):
        token = self._get_access_token()
        if not token:
            return None

        class_map = {
            "Economy": "ECONOMY",
            "Premium Economy": "PREMIUM_ECONOMY",
            "Business": "BUSINESS",
            "First": "FIRST"
        }
        travel_class = class_map.get(cabin_class, "ECONOMY")

        params = {
            "originLocationCode": origin,
            "destinationLocationCode": destination,
            "departureDate": date_str,
            "adults": str(passengers),
            "travelClass": travel_class,
            "currencyCode": "INR",
            "max": "10"
        }

        query_str = urllib.parse.urlencode(params)
        url = f"{self.base_url}/v2/shopping/flight-offers?{query_str}"

        req = urllib.request.Request(url, headers={
            'Authorization': f'Bearer {token}',
            'Accept': 'application/json'
        })

        try:
            with urllib.request.urlopen(req, timeout=12) as resp:
                res_data = json.loads(resp.read().decode('utf-8'))
                raw_offers = res_data.get('data', [])
                dictionaries = res_data.get('dictionaries', {})

                parsed_flights = []
                carrier_names = dictionaries.get('carriers', {})

                for offer in raw_offers:
                    offer_id = offer.get('id')
                    price_info = offer.get('price', {})
                    grand_total = float(price_info.get('grandTotal', price_info.get('total', 4500)))
                    price_per_pax = int(grand_total / max(1, passengers))

                    itineraries = offer.get('itineraries', [])
                    if not itineraries:
                        continue

                    segments = itineraries[0].get('segments', [])
                    if not segments:
                        continue

                    first_seg = segments[0]
                    last_seg = segments[-1]

                    carrier_code = first_seg.get('carrierCode', 'AI')
                    number = first_seg.get('number', '101')
                    flight_num = f"{carrier_code}-{number}"
                    airline_name = carrier_names.get(carrier_code, CARRIER_NAME_MAP.get(carrier_code, f"Airline {carrier_code}"))

                    dep_time = first_seg.get('departure', {}).get('at', '').split('T')[-1][:5] or "08:00"
                    arr_time = last_seg.get('arrival', {}).get('at', '').split('T')[-1][:5] or "10:15"
                    duration_raw = itineraries[0].get('duration', 'PT2H15M').replace('PT', '').lower()
                    duration = duration_raw.replace('h', 'h ').replace('m', 'm').strip()

                    num_stops = len(segments) - 1
                    stops_str = "Non-stop" if num_stops == 0 else f"{num_stops} Stop{'s' if num_stops > 1 else ''}"

                    aircraft_code = first_seg.get('aircraft', {}).get('code', '32N')

                    parsed_flights.append({
                        "id": f"AMAD-{offer_id}-{flight_num}",
                        "flight_number": flight_num,
                        "airline": airline_name,
                        "airline_code": carrier_code,
                        "airline_logo": CARRIER_LOGO_MAP.get(carrier_code, "✈️"),
                        "airline_color": CARRIER_COLOR_MAP.get(carrier_code, "#1E3A8A"),
                        "origin": origin,
                        "origin_name": AIRPORTS_MAPPING.get(origin, f"Airport ({origin})"),
                        "destination": destination,
                        "destination_name": AIRPORTS_MAPPING.get(destination, f"Airport ({destination})"),
                        "departure_date": date_str,
                        "departure_time": dep_time,
                        "arrival_time": arr_time,
                        "duration": duration,
                        "stops": stops_str,
                        "cabin_class": cabin_class,
                        "passengers": passengers,
                        "price_per_pax": price_per_pax,
                        "total_fare": int(grand_total),
                        "baggage": "25 kg Check-in + 7 kg Hand Bag",
                        "aircraft": f"Aircraft {aircraft_code}",
                        "terminal": f"Terminal {first_seg.get('departure', {}).get('terminal', '1')}",
                        "gate": f"Gate {random.randint(1, 30)}",
                        "raw_amadeus_offer": offer
                    })

                return parsed_flights
        except Exception as e:
            print("[AMADEUS FLIGHT SEARCH ERROR]:", str(e))
            return None

class DuffelFlightClient:
    def __init__(self):
        self.api_key = os.environ.get("DUFFEL_API_KEY", "").strip()
        self.base_url = "https://api.duffel.com"

    def is_configured(self):
        return bool(self.api_key)

    def search_flights(self, origin, destination, date_str, passengers=1, cabin_class="Economy"):
        if not self.is_configured():
            return None

        class_map = {
            "Economy": "economy",
            "Premium Economy": "premium_economy",
            "Business": "business",
            "First": "first"
        }
        cabin = class_map.get(cabin_class, "economy")

        pax_list = [{"type": "adult"} for _ in range(passengers)]
        payload = {
            "data": {
                "slices": [{
                    "origin": origin,
                    "destination": destination,
                    "departure_date": date_str
                }],
                "passengers": pax_list,
                "cabin_class": cabin
            }
        }

        url = f"{self.base_url}/air/offer_requests?return_offers=true"
        req_data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=req_data, headers={
            "Authorization": f"Bearer {self.api_key}",
            "Duffel-Version": "v2",
            "Content-Type": "application/json",
            "Accept": "application/json"
        })

        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                res_data = json.loads(resp.read().decode('utf-8'))
                raw_offers = res_data.get('data', {}).get('offers', [])

                parsed_flights = []
                for offer in raw_offers[:10]:
                    offer_id = offer.get('id')
                    total_amount = float(offer.get('total_amount', 4500))
                    price_per_pax = int(total_amount / max(1, passengers))
                    owner = offer.get('owner', {})
                    airline_name = owner.get('name', 'Airline')
                    carrier_code = owner.get('iata_code', 'FL')
                    logo_url = owner.get('logo_symbol_url') or CARRIER_LOGO_MAP.get(carrier_code, '✈️')

                    slices = offer.get('slices', [])
                    if not slices:
                        continue
                    segments = slices[0].get('segments', [])
                    if not segments:
                        continue

                    first_seg = segments[0]
                    last_seg = segments[-1]
                    flight_num = f"{carrier_code}-{first_seg.get('operating_carrier_flight_number') or first_seg.get('marketing_carrier_flight_number') or '101'}"

                    dep_at = first_seg.get('departing_at', '')
                    arr_at = last_seg.get('arriving_at', '')
                    dep_time = dep_at.split('T')[-1][:5] if 'T' in dep_at else "08:00"
                    arr_time = arr_at.split('T')[-1][:5] if 'T' in arr_at else "10:15"

                    duration_raw = slices[0].get('duration', 'PT2H15M').replace('PT', '').lower()
                    duration = duration_raw.replace('h', 'h ').replace('m', 'm').strip()
                    num_stops = len(segments) - 1
                    stops_str = "Non-stop" if num_stops == 0 else f"{num_stops} Stop{'s' if num_stops > 1 else ''}"

                    parsed_flights.append({
                        "id": f"DUFF-{offer_id}-{flight_num}",
                        "flight_number": flight_num,
                        "airline": airline_name,
                        "airline_code": carrier_code,
                        "airline_logo": logo_url if isinstance(logo_url, str) and logo_url.startswith("http") else CARRIER_LOGO_MAP.get(carrier_code, "✈️"),
                        "airline_color": CARRIER_COLOR_MAP.get(carrier_code, "#1E3A8A"),
                        "origin": origin,
                        "origin_name": AIRPORTS_MAPPING.get(origin, f"Airport ({origin})"),
                        "destination": destination,
                        "destination_name": AIRPORTS_MAPPING.get(destination, f"Airport ({destination})"),
                        "departure_date": date_str,
                        "departure_time": dep_time,
                        "arrival_time": arr_time,
                        "duration": duration,
                        "stops": stops_str,
                        "cabin_class": cabin_class,
                        "passengers": passengers,
                        "price_per_pax": price_per_pax,
                        "total_fare": int(total_amount),
                        "baggage": "25 kg Check-in + 7 kg Hand Bag",
                        "aircraft": first_seg.get('aircraft', {}).get('name', 'Airbus A320'),
                        "terminal": f"Terminal {first_seg.get('origin_terminal', '1') or '1'}",
                        "gate": f"Gate {random.randint(1, 30)}",
                        "raw_duffel_offer": offer
                    })
                return parsed_flights
        except Exception as e:
            print("[DUFFEL FLIGHT SEARCH ERROR]:", str(e))
            return None

class AviationStackClient:
    def __init__(self):
        self.api_key = os.environ.get("AVIATIONSTACK_API_KEY", "").strip()
        self.base_url = "http://api.aviationstack.com/v1/flights"

    def is_configured(self):
        return bool(self.api_key)

    def search_flights(self, origin, destination, date_str, passengers=1, cabin_class="Economy"):
        if not self.is_configured():
            return None

        params = {
            "access_key": self.api_key,
            "dep_iata": origin,
            "arr_iata": destination,
            "limit": "10"
        }
        query_str = urllib.parse.urlencode(params)
        url = f"{self.base_url}?{query_str}"

        req = urllib.request.Request(url, headers={"User-Agent": "AeroAssistAI/1.0"})
        try:
            with urllib.request.urlopen(req, timeout=12) as resp:
                res_data = json.loads(resp.read().decode('utf-8'))
                raw_flights = res_data.get('data', [])

                multiplier = 1.0 if cabin_class == 'Economy' else 1.8 if cabin_class == 'Premium Economy' else 2.8

                parsed_flights = []
                for idx, f in enumerate(raw_flights):
                    flight_info = f.get('flight', {})
                    airline_info = f.get('airline', {})
                    departure_info = f.get('departure', {})
                    arrival_info = f.get('arrival', {})

                    flight_num = flight_info.get('iata') or f"{airline_info.get('iata', 'AI')}-{flight_info.get('number', '101')}"
                    airline_name = airline_info.get('name') or "Airline"
                    carrier_code = airline_info.get('iata') or "AI"

                    dep_time = departure_info.get('scheduled', '').split('T')[-1][:5] if 'T' in str(departure_info.get('scheduled', '')) else "08:00"
                    arr_time = arrival_info.get('scheduled', '').split('T')[-1][:5] if 'T' in str(arrival_info.get('scheduled', '')) else "10:15"

                    base_price = 4500 + (idx * 650)
                    price_per_pax = int(base_price * multiplier)

                    parsed_flights.append({
                        "id": f"AVST-{flight_num}-{date_str}",
                        "flight_number": flight_num,
                        "airline": airline_name,
                        "airline_code": carrier_code,
                        "airline_logo": CARRIER_LOGO_MAP.get(carrier_code, "✈️"),
                        "airline_color": CARRIER_COLOR_MAP.get(carrier_code, "#1E3A8A"),
                        "origin": origin,
                        "origin_name": AIRPORTS_MAPPING.get(origin, f"Airport ({origin})"),
                        "destination": destination,
                        "destination_name": AIRPORTS_MAPPING.get(destination, f"Airport ({destination})"),
                        "departure_date": date_str,
                        "departure_time": dep_time,
                        "arrival_time": arr_time,
                        "duration": "2h 15m",
                        "stops": "Non-stop",
                        "cabin_class": cabin_class,
                        "passengers": passengers,
                        "price_per_pax": price_per_pax,
                        "total_fare": price_per_pax * passengers,
                        "baggage": "25 kg Check-in + 7 kg Hand Bag",
                        "aircraft": f.get('aircraft', {}).get('iata', 'Airbus A320neo') if f.get('aircraft') else 'Airbus A320neo',
                        "terminal": f"Terminal {departure_info.get('terminal', '1') or '1'}",
                        "gate": f"Gate {departure_info.get('gate') or random.randint(1, 30)}"
                    })
                return parsed_flights
        except Exception as e:
            print("[AVIATIONSTACK SEARCH ERROR]:", str(e))
            return None

aviationstack_client = AviationStackClient()
duffel_client = DuffelFlightClient()
amadeus_client = AmadeusFlightClient()

CARRIER_NAME_MAP = {
    "AI": "Air India", "6E": "IndiGo", "UK": "Vistara", "SG": "SpiceJet",
    "EK": "Emirates", "SQ": "Singapore Airlines", "QR": "Qatar Airways",
    "BA": "British Airways", "AA": "American Airlines", "LH": "Lufthansa", "G8": "Go First"
}
CARRIER_LOGO_MAP = {
    "AI": "✈️", "6E": "💙", "UK": "✨", "SG": "🌶️", "EK": "👑", "SQ": "🌟", "QR": "🇶🇦"
}
CARRIER_COLOR_MAP = {
    "AI": "#E11B22", "6E": "#002B7F", "UK": "#4A154B", "SG": "#FF6F00", "EK": "#D71921"
}

AIRLINES_DATA = [
    {"name": "Air India", "code": "AI", "logo": "✈️", "color": "#E11B22"},
    {"name": "IndiGo", "code": "6E", "logo": "💙", "color": "#002B7F"},
    {"name": "Vistara", "code": "UK", "logo": "✨", "color": "#4A154B"},
    {"name": "Emirates", "code": "EK", "logo": "👑", "color": "#D71921"},
    {"name": "Singapore Airlines", "code": "SQ", "logo": "🌟", "color": "#00205B"},
    {"name": "Qatar Airways", "code": "QR", "logo": "🇶🇦", "color": "#5C0632"},
    {"name": "SpiceJet", "code": "SG", "logo": "🌶️", "color": "#FF6F00"}
]

AIRPORTS_MAPPING = {
    "DEL": "New Delhi (DEL)",
    "BOM": "Mumbai (BOM)",
    "MAA": "Chennai (MAA)",
    "BLR": "Bengaluru (BLR)",
    "HYD": "Hyderabad (HYD)",
    "DXB": "Dubai (DXB)",
    "SIN": "Singapore (SIN)",
    "LHR": "London Heathrow (LHR)",
    "JFK": "New York (JFK)"
}

@app.route('/api/flights/search', methods=['GET'])
def search_flights():
    origin = (request.args.get('origin') or 'DEL').upper().strip()
    destination = (request.args.get('destination') or 'BOM').upper().strip()
    date_str = request.args.get('date') or datetime.date.today().strftime('%Y-%m-%d')
    passengers = int(request.args.get('passengers') or 1)
    cabin_class = request.args.get('cabin_class') or 'Economy'
    
    # 1. Try AviationStack API first (Instant Key directly on homepage dashboard)
    if aviationstack_client.is_configured():
        avst_flights = aviationstack_client.search_flights(origin, destination, date_str, passengers, cabin_class)
        if avst_flights and len(avst_flights) > 0:
            return jsonify({
                "status": "success",
                "source": "aviationstack_realtime_api",
                "origin": origin,
                "destination": destination,
                "date": date_str,
                "passengers": passengers,
                "cabin_class": cabin_class,
                "count": len(avst_flights),
                "flights": avst_flights
            })

    # 2. Try Duffel API
    if duffel_client.is_configured():
        duffel_flights = duffel_client.search_flights(origin, destination, date_str, passengers, cabin_class)
        if duffel_flights and len(duffel_flights) > 0:
            return jsonify({
                "status": "success",
                "source": "duffel_realtime_api",
                "origin": origin,
                "destination": destination,
                "date": date_str,
                "passengers": passengers,
                "cabin_class": cabin_class,
                "count": len(duffel_flights),
                "flights": duffel_flights
            })


    # 2. Try Amadeus Enterprise API
    if amadeus_client.is_configured():
        amadeus_flights = amadeus_client.search_flights(origin, destination, date_str, passengers, cabin_class)
        if amadeus_flights and len(amadeus_flights) > 0:
            return jsonify({
                "status": "success",
                "source": "amadeus_realtime_api",
                "origin": origin,
                "destination": destination,
                "date": date_str,
                "passengers": passengers,
                "cabin_class": cabin_class,
                "count": len(amadeus_flights),
                "flights": amadeus_flights
            })


    # Smooth Fallback: Generate realistic dynamic flight schedule based on origin-destination hash seed
    flights = []
    base_seed = sum(ord(c) for c in origin + destination + date_str)
    
    schedules = [
        {"dep": "06:00", "arr": "08:15", "duration": "2h 15m", "stops": "Non-stop", "base_price": 4500},
        {"dep": "09:30", "arr": "11:45", "duration": "2h 15m", "stops": "Non-stop", "base_price": 5200},
        {"dep": "13:15", "arr": "15:40", "duration": "2h 25m", "stops": "Non-stop", "base_price": 4800},
        {"dep": "17:45", "arr": "20:05", "duration": "2h 20m", "stops": "Non-stop", "base_price": 6100},
        {"dep": "21:10", "arr": "23:30", "duration": "2h 20m", "stops": "Non-stop", "base_price": 3990},
        {"dep": "11:00", "arr": "16:30", "duration": "5h 30m", "stops": "1 Stop (HYD)", "base_price": 7500}
    ]

    for idx, sch in enumerate(schedules):
        airline = AIRLINES_DATA[(base_seed + idx) % len(AIRLINES_DATA)]
        flight_num = f"{airline['code']}-{(base_seed * (idx + 1)) % 900 + 100}"
        multiplier = 1.0 if cabin_class == 'Economy' else 1.8 if cabin_class == 'Premium Economy' else 2.8
        price_per_pax = int(sch['base_price'] * multiplier)
        total_fare = price_per_pax * passengers
        
        flights.append({
            "id": f"FL-{flight_num}-{date_str}",
            "flight_number": flight_num,
            "airline": airline["name"],
            "airline_code": airline["code"],
            "airline_logo": airline["logo"],
            "airline_color": airline["color"],
            "origin": origin,
            "origin_name": AIRPORTS_MAPPING.get(origin, f"Airport ({origin})"),
            "destination": destination,
            "destination_name": AIRPORTS_MAPPING.get(destination, f"Airport ({destination})"),
            "departure_date": date_str,
            "departure_time": sch["dep"],
            "arrival_time": sch["arr"],
            "duration": sch["duration"],
            "stops": sch["stops"],
            "cabin_class": cabin_class,
            "passengers": passengers,
            "price_per_pax": price_per_pax,
            "total_fare": total_fare,
            "baggage": "25 kg Check-in + 7 kg Hand Bag",
            "aircraft": "Airbus A320neo" if idx % 2 == 0 else "Boeing 787 Dreamliner",
            "terminal": f"Terminal {1 if idx % 2 == 0 else 2}",
            "gate": f"Gate {random.randint(1, 30)}"
        })

    return jsonify({
        "status": "success",
        "source": "dynamic_schedule_generator",
        "origin": origin,
        "destination": destination,
        "date": date_str,
        "passengers": passengers,
        "cabin_class": cabin_class,
        "count": len(flights),
        "flights": flights
    })


@app.route('/api/flights/seats', methods=['GET'])
def get_flight_seats():
    flight_number = request.args.get('flight_number') or request.args.get('flight_code') or request.args.get('flight')
    departure_date = request.args.get('date') or datetime.date.today().strftime('%Y-%m-%d')
    if not flight_number:
        return jsonify({"status": "error", "message": "flight_number parameter is required"}), 400

    seats = db.get_booked_seats(flight_number, departure_date)
    booked_seat_numbers = [s['seat_number'] for s in seats]
    return jsonify({
        "status": "success",
        "flight_number": flight_number,
        "departure_date": departure_date,
        "booked_seats": booked_seat_numbers,
        "occupied_seats": booked_seat_numbers,   # alias for Android clients
        "seats_detail": seats
    })

@app.route('/api/flights/seats/book', methods=['POST'])
def book_flight_seat():
    data = request.json or {}
    flight_number = data.get('flight_number') or data.get('flight_code')
    departure_date = data.get('date') or datetime.date.today().strftime('%Y-%m-%d')
    seat_number = data.get('seat_number')
    booked_by = data.get('user_email') or data.get('email') or 'guest@aeroassist.ai'
    booking_id = data.get('booking_id')

    if not flight_number or not seat_number:
        return jsonify({"status": "error", "message": "flight_number and seat_number are required"}), 400

    success = db.book_seat(flight_number, departure_date, seat_number, booked_by, booking_id)
    if success:
        return jsonify({"status": "success", "message": f"Seat {seat_number} reserved successfully", "seat_number": seat_number})
    else:
        return jsonify({"status": "error", "message": f"Seat {seat_number} is already booked by another passenger"}), 409

@app.route('/api/flights/seats/release', methods=['POST'])
def release_flight_seat():
    data = request.json or {}
    flight_number = data.get('flight_number') or data.get('flight_code')
    departure_date = data.get('date') or datetime.date.today().strftime('%Y-%m-%d')
    seat_number = data.get('seat_number')

    if not flight_number or not seat_number:
        return jsonify({"status": "error", "message": "flight_number and seat_number are required"}), 400

    db.release_seat(flight_number, departure_date, seat_number)
    return jsonify({"status": "success", "message": f"Seat {seat_number} released successfully"})

@app.route('/api/flights/book', methods=['POST'])
def book_flight():
    data = request.json or {}
    user_email = (data.get('user_email') or data.get('email') or getattr(request, 'user_email', None) or 'guest@aeroassist.ai').strip().lower()
    
    flight_details = data.get('flight_details') or {}
    passenger_details = data.get('passenger_details') or []
    amount = float(data.get('amount') or flight_details.get('total_fare') or 0.0)
    payment_method = data.get('payment_method') or 'UPI'
    
    # Generate unique realistic demo identifiers
    rnd_suffix = "".join(random.choices("0123456789", k=6))
    booking_id = f"BK-{rnd_suffix}"
    pnr = "".join(random.choices("ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789", k=6))
    payment_id = f"PAY-{''.join(random.choices('0123456789', k=8))}"
    transaction_id = f"TXN-{''.join(random.choices('0123456789', k=10))}"
    ticket_number = f"TKT-{''.join(random.choices('0123456789', k=10))}"
    invoice_number = f"INV-2026-{''.join(random.choices('0123456789', k=5))}"
    
    origin = flight_details.get('origin', 'DEL')
    destination = flight_details.get('destination', 'BOM')
    departure_date = flight_details.get('departure_date', datetime.date.today().strftime('%Y-%m-%d'))
    flight_num = flight_details.get('flight_number', 'AI-432')

    # Reserve seat numbers in central inventory
    seats_assigned = data.get('seat_numbers') or []
    if not seats_assigned and isinstance(passenger_details, list):
        for p in passenger_details:
            if isinstance(p, dict) and p.get('seat'):
                seats_assigned.append(p.get('seat'))
    for s_num in seats_assigned:
        db.book_seat(flight_num, departure_date, s_num, user_email, booking_id)

    import json
    flight_json = json.dumps(flight_details)
    pax_json = json.dumps(passenger_details)
    
    booking_record = {
        "user_email": user_email,
        "booking_id": booking_id,
        "pnr": pnr,
        "payment_id": payment_id,
        "transaction_id": transaction_id,
        "ticket_number": ticket_number,
        "invoice_number": invoice_number,
        "origin": origin,
        "destination": destination,
        "departure_date": departure_date,
        "flight_details": flight_details,
        "passenger_details": passenger_details,
        "amount": amount,
        "payment_method": payment_method,
        "payment_status": "Demo Success",
        "booking_status": "Confirmed",
        "created_at": datetime.datetime.utcnow().isoformat()
    }
    
    # Always persist in Local SQLite as well for reliable cross-platform syncing
    try:
        db.create_flight_booking(
            user_email, booking_id, pnr, payment_id, transaction_id, ticket_number, invoice_number,
            origin, destination, departure_date, flight_json, pax_json, amount, payment_method
        )
    except Exception as e:
        print("[SQLITE SYNC ERROR]:", str(e))

    if supabase is not None:
        try:
            supabase.table('flight_bookings').insert({
                "user_email": user_email.lower(),
                "booking_id": booking_id,
                "pnr": pnr,
                "payment_id": payment_id,
                "transaction_id": transaction_id,
                "ticket_number": ticket_number,
                "invoice_number": invoice_number,
                "origin": origin,
                "destination": destination,
                "departure_date": departure_date,
                "flight_details": flight_json,
                "passenger_details": pax_json,
                "amount": amount,
                "payment_method": payment_method,
                "payment_status": "Demo Success",
                "booking_status": "Confirmed"
            }).execute()
        except Exception as e:
            print("[SUPABASE SYNC ERROR]:", str(e))

    return jsonify({
        "status": "success",
        "message": "Flight booked successfully",
        "booking": booking_record
    }), 201


@app.route('/api/flights/bookings', methods=['GET'])
def get_flight_bookings():
    raw_email = request.args.get('email') or request.args.get('user_email') or getattr(request, 'user_email', None)
    if not raw_email or not raw_email.strip():
        return jsonify({"status": "success", "bookings": []})
    user_email = raw_email.strip().lower()
    import json
    
    # Retrieve from local SQLite (always available)
    raw_sqlite = db.get_flight_bookings(user_email)
    bookings_map = {}

    for b in raw_sqlite:
        b_dict = dict(b)
        try:
            b_dict['flight_details'] = json.loads(b_dict['flight_details']) if isinstance(b_dict['flight_details'], str) else b_dict['flight_details']
            b_dict['passenger_details'] = json.loads(b_dict['passenger_details']) if isinstance(b_dict['passenger_details'], str) else b_dict['passenger_details']
        except Exception:
            pass
        key = b_dict.get('pnr') or b_dict.get('booking_id') or str(b_dict.get('id'))
        if key:
            bookings_map[key] = b_dict

    # Retrieve from Supabase if configured
    if supabase is not None:
        try:
            res = supabase.table('flight_bookings').select('*').ilike('user_email', user_email).order('id', desc=True).execute()
            for b in (res.data or []):
                try:
                    if isinstance(b.get('flight_details'), str):
                        b['flight_details'] = json.loads(b['flight_details'])
                    if isinstance(b.get('passenger_details'), str):
                        b['passenger_details'] = json.loads(b['passenger_details'])
                except Exception:
                    pass
                key = b.get('pnr') or b.get('booking_id') or str(b.get('id'))
                if key:
                    bookings_map[key] = b
        except Exception as e:
            print("[SUPABASE QUERY NOTICE]:", str(e))

    combined = list(bookings_map.values())
    combined.sort(key=lambda x: str(x.get('created_at', '') or x.get('id', '')), reverse=True)
    return jsonify({"status": "success", "bookings": combined})


@app.route('/api/flights/bookings/<id_or_pnr>', methods=['GET'])
def get_flight_booking_detail(id_or_pnr):
    import json
    if USE_SQLITE:
        booking = db.get_flight_booking_by_id_or_pnr(id_or_pnr)
        if not booking:
            return jsonify({"status": "error", "message": "Booking not found"}), 404
        try:
            booking['flight_details'] = json.loads(booking['flight_details']) if isinstance(booking['flight_details'], str) else booking['flight_details']
            booking['passenger_details'] = json.loads(booking['passenger_details']) if isinstance(booking['passenger_details'], str) else booking['passenger_details']
        except Exception:
            pass
        return jsonify({"status": "success", "booking": booking})
    else:
        try:
            res = supabase.table('flight_bookings').select('*').or_(f"booking_id.eq.{id_or_pnr},pnr.eq.{id_or_pnr},ticket_number.eq.{id_or_pnr}").limit(1).execute()
            if not res.data:
                booking = db.get_flight_booking_by_id_or_pnr(id_or_pnr)
            else:
                booking = res.data[0]
            if not booking:
                return jsonify({"status": "error", "message": "Booking not found"}), 404
            try:
                if isinstance(booking.get('flight_details'), str):
                    booking['flight_details'] = json.loads(booking['flight_details'])
                if isinstance(booking.get('passenger_details'), str):
                    booking['passenger_details'] = json.loads(booking['passenger_details'])
            except Exception:
                pass
            return jsonify({"status": "success", "booking": booking})
        except Exception as e:
            print("[FALLBACK] Supabase flight_booking detail query error:", str(e))
            booking = db.get_flight_booking_by_id_or_pnr(id_or_pnr)
            if not booking:
                return jsonify({"status": "error", "message": "Booking not found"}), 404
            try:
                booking['flight_details'] = json.loads(booking['flight_details']) if isinstance(booking['flight_details'], str) else booking['flight_details']
                booking['passenger_details'] = json.loads(booking['passenger_details']) if isinstance(booking['passenger_details'], str) else booking['passenger_details']
            except Exception:
                pass
            return jsonify({"status": "success", "booking": booking})

@app.route('/api/baggage/track', methods=['GET'])
def track_baggage():
    tag = request.args.get('tag', '').upper().strip()
    if not tag:
        return jsonify({"status": "error", "message": "Bag Tag or PNR Required"}), 400
    
    import time
    import hashlib
    import json
    
    # Try to find a flight booking if PNR was entered
    booking = None
    if supabase is not None:
        try:
            res = supabase.table('flight_bookings').select('*').or_(f"booking_id.eq.{tag},pnr.eq.{tag},ticket_number.eq.{tag}").limit(1).execute()
            if res.data:
                booking = res.data[0]
        except Exception:
            pass
    if not booking:
        booking = db.get_flight_booking_by_id_or_pnr(tag)
        
    flight = {}
    if booking:
        f = booking.get('flight_details', {})
        if isinstance(f, str):
            try:
                flight = json.loads(f)
            except Exception:
                flight = {}
        else:
            flight = f

    # Generate deterministic "live" progression based on tag string and current time
    h = int(hashlib.md5(tag.encode()).hexdigest(), 16)
    
    states = [
        {"status": "Checked In", "desc": f"Terminal Check-in Desk - {flight.get('origin', 'Origin')}", "color": "accepted"},
        {"status": "Security Cleared", "desc": "Automated Baggage Handling System", "color": "accepted"},
        {"status": "Loaded on Aircraft", "desc": f"Flight {flight.get('flight_number', 'Cargo')} Hold", "color": "pending"},
        {"status": "In Transit", "desc": "En route to destination", "color": "pending"},
        {"status": "Arrived at Destination", "desc": f"Offloaded at {flight.get('destination', 'Destination')}", "color": "delivered"},
        {"status": "On Carousel", "desc": "Baggage Claim Belt", "color": "delivered"}
    ]
    
    current_hour = int(time.time() / 3600)
    current_idx = (h + current_hour) % len(states)
    
    timeline = []
    for i in range(current_idx + 1):
        timeline.append({
            "status": states[i]["status"],
            "desc": states[i]["desc"],
            "is_current": (i == current_idx)
        })
        
    current_state = states[current_idx]
    
    return jsonify({
        "status": "success",
        "tag_id": tag,
        "flight": flight.get('flight_number', ''),
        "current_status": current_state["status"],
        "color": current_state["color"],
        "timeline": timeline
    })


@app.errorhandler(429)
def ratelimit_handler(e):
    return jsonify({"status": "error", "message": "Too many requests. Please wait a few seconds before trying again."}), 429

if __name__ == '__main__':
    port = int(os.environ.get("PORT", 5000))
    app.run(host='0.0.0.0', port=port, debug=True)

