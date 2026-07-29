package com.aeroassist.ai;

public class Constants {
    // Backend API
    public static final String BACKEND_BASE_URL = "https://aeroassistai.onrender.com";
    public static final String API_V1_BASE = BACKEND_BASE_URL + "/api";
    
    // API Keys (Moved to environment variables/secrets to prevent Gitleaks failures)
    public static final String OLA_MAPS_API_KEY = "ENTER_OLA_MAPS_API_KEY_HERE";
    public static final String AVIATION_STACK_API_KEY = "ENTER_AVIATION_STACK_API_KEY_HERE";
    
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
    
    // Flight Booking Endpoints
    public static final String FLIGHT_SEARCH_ENDPOINT = API_V1_BASE + "/flights/search";
    public static final String FLIGHT_BOOK_ENDPOINT = API_V1_BASE + "/flights/book";
    public static final String FLIGHT_BOOKINGS_ENDPOINT = API_V1_BASE + "/flights/bookings";
}
