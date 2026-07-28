package com.aeroassist.ai;

public class Constants {
    // Backend API URL Configuration:
    // 1. For cloud deployment: "https://aeroassistai.onrender.com"
    // 2. For local Android Emulator: "http://10.0.2.2:5000"
    // 3. For physical Android phone on Wi-Fi: "http://10.181.143.74:5000"
    public static final String BACKEND_BASE_URL = "https://aeroassistai.onrender.com";
    public static final String API_V1_BASE = BACKEND_BASE_URL + "/api";
    
    // API Keys
    public static final String OLA_MAPS_API_KEY = "mjvbKLsjrbnfxQgWu6tF3080AbtWZdGIHds9vANS";
    public static final String AVIATION_STACK_API_KEY = "322876eed5ec416a01fffd3e4429c29e";
    
    // API Endpoints
    public static final String CHAT_ENDPOINT = BACKEND_BASE_URL + "/chat";
    public static final String GOOGLE_LOGIN_ENDPOINT = API_V1_BASE + "/google-login";
    public static final String LOGIN_ENDPOINT = API_V1_BASE + "/login";
    public static final String REGISTER_ENDPOINT = API_V1_BASE + "/register";
    public static final String VERIFY_OTP_ENDPOINT = API_V1_BASE + "/verify";
    public static final String UPDATE_PROFILE_ENDPOINT = API_V1_BASE + "/update-profile";
    public static final String PASSWORD_RESET_REQUEST_ENDPOINT = API_V1_BASE + "/password-reset-request";
    public static final String PASSWORD_RESET_CONFIRM_ENDPOINT = API_V1_BASE + "/password-reset-confirm";

    public static final String REWARD_CERTIFICATE_ENDPOINT = API_V1_BASE + "/send-certificate";
    public static final String SAVE_CHAT_ENDPOINT = API_V1_BASE + "/save-chat";
}
