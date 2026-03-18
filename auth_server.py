import os
import sqlite3
import random

from flask import Flask, request, jsonify
from flask_cors import CORS
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail
from groq import Groq

app = Flask(__name__)
CORS(app)

# ---------------- CONFIG ----------------

SENDGRID_API_KEY = os.getenv("SENDGRID_API_KEY")
FROM_EMAIL = os.getenv("FROM_EMAIL", "santhoshbabusbk25@gmail.com")

GROQ_API_KEY = os.getenv("GROQ_API_KEY")

client = Groq(api_key=GROQ_API_KEY)

DB = "users.db"

# ---------------- DATABASE ----------------

def init_db():
    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("""
    CREATE TABLE IF NOT EXISTS users(
        email TEXT PRIMARY KEY,
        first_name TEXT,
        last_name TEXT,
        phone TEXT,
        password TEXT,
        verified INTEGER,
        otp TEXT
    )
    """)

    conn.commit()
    conn.close()

init_db()

# ---------------- EMAIL ----------------

def send_otp(email, otp):
    if not SENDGRID_API_KEY:
        print("SendGrid key missing")
        return

    message = Mail(
        from_email=FROM_EMAIL,
        to_emails=email,
        subject="AeroAssist OTP Verification",
        html_content=f"<h2>Your OTP is: {otp}</h2>"
    )

    try:
        sg = SendGridAPIClient(SENDGRID_API_KEY)
        response = sg.send(message)
        print("Email sent:", response.status_code)
    except Exception as e:
        print("SendGrid Error:", e)

# ---------------- HOME ----------------

@app.route("/")
def home():
    return "AeroAssist Backend Running"

@app.route("/health")
def health():
    return {"status": "ok"}

# ---------------- SIGNUP ----------------

@app.route('/signup', methods=['POST'])
def signup():
    data = request.json

    first = data.get("first_name")
    last = data.get("last_name")
    email = data.get("email")
    phone = data.get("phone")
    password = data.get("password")

    otp = str(random.randint(100000, 999999))

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT email FROM users WHERE email=?", (email,))
    if c.fetchone():
        conn.close()
        return jsonify({"status":"error","message":"Account already exists"})

    c.execute("INSERT INTO users VALUES(?,?,?,?,?,?,?)",
              (email, first, last, phone, password, 0, otp))

    conn.commit()
    conn.close()

    send_otp(email, otp)

    return jsonify({"status":"success","message":"OTP sent"})

# ---------------- GOOGLE SIGNUP ----------------

@app.route('/signup_google', methods=['POST'])
def signup_google():
    data = request.json
    email = data.get("email")

    otp = str(random.randint(100000,999999))

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT email FROM users WHERE email=?", (email,))
    if c.fetchone():
        conn.close()
        return jsonify({"status":"error","message":"Account exists"})

    c.execute("INSERT INTO users VALUES(?,?,?,?,?,?,?)",
              (email, "Google", "User", "", "google_login", 0, otp))

    conn.commit()
    conn.close()

    send_otp(email, otp)

    return jsonify({"status":"success","message":"OTP sent"})

# ---------------- VERIFY OTP ----------------

@app.route('/verify', methods=['POST'])
def verify():
    data = request.json
    email = data.get("email")
    otp = data.get("otp")

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT otp FROM users WHERE email=?", (email,))
    row = c.fetchone()

    if row and row[0] == otp:
        c.execute("UPDATE users SET verified=1 WHERE email=?", (email,))
        conn.commit()
        conn.close()
        return jsonify({"status":"success","message":"verified"})

    conn.close()
    return jsonify({"status":"error","message":"Invalid OTP"})

# ---------------- LOGIN ----------------

@app.route('/login', methods=['POST'])
def login():
    data = request.json

    email = data.get("email")
    password = data.get("password")

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT password, verified FROM users WHERE email=?", (email,))
    user = c.fetchone()
    conn.close()

    if user is None:
        return jsonify({"status":"error","message":"Account not found"})

    stored_password, verified = user

    if verified == 0:
        return jsonify({"status":"error","message":"Email not verified"})

    if password != stored_password:
        return jsonify({"status":"error","message":"Invalid password"})

    return jsonify({"status":"success","message":"Login successful"})

# ---------------- GOOGLE LOGIN ----------------

@app.route('/google_login', methods=['POST'])
def google_login():
    data = request.json
    email = data.get("email")

    conn = sqlite3.connect(DB)
    c = conn.cursor()

    c.execute("SELECT verified FROM users WHERE email=?", (email,))
    user = c.fetchone()
    conn.close()

    if user is None:
        return jsonify({"status":"error","message":"Signup first"})

    if user[0] == 0:
        return jsonify({"status":"error","message":"Email not verified"})

    return jsonify({"status":"success","message":"Google login successful"})

# ---------------- AI CHAT ----------------

conversation_history = [
    {"role": "system", "content": "You are AeroAssist AI, an airport assistant. Give short clear answers."}
]

@app.route('/chat', methods=['POST'])
def chat():
    try:
        data = request.json
        message = data.get("message","")

        conversation_history.append({
            "role":"user",
            "content":message
        })

        response = client.chat.completions.create(
            model="llama-3.3-70b-versatile",
            messages=conversation_history,
            max_tokens=120,
            temperature=0.3
        )

        reply = response.choices[0].message.content

        conversation_history.append({
            "role":"assistant",
            "content":reply
        })

        return jsonify({"reply":reply})

    except Exception as e:
        print("AI ERROR:", e)
        return jsonify({"reply":"AI server error"})

# ---------------- RUN ----------------

if __name__ == "__main__":
    port = int(os.getenv("PORT", 5000))
    app.run(host="0.0.0.0", port=port)