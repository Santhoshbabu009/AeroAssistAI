package com.aeroassist.ai;

public class Constants {
    // Backend API
    public static final String BACKEND_BASE_URL = "https://aeroassistai.onrender.com";
    public static final String API_V1_BASE = BACKEND_BASE_URL + "/api";
    
    // API Keys
    public static final String OLA_MAPS_API_KEY = "ENTER_OLA_MAPS_API_KEY_HERE";
    public static final String AVIATION_STACK_API_KEY = "d4ec6dda8d5a4e2b81c989764b8ca9a1";
    
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
    public static final String GET_PROFILE_ENDPOINT = API_V1_BASE + "/get-profile";
    public static final String CHAT_HISTORY_ENDPOINT = API_V1_BASE + "/chat-history";
    
    public static final String FLIGHT_SEARCH_ENDPOINT = API_V1_BASE + "/flights/search";
    public static final String FLIGHT_STATUS_ENDPOINT = API_V1_BASE + "/flights/status";
    public static final String FLIGHT_BOOK_ENDPOINT = API_V1_BASE + "/flights/book";
    public static final String FLIGHT_BOOKINGS_ENDPOINT = API_V1_BASE + "/flights/bookings";
    public static final String FLIGHT_SEATS_ENDPOINT = API_V1_BASE + "/flights/seats";
    public static final String FLIGHT_SEAT_BOOK_ENDPOINT = API_V1_BASE + "/flights/seats/book";
    public static final String FLIGHT_SEAT_RELEASE_ENDPOINT = API_V1_BASE + "/flights/seats/release";


    // Parking Endpoints
    public static final String PARKING_BOOK_ENDPOINT      = API_V1_BASE + "/parking-bookings";         // POST - create booking
    public static final String PARKING_BOOKINGS_ENDPOINT  = API_V1_BASE + "/parking-bookings/history";  // GET  - fetch history

    // Food Orders Endpoint
    public static final String FOOD_ORDERS_ENDPOINT = API_V1_BASE + "/orders";

    // Lost & Found Endpoint
    public static final String LOST_ITEMS_ENDPOINT = API_V1_BASE + "/lost-items";
}
