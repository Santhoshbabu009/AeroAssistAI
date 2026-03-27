from flask import Flask, request, jsonify
from flask_cors import CORS
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
import random
import sqlite3
import os
import requests

app = Flask(__name__)
CORS(app)

# ================== ENV VARIABLES ==================
EMAIL_USER = os.environ.get("EMAIL_USER")
EMAIL_PASS = os.environ.get("EMAIL_PASS")
GROQ_API_KEY = os.environ.get("GROQ_API_KEY")

# ================== DATABASE ==================
def init_db():
    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS users (
            email TEXT PRIMARY KEY,
            name TEXT,
            password TEXT,
            mobile TEXT
        )
    ''')
    conn.commit()
    conn.close()

init_db()

# ================== HOME ==================
@app.route('/', methods=['GET'])
def home():
    return jsonify({
        "status": "online",
        "message": "AeroAssist AI Backend Running"
    })

# ================== OTP STORE ==================
otp_store = {}

# ================== EMAIL ==================
def send_smtp_email(to_email, otp, name="User", custom_message=None):
    if not EMAIL_USER or not EMAIL_PASS:
        print("Missing email credentials")
        return False

    try:
        msg = MIMEMultipart()
        msg['From'] = EMAIL_USER
        msg['To'] = to_email
        msg['Subject'] = "AeroAssist Verification Code"

        html = f"""
        <h2>Hello {name}</h2>
        <p>Your OTP is:</p>
        <h1>{otp}</h1>
        <p>Valid for 5 minutes</p>
        """
        msg.attach(MIMEText(html, 'html'))

        server = smtplib.SMTP('smtp.gmail.com', 587)
        server.starttls()
        server.login(EMAIL_USER, EMAIL_PASS)
        server.sendmail(EMAIL_USER, to_email, msg.as_string())
        server.quit()
        return True

    except Exception as e:
        print("Email Error:", e)
        return False

# ================== GOOGLE LOGIN ==================
@app.route('/api/google-login', methods=['POST'])
def google_login():
    data = request.json
    email = data.get('email')
    name = data.get('name', 'Google User')

    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute('SELECT name, mobile FROM users WHERE email=?', (email,))
    user = cursor.fetchone()
    conn.close()

    if user:
        return jsonify({
            "status": "success",
            "existing": True,
            "name": user[0],
            "mobile": user[1] or ""
        })

    otp = str(random.randint(1000, 9999))
    otp_store[email] = {
        "otp": otp,
        "name": name,
        "password": f"google_{email}",
        "mobile": ""
    }

    send_smtp_email(email, otp, name)
    return jsonify({"status": "success", "existing": False})

# ================== REGISTER ==================
@app.route('/api/register', methods=['POST'])
def register():
    data = request.json
    email = data.get('email')

    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute('SELECT email FROM users WHERE email=?', (email,))
    if cursor.fetchone():
        conn.close()
        return jsonify({"status": "error", "message": "User exists"}), 400
    conn.close()

    otp = str(random.randint(1000, 9999))
    otp_store[email] = data
    otp_store[email]['otp'] = otp

    send_smtp_email(email, otp, data.get('name'))

    return jsonify({"status": "success", "message": "OTP sent"})

# ================== VERIFY ==================
@app.route('/api/verify', methods=['POST'])
def verify():
    data = request.json
    email = data.get('email')
    otp = data.get('otp')

    if email in otp_store and otp_store[email]['otp'] == str(otp):
        user = otp_store[email]

        conn = sqlite3.connect('aeroassist.db')
        cursor = conn.cursor()
        cursor.execute(
            'INSERT INTO users VALUES (?, ?, ?, ?)',
            (email, user['name'], user['password'], user['mobile'])
        )
        conn.commit()
        conn.close()

        del otp_store[email]

        return jsonify({"status": "success", "name": user['name']})

    return jsonify({"status": "error", "message": "Invalid OTP"}), 400

# ================== LOGIN ==================
@app.route('/api/login', methods=['POST'])
def login():
    data = request.json
    email = (data.get('email') or '').lower()
    password = data.get('password')

    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute('SELECT name, password, mobile FROM users WHERE email=?', (email,))
    user = cursor.fetchone()
    conn.close()

    if user and user[1] == password:
        return jsonify({
            "status": "success",
            "name": user[0],
            "mobile": user[2]
        })

    return jsonify({"status": "error", "message": "Invalid login"}), 401

# ================== CHAT ==================
@app.route('/chat', methods=['POST'])
def chat():
    data = request.json
    message = data.get('message')

    headers = {
        "Authorization": f"Bearer {GROQ_API_KEY}",
        "Content-Type": "application/json"
    }

    payload = {
        "model": "llama-3.1-8b-instant",
        "messages": [
            {"role": "system", "content": "You are an airport assistant AI."},
            {"role": "user", "content": message}
        ]
    }

    try:
        res = requests.post(
            "https://api.groq.com/openai/v1/chat/completions",
            headers=headers,
            json=payload
        )
        if res.status_code != 200:
            error_msg = "Unknown error"
            try:
                error_msg = res.json().get('error', {}).get('message', res.text)
            except:
                error_msg = res.text
            return jsonify({"reply": f"Chat service error: {error_msg}"}), 500
            
        reply = res.json()['choices'][0]['message']['content']
        return jsonify({"reply": reply})

    except Exception as e:
        print("Chat Error:", e)
        return jsonify({"reply": "Chat service unavailable"}), 500

# ================== PASSWORD RESET ==================
@app.route('/api/password-reset-request', methods=['POST'])
def password_reset_request():
    data = request.json
    email = data.get('email')

    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute('SELECT password, name FROM users WHERE email=?', (email,))
    user = cursor.fetchone()
    conn.close()

    if not user:
        return jsonify({"status": "error", "message": "User not found"}), 404
        
    if user[0].startswith('google_'):
        return jsonify({"status": "error", "message": "Cannot reset Google Account password here"}), 400

    otp = str(random.randint(1000, 9999))
    otp_store[email] = {"otp": otp, "type": "reset"}

    send_smtp_email(email, otp, user[1])
    return jsonify({"status": "success", "message": "OTP sent"})

@app.route('/api/password-reset-confirm', methods=['POST'])
def password_reset_confirm():
    data = request.json
    email = data.get('email')
    otp = data.get('otp')
    new_password = data.get('password')

    if email in otp_store and otp_store[email].get('otp') == str(otp):
        conn = sqlite3.connect('aeroassist.db')
        cursor = conn.cursor()
        cursor.execute('UPDATE users SET password=? WHERE email=?', (new_password, email))
        conn.commit()
        conn.close()

        del otp_store[email]
        return jsonify({"status": "success", "message": "Password updated"})

    return jsonify({"status": "error", "message": "Invalid OTP"}), 400

# ================== PROFILE UPDATE ==================
@app.route('/api/update-profile', methods=['POST'])
def update_profile():
    data = request.json
    email = data.get('email')
    name = data.get('name')
    mobile = data.get('mobile')

    conn = sqlite3.connect('aeroassist.db')
    cursor = conn.cursor()
    cursor.execute(
        'UPDATE users SET name=?, mobile=? WHERE email=?',
        (name, mobile, email)
    )
    conn.commit()
    conn.close()

    return jsonify({"status": "success"})

# ================== RUN ==================
if __name__ == '__main__':
    port = int(os.environ.get("PORT", 5000))
    app.run(host='0.0.0.0', port=port)