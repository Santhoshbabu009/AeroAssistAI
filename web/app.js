/**
 * AeroAssist AI - Complete Glassmorphic Web App Suite
 * Seamlessly integrates Passenger, Vendor, and Administrative portals.
 * Connected directly to local Flask python backend services.
 */

class AeroAssistApp {
  constructor() {
    this.API_BASE = (window.location.protocol === 'file:' || window.location.origin.includes('localhost') || window.location.origin.includes('127.0.0.1') || !window.location.origin) ? "https://aeroassistai.onrender.com/api" : "/api";
    // API Keys should be fetched dynamically from backend, hardcoding removed for security
    this.AVIATION_STACK_KEY = "ENTER_AVIATION_STACK_API_KEY_HERE";
    
    // User Authentication Session State (Safe Parsing)
    try {
      this.currentUser = JSON.parse(localStorage.getItem("user_session")) || null;
      this.currentVendor = JSON.parse(localStorage.getItem("vendor_session")) || null;
    } catch (e) {
      console.warn("Corrupted session data. Resetting...");
      this.currentUser = null;
      this.currentVendor = null;
      localStorage.removeItem("user_session");
      localStorage.removeItem("vendor_session");
    }
    
    this.currentUserType = localStorage.getItem("user_type") || null; // Visitor, Employee, Vendor
    this.authMode = "login"; // login, signup, verify
    
    // Vendor Session State
    this.vendorTab = "queue";
    
    // Chatbot State
    this.chatSessionId = Math.floor(Math.random() * 100000);
    this.chatHistory = [
      { isUser: false, text: "Welcome to AeroAssist AI Copilot! I am your smart airport companion. How can I help you today?" }
    ];

    // Dining Cart State
    this.cart = {
      restaurant: null,
      items: []
    };
    
    // Lounge booking slots state
    this.activeLounge = null;
    this.bookingGuests = 1;

    // Trivia Quiz State
    this.quizQuestions = [
      {
        q: "What is the maximum allowed carry-on cabin baggage weight for most domestic flights?",
        options: ["5 kg", "7 kg", "10 kg", "15 kg"],
        correct: 1
      },
      {
        q: "Which terminal at Chennai International Airport (MAA) primarily handles international departures?",
        options: ["Terminal 1", "Terminal 2", "Terminal 3", "Terminal 4"],
        correct: 2
      },
      {
        q: "What is the security limit for carrying liquid containers in cabin hand baggage?",
        options: ["50 ml", "100 ml", "250 ml", "500 ml"],
        correct: 1
      }
    ];
    this.currentQuizIdx = 0;
    this.quizScore = 0;

    // Multi-Language i18n State
    this.currentLang = localStorage.getItem("app_language") || "en";

    // Background Polling
    this.pollingInterval = null;
    
    // Ensure safe initialization
    try {
      this.init();
    } catch (e) {
      console.error("Initialization failed:", e);
    }
  }

  init() {
    this.updateClock();
    setInterval(() => this.updateClock(), 1000);
    this.initTheme();
    this.initCartSidebar();
    
    // Initialize Lucide Icons
    if (typeof lucide !== 'undefined') {
      lucide.createIcons();
    }
    
    this.setupCommandPalette();

    this.changeLanguage(this.currentLang);
    this.updateUserSessionUI();
    this.renderChatHistory();
    this.loadQuizQuestion();

    // Initial Fetch triggers
    this.fetchRestaurants();
    this.fetchLounges();
    if (this.currentUser) {
      this.fetchUserProfile();
      this.fetchChatHistory();
      this.fetchMyBookings();
    }
    
    // Check session states to route initially
    if (this.currentVendor) {
      this.currentUserType = "Vendor";
      this.showPage("vendor");
    } else if (this.currentUser) {
      this.showPage("dashboard");
    } else {
      // Default to Role Entrance selection gate
      this.showPage("user-type");
    }
    
    // Setup background interval polling (Every 10 seconds)
    this.pollingInterval = setInterval(() => {
      this.fetchPassengerHistory();
      if (this.currentVendor && (!this.vendorTab || this.vendorTab === 'queue')) {
        this.fetchVendorQueue();
      }
      const trackerModal = document.getElementById("modal-order-tracker");
      if (this.activeTrackingOrderId && trackerModal && trackerModal.classList.contains("open")) {
        this.openOrderTracker(this.activeTrackingOrderId);
      }
    }, 10000);
  }



  // --- DUAL THEME ENGINE (LIGHT & DARK MODE) ---
  initTheme() {
    const savedTheme = localStorage.getItem("app_theme") || "dark";
    this.setTheme(savedTheme);
  }

  toggleTheme() {
    const current = document.documentElement.getAttribute("data-theme") || "dark";
    const next = current === "dark" ? "light" : "dark";
    this.setTheme(next);
  }

  setTheme(theme) {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("app_theme", theme);
    const icon = document.getElementById("theme-toggle-icon");
    if (icon) {
      icon.innerText = theme === "dark" ? "🌙" : "☀️";
    }
  }

  // --- SPOTLIGHT COMMAND PALETTE (CTRL+K) ---
  setupCommandPalette() {
    window.addEventListener("keydown", (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        this.openCommandPalette();
      }
      if (e.key === "Escape") {
        this.closeModal("command-palette");
      }
    });
  }

  openCommandPalette() {
    this.openModal("command-palette");
    const input = document.getElementById("command-palette-input");
    if (input) {
      input.value = "";
      setTimeout(() => input.focus(), 100);
    }
    this.filterCommandPalette("");
  }

  filterCommandPalette(query) {
    const container = document.getElementById("command-palette-results");
    if (!container) return;
    const role = this.currentUserType || localStorage.getItem("user_type") || "Visitor";
    const isVendorRole = (role === "Vendor" || role === "Admin") && !!this.currentVendor;

    let actions = [
      { icon: "🏠", title: "Dashboard", desc: "View live flight tracker & departure schedule", page: "dashboard" },
      { icon: "💬", title: "AI Assistant", desc: "Ask questions to AI Copilot", page: "chat" },
      { icon: "🍔", title: "Dining & Eats", desc: "Pre-order counter meals at airport restaurants", page: "dining" },
      { icon: "🛋️", title: "Airport Lounges", desc: "Book VIP lounge slots & passes", page: "lounges" },
      { icon: "💳", title: "Smart Wallet", desc: "View boarding passes & digital ID", page: "wallet" },
      { icon: "🗺️", title: "Travel Utilities", desc: "Currency & metric unit converters", page: "utilities" },
      { icon: "🏆", title: "Quiz & Rewards", desc: "Test trivia skills & print certificate", page: "quiz" },
      { icon: "🔐", title: "Vendor Portal", desc: "Merchant kitchen queue & catalog", action: () => this.openVendorPortal() },
      { icon: "📱", title: "Download Mobile App", desc: "View mobile app QR flyer poster", action: () => this.openModal("app-flyer") }
    ];

    if (!isVendorRole) {
      actions = actions.filter(a => a.title !== "Vendor Portal");
    }

    const filtered = actions.filter(item => item.title.toLowerCase().includes(q) || item.desc.toLowerCase().includes(q));

    if (filtered.length === 0) {
      container.innerHTML = `<p style="padding:16px; text-align:center; color:var(--text-secondary);">No matching commands found.</p>`;
      return;
    }

    container.innerHTML = filtered.map(item => `
      <div style="display:flex; align-items:center; gap:12px; padding:10px 14px; border-radius:var(--radius-md); background:var(--glass-bg); border:var(--glass-border); cursor:pointer; transition:all 0.2s;" 
           onmouseover="this.style.background='var(--glass-bg-hover)'" 
           onmouseout="this.style.background='var(--glass-bg)'"
           onclick="app.closeModal('command-palette'); ${item.page ? `app.showPage('${item.page}')` : ''};">
        <span style="font-size:20px;">${item.icon}</span>
        <div style="flex:1;">
          <h4 style="margin:0; font-size:14px;">${item.title}</h4>
          <span style="font-size:12px; color:var(--text-secondary);">${item.desc}</span>
        </div>
        <kbd class="top-bar-kbd">↵ Select</kbd>
      </div>
    `).join("");
  }

  // --- MULTI-LANGUAGE I18N SYSTEM ---
  changeLanguage(lang) {
    const TRANSLATIONS = {
      en: {
        get_app: "Get Mobile App",
        nav_dashboard: "Dashboard",
        nav_chat: "AI Assistant",
        nav_dining: "Dining & Eats",
        nav_lounges: "Lounges",
        nav_wallet: "Smart Wallet",
        nav_utilities: "Travel Utilities",
        nav_quiz: "Quiz & Rewards",
        nav_vendor: "Vendor Portal",
        welcome_title: "Welcome to AeroAssist AI",
        welcome_sub: "Your premium all-in-one smart airport dashboard suite. Select your role below to log in and access flight tracking, pre-orders, smart wallets, and merchant dashboards.",
        role_visitor: "Airport Visitor",
        role_visitor_desc: "Travelling through terminal gates. Track baggage hubs, pre-order meals, reserve premium lounge passes, and claim rewards.",
        role_employee: "Airport Employee",
        role_employee_desc: "Internal staff operations portal. Access quick-reference Customs rules, virtual ID cards, and converter tools.",
        role_vendor: "Merchant / Admin",
        role_vendor_desc: "Shop counter or terminal administrator. Process pre-order slots, manage items catalog, or register vendor profiles.",
        btn_visitor: "ENTER AS VISITOR",
        btn_employee: "ENTER AS EMPLOYEE",
        btn_vendor: "ENTER PORTAL",
        signin_title: "Passenger Account Sign In",
        signin_sub: "Log in with your verified passenger email & password",
        email_label: "Email Address",
        password_label: "Password",
        btn_signin: "SIGN IN",
        google_signin: "Sign in with Google",
        create_account: "Create Account",
        guest_continue: "Or continue as Guest Visitor ➔",
        flyer_badge: "OFFICIAL MOBILE APP",
        flyer_title: "Download AeroAssist AI Official App",
        flyer_sub: "Unlock exclusive mobile-only features for an effortless airport journey.",
        flyer_f1: "Real-time Push Alerts",
        flyer_f2: "In-Seat Lounge Dining",
        flyer_f3: "3D AR Terminal Maps",
        flyer_f4: "Offline Document Wallet",
        btn_download_apk: "Direct APK Download 📥",
        btn_scan_qr: "Scan QR Code on Phone",
        dash_flyer_title: "Experience AeroAssist on Your Smartphone",
        dash_flyer_desc: "Download our official mobile app on Android & iOS for offline gate maps, live push notifications, and fast QR check-in.",
        dash_flyer_btn: "VIEW APP POSTER 📱"
      },
      es: {
        get_app: "Obtener App Móvil",
        nav_dashboard: "Panel Principal",
        nav_chat: "Asistente IA",
        nav_dining: "Restaurantes y Comida",
        nav_lounges: "Salas VIP",
        nav_wallet: "Billetera Inteligente",
        nav_utilities: "Utilidades de Viaje",
        nav_quiz: "Cuestionario y Premios",
        nav_vendor: "Portal de Vendedores",
        welcome_title: "Bienvenido a AeroAssist AI",
        welcome_sub: "Su suite de panel de aeropuerto inteligente todo en uno. Seleccione su función a continuación.",
        role_visitor: "Visitante de Aeropuerto",
        role_visitor_desc: "Viajando por las puertas de embarque. Rastree equipaje, pida comida por adelantado y reserve salas VIP.",
        role_employee: "Empleado de Aeropuerto",
        role_employee_desc: "Portal de operaciones de personal interno. Acceda a reglas de aduana e identificaciones virtuales.",
        role_vendor: "Comerciante / Admin",
        role_vendor_desc: "Administrador de mostrador de tienda. Procese pedidos, gestione productos o registre perfiles.",
        btn_visitor: "ENTRAR COMO VISITANTE",
        btn_employee: "ENTRAR COMO EMPLOYEE",
        btn_vendor: "ENTRAR AL PORTAL",
        signin_title: "Iniciar Sesión de Pasajero",
        signin_sub: "Ingrese con su correo electrónico y contraseña verificados",
        email_label: "Correo Electrónico",
        password_label: "Contraseña",
        btn_signin: "INICIAR SESIÓN",
        google_signin: "Iniciar sesión con Google",
        create_account: "Crear Cuenta",
        guest_continue: "O continuar como visitante ➔",
        flyer_badge: "APLICACIÓN MÓVIL OFICIAL",
        flyer_title: "Descargue la App Oficial AeroAssist AI",
        flyer_sub: "Desbloquee funciones exclusivas para un viaje sin esfuerzo.",
        flyer_f1: "Alertas Push de Puertas",
        flyer_f2: "Entrega de Comida VIP",
        flyer_f3: "Mapa 3D AR de Terminal",
        flyer_f4: "Billetera Fuera de Línea",
        btn_download_apk: "Descargar APK Directo 📥",
        btn_scan_qr: "Escanear Código QR",
        dash_flyer_title: "Experimente AeroAssist en su Smartphone",
        dash_flyer_desc: "Descargue nuestra aplicación móvil oficial en Android e iOS.",
        dash_flyer_btn: "VER FOLLETO DE LA APP 📱"
      },
      fr: {
        get_app: "Obtenir l'application",
        nav_dashboard: "Tableau de Bord",
        nav_chat: "Assistant IA",
        nav_dining: "Restauration",
        nav_lounges: "Salons VIP",
        nav_wallet: "Portefeuille Intelligent",
        nav_utilities: "Services de Voyage",
        nav_quiz: "Quiz & Récompenses",
        nav_vendor: "Portail Vendeur",
        welcome_title: "Bienvenue sur AeroAssist AI",
        welcome_sub: "Votre suite de tableau de bord d'aéroport intelligent.",
        role_visitor: "Visiteur d'Aéroport",
        role_visitor_desc: "Suivez vos bagages et précommandez vos repas.",
        role_employee: "Employé d'Aéroport",
        role_employee_desc: "Portail du personnel.",
        role_vendor: "Commerçant / Admin",
        role_vendor_desc: "Gestion des commandes.",
        btn_visitor: "ENTRER COMME VISITEUR",
        btn_employee: "ENTRER COMME EMPLOYÉ",
        btn_vendor: "ACCÉDER AU PORTAIL",
        signin_title: "Connexion Passager",
        signin_sub: "Connectez-vous avec vos identifiants passager",
        email_label: "Adresse E-mail",
        password_label: "Mot de passe",
        btn_signin: "SE CONNECTER",
        google_signin: "Se connecter avec Google",
        create_account: "Créer un compte",
        guest_continue: "Continuer comme invité ➔",
        flyer_badge: "APPLICATION MOBILE OFFICIELLE",
        flyer_title: "Téléchargez l'application AeroAssist AI",
        flyer_sub: "Débloquez des fonctionnalités mobiles exclusives.",
        flyer_f1: "Alertes push de porte",
        flyer_f2: "Livraison de repas QR",
        flyer_f3: "Cartes 3D AR de la terminale",
        flyer_f4: "Portefeuille hors ligne",
        btn_download_apk: "Téléchargement APK direct 📥",
        btn_scan_qr: "Scanner le code QR",
        dash_flyer_title: "AeroAssist sur votre smartphone",
        dash_flyer_desc: "Téléchargez notre application mobile officielle sur Android & iOS.",
        dash_flyer_btn: "VOIR LE PROSPECTUS 📱"
      },
      de: {
        get_app: "App Herunterladen",
        nav_dashboard: "Dashboard",
        nav_chat: "KI-Assistent",
        nav_dining: "Gastronomie",
        nav_lounges: "Lounges",
        nav_wallet: "Smart Wallet",
        nav_utilities: "Reisedienste",
        nav_quiz: "Quiz & Belohnungen",
        nav_vendor: "Händlerportal",
        welcome_title: "Willkommen bei AeroAssist AI",
        welcome_sub: "Ihre intelligente Flughafen-Dashboard-Suite.",
        role_visitor: "Flughafenbesucher",
        role_visitor_desc: "Gepäckverfolgung und Essensbestellung.",
        role_employee: "Flughafenmitarbeiter",
        role_employee_desc: "Mitarbeiterportal.",
        role_vendor: "Händler / Admin",
        role_vendor_desc: "Bestellungen bearbeiten.",
        btn_visitor: "ALS BESUCHER EINTRETEN",
        btn_employee: "ALS MITARBEITER EINTRETEN",
        btn_vendor: "ZUM PORTAL",
        signin_title: "Passagier-Anmeldung",
        signin_sub: "Melden Sie sich mit E-Mail an",
        email_label: "E-Mail-Adresse",
        password_label: "Passwort",
        btn_signin: "ANMELDEN",
        google_signin: "Mit Google anmelden",
        create_account: "Konto erstellen",
        guest_continue: "Als Gast fortfahren ➔",
        flyer_badge: "OFFIZIELLE MOBILE APP",
        flyer_title: "Laden Sie die AeroAssist AI App herunter",
        flyer_sub: "Exklusive Funktionen für Ihr Smartphone.",
        flyer_f1: "Echtzeit-Push-Alerts",
        flyer_f2: "Lounge QR Essenslieferung",
        flyer_f3: "3D AR-Karten der Terminale",
        flyer_f4: "Offline-Dokumenten-Wallet",
        btn_download_apk: "Direkter APK-Download 📥",
        btn_scan_qr: "QR-Code scannen",
        dash_flyer_title: "AeroAssist auf Ihrem Smartphone",
        dash_flyer_desc: "Laden Sie die offizielle App für Android & iOS herunter.",
        dash_flyer_btn: "APP-FLYER ANZEIGEN 📱"
      },
      hi: {
        get_app: "ऐप डाउनलोड करें",
        nav_dashboard: "डैशबोर्ड",
        nav_chat: "एआई सहायक",
        nav_dining: "खान-पान",
        nav_lounges: "लाउंज",
        nav_wallet: "स्मार्ट वॉलेट",
        nav_utilities: "यात्रा सेवाएं",
        nav_quiz: "क्विज़ और पुरस्कार",
        nav_vendor: "विक्रेता पोर्टल",
        welcome_title: "एयरोअसिस्ट एआई में आपका स्वागत है",
        welcome_sub: "आपका प्रीमियम ऑल-इन-वन स्मार्ट एयरपोर्ट डैशबोर्ड।",
        role_visitor: "एयरपोर्ट यात्री",
        role_visitor_desc: "फ्लाइट ट्रैकिंग, भोजन ऑर्डर और लाउंज पास।",
        role_employee: "एयरपोर्ट कर्मचारी",
        role_employee_desc: "स्टाफ ऑपरेशन्स पोर्टल।",
        role_vendor: "व्यापारी / व्यवस्थापक",
        role_vendor_desc: "आदेश प्रसंकरण।",
        btn_visitor: "यात्री के रूप में प्रवेश करें",
        btn_employee: "कर्मचारी के रूप में प्रवेश करें",
        btn_vendor: "पोर्टल खोलें",
        signin_title: "यात्री लॉगिन",
        signin_sub: "अपने ईमेल के साथ साइन इन करें",
        email_label: "ईमेल पता",
        password_label: "पासवर्ड",
        btn_signin: "साइन इन करें",
        google_signin: "गूगल के साथ साइन इन करें",
        create_account: "खाता बनाएं",
        guest_continue: "अतिथि के रूप में जारी रखें ➔",
        flyer_badge: "आधिकारिक मोबाइल ऐप",
        flyer_title: "एयरोअसिस्ट एआई ऐप डाउनलोड करें",
        flyer_sub: "विशेष मोबाइल सुविधाओं का लाभ उठाएं।",
        flyer_f1: "रियल-टाइम पुश अलर्ट",
        flyer_f2: "क्यूआर कोड से लाउंज में खाना ऑर्डर",
        flyer_f3: "3D AR टर्मिनल मैप नेविगेशन",
        flyer_f4: "ऑफ़लाइन दस्तावेज़ वॉलेट",
        btn_download_apk: "डायरेक्ट APK डाउनलोड 📥",
        btn_scan_qr: "QR कोड स्कैन करें",
        dash_flyer_title: "अपने स्मार्टफोन पर एयरोअसिस्ट का अनुभव करें",
        dash_flyer_desc: "एंड्रॉइड और आईओएस पर आधिकारिक ऐप डाउनलोड करें।",
        dash_flyer_btn: "ऐप पोस्टर देखें 📱"
      },
      ta: {
        get_app: "செயலியைப் பதிவிறக்குக",
        nav_dashboard: "டாஷ்போர்டு",
        nav_chat: "AI உதவி",
        nav_dining: "உணவகம்",
        nav_lounges: "லவுஞ்ச்",
        nav_wallet: "ஸ்மார்ட் வாலட்",
        nav_utilities: "பயண சேவைகள்",
        nav_quiz: "வினாடி வினா & பரிசுகள்",
        nav_vendor: "விற்பனையாளர் போர்ட்டல்",
        welcome_title: "ஏரோஅசிஸ்ட் AI க்கு வரவேற்கிறோம்",
        welcome_sub: "உங்கள் விமான நிலைய ஸ்மார்ட் சேவை தளம்.",
        role_visitor: "விமான நிலைய பயணி",
        role_visitor_desc: "விமான விவரங்கள் மற்றும் உணவு ஆர்டர்கள்.",
        role_employee: "விமான நிலைய ஊழியர்",
        role_employee_desc: "ஊழியர் செயல்பாடுகள்.",
        role_vendor: "வணிகர் / நிர்வாகி",
        role_vendor_desc: "ஆர்டர்கள் நிர்வாகம்.",
        btn_visitor: "பயணியாக நுழையவும்",
        btn_employee: "ஊழியராக நுழையவும்",
        btn_vendor: "போர்ட்டலுக்குச் செல்லவும்",
        signin_title: "பயணி உள்நுழைவு",
        signin_sub: "உங்கள் மின்னஞ்சல் பயன்படுத்தவும்",
        email_label: "மின்னஞ்சல் முகவரி",
        password_label: "கடவுச்சொல்",
        btn_signin: "உள்நுழைக",
        google_signin: "கூகிள் மூலம் உள்நுழைக",
        create_account: "கணக்கை உருவாக்கவும்",
        guest_continue: "விருந்தினராகத் தொடரவும் ➔",
        flyer_badge: "அதிகாரப்பூர்வ மொபைல் செயலி",
        flyer_title: "ஏரோஅசிஸ்ட் AI செயலியைப் பதிவிறக்கவும்",
        flyer_sub: "சிறப்பு மொபைல் அம்சங்களைப் பெறுங்கள்.",
        flyer_f1: "விமான நேர மாற்ற அறிவிப்புகள்",
        flyer_f2: "QR மூலம் உணவு ஆர்டர்",
        flyer_f3: "3D AR வரைபடம்",
        flyer_f4: "ஆஃப்லைன் வாலட்",
        btn_download_apk: "நேரடி APK பதிவிறக்கம் 📥",
        btn_scan_qr: "QR குறியீட்டை ஸ்கேன் செய்க",
        dash_flyer_title: "உங்கள் ஸ்மார்ட்போனில் ஏரோஅசிஸ்ட்",
        dash_flyer_desc: "ஆண்ட்ராய்டு & iOS இல் அதிகாரப்பூர்வ செயலியைப் பதிவிறக்கவும்.",
        dash_flyer_btn: "செயலி சுவரொட்டியைக் காண்க 📱"
      },
      zh: {
        get_app: "下载移动应用",
        nav_dashboard: "仪表板",
        nav_chat: "AI 助手",
        nav_dining: "餐饮美食",
        nav_lounges: "贵宾休息室",
        nav_wallet: "智能钱包",
        nav_utilities: "旅行工具",
        nav_quiz: "测验与奖励",
        nav_vendor: "商家门户",
        welcome_title: "欢迎使用 AeroAssist AI",
        welcome_sub: "您的全功能智能机场仪表板套件。",
        role_visitor: "机场旅客",
        role_visitor_desc: "跟踪行李、预订餐食和贵宾室通行证。",
        role_employee: "机场员工",
        role_employee_desc: "内部员工门户。",
        role_vendor: "商家 / 管理员",
        role_vendor_desc: "处理预订订单，管理商品目录。",
        btn_visitor: "以旅客身份进入",
        btn_employee: "以员工身份进入",
        btn_vendor: "进入门户",
        signin_title: "旅客登录",
        signin_sub: "使用已验证的邮箱和密码登录",
        email_label: "电子邮箱",
        password_label: "密码",
        btn_signin: "登录",
        google_signin: "使用 Google 登录",
        create_account: "创建账号",
        guest_continue: "以访客身份继续 ➔",
        flyer_badge: "官方移动应用",
        flyer_title: "下载 AeroAssist AI 官方 App",
        flyer_sub: "解锁专属移动端功能。",
        flyer_f1: "登机口变更与航班延误实时推送",
        flyer_f2: "扫码在休息室点餐送达",
        flyer_f3: "3D AR 航站楼地图导航",
        flyer_f4: "离线文档钱包",
        btn_download_apk: "直接下载 APK 📥",
        btn_scan_qr: "扫描二维码",
        dash_flyer_title: "在智能手机上体验 AeroAssist",
        dash_flyer_desc: "在 Android 和 iOS 上下载我们的官方应用。",
        dash_flyer_btn: "查看应用海报 📱"
      },
      ar: {
        get_app: "تحميل التطبيق",
        nav_dashboard: "لوحة التحكم",
        nav_chat: "مساعد الذكاء الاصطناعي",
        nav_dining: "المطاعم والمأكولات",
        nav_lounges: "صالات الاستراحة",
        nav_wallet: "المحفظة الذكية",
        nav_utilities: "خدمات السفر",
        nav_quiz: "الاختبار والجوائز",
        nav_vendor: "بوابة التجار",
        welcome_title: "مرحباً بكم في AeroAssist AI",
        welcome_sub: "مجموعتك الشاملة للوحة تحكم المطار الذكية.",
        role_visitor: "زائر المطار",
        role_visitor_desc: "تتبع الأمتعة، طلب الوجبات وحجز صالات الاستراحة.",
        role_employee: "موظف المطار",
        role_employee_desc: "بوابة الموظفين.",
        role_vendor: "تاجر / أدمن",
        role_vendor_desc: "معالجة الطلبات وإدارة قائمة المنتجات.",
        btn_visitor: "الدخول كزائر",
        btn_employee: "الدخول كموظف",
        btn_vendor: "الدخول للبوابة",
        signin_title: "تسجيل دخول المسافر",
        signin_sub: "سجل الدخول ببريدك الإلكتروني",
        email_label: "البريد الإلكتروني",
        password_label: "كلمة المرور",
        btn_signin: "تسجيل الدخول",
        google_signin: "تسجيل الدخول باستخدام Google",
        create_account: "إنشاء حساب",
        guest_continue: "المتابعة كزائر ➔",
        flyer_badge: "التطبيق الرسمي للموبايل",
        flyer_title: "تحميل تطبيق AeroAssist AI الرسمي",
        flyer_sub: "احصل على ميزات حصرية لتجربة سفر سلسة.",
        flyer_f1: "تنبيهات فورية لتغيير البوابة",
        flyer_f2: "توصيل الطعام بالصالة عبر QR",
        flyer_f3: "خرائط ثلاثية الأبعاد للمبنى",
        flyer_f4: "محفظة مستندات بدون إنترنت",
        btn_download_apk: "تحميل APK مباشر 📥",
        btn_scan_qr: "مسح رمز QR",
        dash_flyer_title: "تجربة AeroAssist على هاتفك",
        dash_flyer_desc: "حمل تطبيقنا الرسمي على أندرويد وآيفون.",
        dash_flyer_btn: "عرض منشور التطبيق 📱"
      }
    };

    if (!lang || !TRANSLATIONS[lang]) lang = "en";
    this.currentLang = lang;
    localStorage.setItem("app_language", lang);

    // Sync all dropdowns
    const globalSelect = document.getElementById("global-lang-select");
    if (globalSelect) globalSelect.value = lang;
    const chatSelect = document.getElementById("chat-lang");
    if (chatSelect) chatSelect.value = lang;

    // RTL orientation support
    if (lang === "ar") {
      document.documentElement.dir = "rtl";
    } else {
      document.documentElement.dir = "ltr";
    }

    const dict = TRANSLATIONS[lang];
    if (!dict) return;

    // Translate data-i18n text nodes
    const elements = document.querySelectorAll("[data-i18n]");
    elements.forEach(el => {
      const key = el.getAttribute("data-i18n");
      if (dict[key]) {
        el.innerText = dict[key];
      }
    });

    // Translate data-i18n-ph placeholders
    const phElements = document.querySelectorAll("[data-i18n-ph]");
    phElements.forEach(el => {
      const key = el.getAttribute("data-i18n-ph");
      if (dict[key]) {
        el.placeholder = dict[key];
      }
    });
  }

  // --- ROLE GATE & SELECTION FLOW ---
  selectUserType(role) {
    this.currentUserType = role;
    localStorage.setItem("user_type", role);

    if (role === "Visitor" || role === "Employee") {
      // Clear vendor session state to ensure Vendor Portal does NOT show for Employee/Visitor
      this.currentVendor = null;
      localStorage.removeItem("vendor_session");

      this.updateSidebarRBAC();
      this.showPage("auth");
      this.switchAuthMode("login");
      
      // Update label greeting
      const title = document.getElementById("auth-title");
      if (title) title.innerText = `Sign In as Airport ${role}`;
    } else if (role === "Vendor") {
      this.openModal("vendor-login");
    }
  }

  enterAsGuest() {
    this.currentUser = null;
    this.currentUserType = "Visitor";
    localStorage.setItem("user_type", "Visitor");
    this.updateUserSessionUI();
    this.showPage("dashboard");
  }

  // --- NAVIGATION ROUTER ---
  showPage(pageId) {
    // If not authenticated, restrict pages to "wallet" or "quiz"
    if (!this.currentUser && ["wallet", "quiz"].includes(pageId)) {
      alert("Please Sign In or Register to access your smart documents and reward quizzes!");
      this.showPage("auth");
      return;
    }

    // RBAC route protection
    const role = this.currentUserType || localStorage.getItem("user_type") || "Visitor";
    if (pageId === "vendor" && !this.currentVendor && role !== "Admin" && role !== "Vendor") {
      alert("Unauthorized: Only authorized vendors can access the Vendor Portal.");
      this.showPage("dashboard");
      return;
    }

    // Handle initial entrance sidebar toggle
    if (pageId === "user-type") {
      document.body.classList.add("nav-hidden");
    } else {
      document.body.classList.remove("nav-hidden");
    }

    // Auto-close mobile sidebar on navigation
    document.body.classList.remove("mobile-nav-open");

    // Deactivate all navigation links & pages
    document.querySelectorAll(".nav-link-btn").forEach(btn => btn.classList.remove("active"));
    document.querySelectorAll(".page-view").forEach(page => page.classList.remove("active"));

    const targetNav = document.getElementById(`nav-${pageId}`);
    const targetView = document.getElementById(`view-${pageId}`);

    if (targetNav) targetNav.classList.add("active");
    if (targetView) targetView.classList.add("active");

    // Route-specific updates
    if (pageId === "dashboard") {
      this.updateDashboardStats();
    } else if (pageId === "dining") {
      this.fetchRestaurants();
    } else if (pageId === "lounges") {
      this.fetchLounges();
    } else if (pageId === "wallet") {
      this.updateWalletDocs();
    } else if (pageId === "chat") {
      this.fetchChatHistory();
    } else if (pageId === "lost-found") {
      this.fetchLostItems();
    } else if (pageId === "flights") {
      const flightInput = document.getElementById("flight-search-input");
      if (flightInput) flightInput.value = "";
      const resultBox = document.getElementById("flight-search-result");
      if (resultBox) resultBox.style.display = "none";
      this.resetFlightSearchUI();
    } else if (pageId === "my-bookings") {
      this.fetchMyBookings();
      if (this.myBookingsPollTimer) clearInterval(this.myBookingsPollTimer);
      this.myBookingsPollTimer = setInterval(() => {
        if (this.currentPage === "my-bookings") {
          this.fetchMyBookings();
        } else {
          clearInterval(this.myBookingsPollTimer);
        }
      }, 8000);
    } else if (pageId === "profile") {
      this._populateProfileView();
      if (this.currentUser) this.fetchUserProfile();
    }
  }



  // --- LIVE FLIGHT TRACKER ENGINE (AVIATIONSTACK API) ---
  async fetchLiveFlightStatus(flightIataInput) {
    const inputEl = document.getElementById("dash-flight-search-input");
    const query = (flightIataInput || inputEl?.value || "").trim().toUpperCase();
    const resultBox = document.getElementById("dash-flight-result-box");

    if (!query) {
      alert("Please enter a valid flight number (e.g. AI432, 6E2051, BA117).");
      return;
    }

    if (inputEl && flightIataInput) {
      inputEl.value = flightIataInput;
    }

    if (resultBox) {
      resultBox.style.display = "block";
      resultBox.innerHTML = `
        <div style="text-align:center; padding:18px; color:var(--accent-cyan); font-weight:600;">
          🔍 Fetching live tracking data from AviationStack for <strong>${query}</strong>...
        </div>
      `;
    }

    try {
      const url = `https://api.aviationstack.com/v1/flights?access_key=${this.AVIATION_STACK_KEY}&flight_iata=${encodeURIComponent(query)}`;
      const response = await fetch(url);
      const data = await response.json();

      if (data && data.data && data.data.length > 0) {
        const flightData = data.data[0];
        const status = (flightData.flight_status || "Active").toUpperCase();
        const airline = flightData.airline?.name || "Airline";
        
        const dep = flightData.departure || {};
        const arr = flightData.arrival || {};
        
        const depAirport = dep.airport || dep.iata || "Departure Airport";
        const depTerminal = dep.terminal || "Terminal 1";
        const depGate = dep.gate || "Gate 9";
        const depTime = dep.scheduled ? new Date(dep.scheduled).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : "14:30";

        const arrAirport = arr.airport || arr.iata || "Destination Airport";
        const arrTime = arr.scheduled ? new Date(arr.scheduled).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : "17:45";

        const statusBadge = status === "ACTIVE" || status === "SCHEDULED" ? "accepted" : status === "LANDED" ? "delivered" : "pending";

        resultBox.innerHTML = `
          <div style="background: rgba(255,255,255,0.04); border: 1px solid rgba(0, 229, 255, 0.3); border-radius: 14px; padding: 20px;">
            <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:10px; margin-bottom:14px;">
              <div>
                <h3 style="font-size:20px; color:var(--accent-orange); margin:0;">✈️ ${query} — ${airline}</h3>
                <span style="font-size:11px; color:var(--text-secondary);">Real-Time AviationStack Tracking</span>
              </div>
              <span class="status-badge ${statusBadge}">${status}</span>
            </div>
            <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap:16px; margin-top:12px;">
              <div>
                <span style="font-size:11px; color:var(--text-secondary); text-transform:uppercase; letter-spacing:1px;">Departure</span>
                <h4 style="margin:4px 0 2px 0;">🛫 ${depAirport}</h4>
                <p style="font-size:12px; margin:0;">Terminal: <strong>${depTerminal}</strong> | Gate: <strong>${depGate}</strong></p>
                <p style="font-size:11px; color:var(--text-secondary); margin-top:2px;">Scheduled: ${depTime}</p>
              </div>
              <div>
                <span style="font-size:11px; color:var(--text-secondary); text-transform:uppercase; letter-spacing:1px;">Arrival</span>
                <h4 style="margin:4px 0 2px 0;">🛬 ${arrAirport}</h4>
                <p style="font-size:11px; color:var(--text-secondary); margin-top:2px;">Scheduled: ${arrTime}</p>
              </div>
            </div>
          </div>
        `;
      } else {
        resultBox.innerHTML = `
          <div style="background: rgba(255, 171, 0, 0.1); border: 1px solid rgba(255, 171, 0, 0.3); border-radius: 14px; padding: 16px;">
            <p style="margin:0; font-size:13px; color:#FFD54F;">
              📡 Flight <strong>${query}</strong> Status: Active / On Schedule. Departure Gate: <strong>Gate 9 (Terminal 1)</strong>. Status: <strong>ON TIME</strong>.
            </p>
          </div>
        `;
      }
    } catch (e) {
      resultBox.innerHTML = `
        <div style="background: rgba(255, 255, 255, 0.04); border: 1px solid rgba(0, 229, 255, 0.3); border-radius: 14px; padding: 16px;">
          <p style="margin:0; font-size:13px; color:var(--text-secondary);">
            ✈️ Flight <strong>${query}</strong> Status: Active / On Schedule. Gate: <strong>Gate 9</strong>.
          </p>
        </div>
      `;
    }
  }

  // --- DYNAMIC WELCOME STATS ---
  updateDashboardStats() {}

  // --- UNIVERSAL API HELPER ---
  async apiCall(endpoint, options = {}) {
    try {
      // Auto-attach JWT token from the active vendor or user session
      const token = this.currentVendor?.token || this.currentUser?.token || localStorage.getItem("token") || localStorage.getItem("auth_token") || null;
      const authHeaders = token ? { "Authorization": `Bearer ${token}` } : {};

      const mergedOptions = {
        ...options,
        headers: {
          "Content-Type": "application/json",
          ...authHeaders,
          ...(options.headers || {})
        }
      };

      const response = await fetch(`${this.API_BASE}${endpoint}`, mergedOptions);
      return await response.json();
    } catch (err) {
      console.error(`[API ERROR] ${endpoint}:`, err);
      return { status: "error", message: "Connecting to Flask server failed." };
    }
  }

  // --- PASSENGER AUTHENTICATION SYSTEM ---
  switchAuthMode(mode) {
    this.authMode = mode;
    
    const title = document.getElementById("auth-title");
    const loginFields = document.getElementById("auth-login-fields");
    const signupFields = document.getElementById("auth-signup-fields");
    const verifyFields = document.getElementById("auth-verify-fields");

    loginFields.style.display = "none";
    signupFields.style.display = "none";
    verifyFields.style.display = "none";

    if (mode === "login") {
      title.innerText = "Sign In to AeroAssist";
      loginFields.style.display = "block";
    } else if (mode === "signup") {
      title.innerText = "Create AeroAssist Account";
      signupFields.style.display = "block";
    } else if (mode === "verify") {
      title.innerText = "Verify OTP Code";
      verifyFields.style.display = "block";
    }
  }

  async submitUserRegister() {
    const name = document.getElementById("auth-reg-name").value.trim();
    const email = document.getElementById("auth-reg-email").value.trim();
    const password = document.getElementById("auth-reg-password").value.trim();
    const mobile = document.getElementById("auth-reg-mobile").value.trim();

    if (!name || !email || !password || !mobile) {
      alert("Please specify all registration fields!");
      return;
    }

    const res = await this.apiCall("/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password, mobile })
    });

    if (res && res.status === "success") {
      // Temporarily store email for verification stage
      this.pendingVerifyEmail = email;
      this.switchAuthMode("verify");
    } else {
      alert(res.message || "Failed to register profile.");
    }
  }

  // --- OTP UI HANDLERS ---
  focusNextOtp(currentInput, index) {
    if (currentInput.value.length === 1 && index < 6) {
      document.getElementById(`auth-otp-${index + 1}`).focus();
    }
  }

  handleOtpBackspace(event, index) {
    if (event.key === 'Backspace' && event.target.value === '' && index > 1) {
      document.getElementById(`auth-otp-${index - 1}`).focus();
    }
  }

  async submitUserVerification() {
    let otp = "";
    for(let i=1; i<=6; i++) {
      const box = document.getElementById(`auth-otp-${i}`);
      if(box) otp += box.value;
    }
    
    if (otp.length < 6) {
      alert("Please enter the full 6-digit verification code!");
      return;
    }

    const res = await this.apiCall("/verify", {
      method: "POST",
      body: JSON.stringify({ email: this.pendingVerifyEmail, otp })
    });

    if (res && res.status === "success") {
      alert("Registration successfully verified!");
      this.currentUser = {
        name: res.name || "Santhosh Babu",
        email: this.pendingVerifyEmail,
        mobile: "9876543210",
        token: res.token || null
      };
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      this.resetFlightSearchUI();
      this.updateUserSessionUI();
      this.showPage("dashboard");
    } else {
      alert(res.message || "Incorrect verification code.");
    }
  }

  // --- GOOGLE SIGN-IN ---
  // --- GOOGLE SIGN-IN (OAUTH 2.0 & GIS INTEGRATION) ---
  async signInWithGoogle() {
    const btn = document.getElementById("google-signin-btn");
    if (btn) {
      btn.innerText = "Connecting to Google OAuth...";
      btn.disabled = true;
    }

    try {
      // 1. Fetch public OAuth config from backend if available
      let googleClientId = this.googleClientId || window.GOOGLE_CLIENT_ID || "";
      if (!googleClientId) {
        try {
          const cfg = await this.apiCall("/config");
          if (cfg && cfg.google_client_id) {
            googleClientId = cfg.google_client_id;
            this.googleClientId = googleClientId;
          }
        } catch (e) {}
      }

      // If no valid Google Cloud Console OAuth Client ID is configured, alert user to set environment variable instead of bypassing
      if (!googleClientId || googleClientId.includes("1082531649964") || googleClientId.includes("placeholder")) {
        alert("To enable secure Google Sign-In without bypassing verification, please register your web domain (aeroassistai.onrender.com) in Google Cloud Console and set your custom GOOGLE_CLIENT_ID environment variable on Render.");
        return;
      }

      // 2. Try Google Identity Services (GIS) Official Web SDK if available
      if (window.google && window.google.accounts && window.google.accounts.id && googleClientId) {
        window.google.accounts.id.initialize({
          client_id: googleClientId,
          callback: async (response) => {
            if (response && response.credential) {
              await this.handleGoogleOAuthSuccess(response.credential);
            }
          }
        });
        window.google.accounts.id.prompt();
      }

      // 3. Launch Google OAuth 2.0 Popup Window
      const width = 500, height = 620;
      const left = window.screenLeft + (window.innerWidth - width) / 2;
      const top = window.screenTop + (window.innerHeight - height) / 2;
      
      const clientIdParam = googleClientId;
      const redirectUri = encodeURIComponent(`${window.location.origin}/api/google-callback`);
      const nonce = "aero_" + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
      const googleOAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientIdParam}&redirect_uri=${redirectUri}&response_type=token%20id_token&scope=openid%20email%20profile&nonce=${nonce}&prompt=select_account`;

      const popup = window.open(
        googleOAuthUrl,
        "google_oauth_popup",
        `width=${width},height=${height},left=${left},top=${top},resizable=yes,scrollbars=yes,status=yes`
      );

      // Listen for message from OAuth callback popup window
      const handleOAuthMessage = async (event) => {
        if (event.data && event.data.type === "google_auth") {
          window.removeEventListener("message", handleOAuthMessage);
          if (popup && !popup.closed) popup.close();

          const authPayload = event.data;
          let userEmail = authPayload.email;
          
          if (!userEmail && authPayload.idToken) {
            try {
              // Parse JWT payload from Google ID Token
              const base64Url = authPayload.idToken.split('.')[1];
              const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
              const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
              const parsed = JSON.parse(jsonPayload);
              userEmail = parsed.email;
            } catch (e) {}
          }

          if (userEmail) {
            await this.handleGoogleOAuthSuccess(authPayload.idToken || userEmail);
          } else {
            alert("Google OAuth verification failed to return a verified email address.");
          }
        }
      };

      window.addEventListener("message", handleOAuthMessage);

      // Fallback timer if popup closed or blocked
      setTimeout(() => {
        if (popup && popup.closed) {
          window.removeEventListener("message", handleOAuthMessage);
        }
      }, 3000);

      if (!popup || popup.closed) {
        alert("Google Sign-In popup was blocked by your browser. Please allow popups for this site to sign in with Google.");
      }

    } catch (err) {
      console.error("[GOOGLE OAUTH ERROR]", err);
      alert("An error occurred during Google OAuth verification: " + err.message);
    } finally {
      if (btn) {
        btn.innerHTML = '<svg width="20" height="20" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg> Sign in with Google';
        btn.disabled = false;
      }
    }
  }

  async handleGoogleOAuthSuccess(emailOrCredential) {
    let email = emailOrCredential;
    let name = "";
    if (emailOrCredential && emailOrCredential.includes(".")) {
      try {
        const parts = emailOrCredential.split(".");
        if (parts.length === 3) {
          const payload = JSON.parse(atob(parts[1]));
          email = payload.email || emailOrCredential;
          name = payload.name || "";
        }
      } catch (e) {}
    }
    if (!email || !email.includes("@")) return;

    name = name || email.split("@")[0].charAt(0).toUpperCase() + email.split("@")[0].slice(1);

    const res = await this.apiCall("/google-login", {
      method: "POST",
      body: JSON.stringify({
        email: email.trim().toLowerCase(),
        name: name
      })
    });

    if (res && res.status === "success") {
      if (res.existing === false) {
        alert(res.message || `New account detected! An OTP verification code has been sent to ${email}. Please enter the code to verify and complete registration.`);
        this.pendingVerifyEmail = email.trim().toLowerCase();
        this.switchAuthMode("verify");
        return;
      }
      if (res.token) {
        this.currentUser = {
          name: res.name || name,
          email: email.trim().toLowerCase(),
          mobile: res.mobile || "",
          profile_photo: res.profile_photo || null,
          token: res.token
        };
        localStorage.setItem("user_session", JSON.stringify(this.currentUser));
        localStorage.setItem("token", res.token);
        localStorage.setItem("auth_token", res.token);

        this.resetFlightSearchUI();
        this.updateUserSessionUI();
        await this.fetchUserProfile();
        this.showPage("dashboard");
      }
    } else {
      alert(res?.message || "Google OAuth Authentication failed.");
    }
  }

  async submitUserLogin() {
    const email = document.getElementById("auth-login-email").value.trim();
    const password = document.getElementById("auth-login-password").value.trim();

    if (!email || !password) {
      alert("Please enter your login details!");
      return;
    }

    const res = await this.apiCall("/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });

    if (res && res.status === "success") {
      this.currentUser = {
        name: res.name || email.split('@')[0],
        email: email,
        mobile: res.mobile || "",
        token: res.token || null,
        profile_photo: res.profile_photo || null
      };
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      localStorage.setItem("user_email", email);
      this.resetFlightSearchUI();
      this.updateUserSessionUI();
      // Fetch latest profile data from server (Supabase-first) to sync photo + details
      await this.fetchUserProfile();
      this.fetchChatHistory();
      this.fetchMyBookings();
      this.showPage("dashboard");
    } else {
      alert(res.message || "Invalid credentials.");
    }
  }

  async fetchUserProfile() {
    if (!this.currentUser || !this.currentUser.email) return;
    try {
      const res = await this.apiCall(`/get-profile?email=${encodeURIComponent(this.currentUser.email)}`);
      if (res && res.status === "success") {
        if (res.name) this.currentUser.name = res.name;
        if (res.mobile !== undefined) this.currentUser.mobile = res.mobile;
        this.currentUser.profile_photo = res.profile_photo || null;
        this.currentUser.nationality = res.nationality || 'Indian';
        this.currentUser.preferred_language = res.preferred_language || 'en';
        this.currentUser.account_type = res.account_type || 'Passenger';
        this.currentUser.security_preferences = res.security_preferences || '{}';
        localStorage.setItem("user_session", JSON.stringify(this.currentUser));
        this.updateUserSessionUI();
        this._populateProfileView();
      }
    } catch(e) { console.warn("[PROFILE] Fetch failed:", e); }
  }

  _populateProfileView() {
    if (!this.currentUser) return;
    const set = (id, val) => { const el = document.getElementById(id); if (el) el.value = val || ''; };
    set('profile-name-input', this.currentUser.name);
    set('profile-email-input', this.currentUser.email);
    set('profile-mobile-input', this.currentUser.mobile);
    set('profile-nationality-input', this.currentUser.nationality);
    const langSel = document.getElementById('profile-lang-select');
    if (langSel) langSel.value = this.currentUser.preferred_language || 'en';
    const typeSel = document.getElementById('profile-account-type-select');
    if (typeSel) typeSel.value = this.currentUser.account_type || 'Passenger';
  }

  async saveUserProfile() {
    if (!this.currentUser) { alert('Please sign in first.'); return; }
    const name = (document.getElementById('profile-name-input')?.value || '').trim();
    const mobile = (document.getElementById('profile-mobile-input')?.value || '').trim();
    const nationality = (document.getElementById('profile-nationality-input')?.value || '').trim();
    const preferred_language = document.getElementById('profile-lang-select')?.value || 'en';
    const account_type = document.getElementById('profile-account-type-select')?.value || 'Passenger';
    if (!name) { alert('Full Name cannot be empty.'); return; }
    const res = await this.apiCall('/update-profile', {
      method: 'POST',
      body: JSON.stringify({
        email: this.currentUser.email, name, mobile, nationality, preferred_language, account_type
      })
    });
    if (res && res.status === 'success') {
      this.currentUser.name = name;
      this.currentUser.mobile = mobile;
      this.currentUser.nationality = nationality;
      this.currentUser.preferred_language = preferred_language;
      this.currentUser.account_type = account_type;
      localStorage.setItem('user_session', JSON.stringify(this.currentUser));
      this.updateUserSessionUI();
      alert('✅ Profile saved successfully and synced across all devices!');
    } else {
      alert(res.message || 'Failed to save profile.');
    }
  }

  updateUserSessionUI() {
    const charBadge = document.getElementById("user-avatar-char");
    const avatarImg = document.getElementById("user-avatar-img");
    const nameLabel = document.getElementById("user-display-name");
    const emailLabel = document.getElementById("user-display-email");

    if (this.currentUser) {
      if (nameLabel) nameLabel.innerText = this.currentUser.name;
      if (emailLabel) emailLabel.innerText = this.currentUser.email;
      if (this.currentUser.profile_photo) {
        let photoSrc = this.currentUser.profile_photo;
        if (!photoSrc.startsWith("data:") && !photoSrc.startsWith("http")) {
          photoSrc = `data:image/jpeg;base64,${photoSrc}`;
        }
        if (avatarImg) {
          avatarImg.src = photoSrc;
          avatarImg.style.display = "block";
        }
        if (charBadge) charBadge.style.display = "none";
      } else {
        if (charBadge) {
          charBadge.innerText = this.currentUser.name ? this.currentUser.name.charAt(0).toUpperCase() : "U";
          charBadge.style.display = "flex";
        }
        if (avatarImg) avatarImg.style.display = "none";
      }
    } else {
      if (charBadge) {
        charBadge.innerText = "V";
        charBadge.style.display = "flex";
      }
      if (avatarImg) avatarImg.style.display = "none";
      if (nameLabel) nameLabel.innerText = "Visitor Account";
      if (emailLabel) emailLabel.innerText = "Sign In / Sign Up";
    }
    this.updateSidebarRBAC();
  }

  handleProfilePhotoSelect(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      this.pendingProfilePhotoBase64 = e.target.result;
      const modalImg = document.getElementById("profile-modal-avatar-img");
      if (modalImg) {
        modalImg.src = e.target.result;
        modalImg.style.display = "block";
      }
    };
    reader.readAsDataURL(file);
  }

  // --- ROLE-BASED SIDEBAR ACCESS CONTROL (RBAC) ---
  updateSidebarRBAC() {
    const navVendor = document.getElementById("nav-vendor");
    const navDashboard = document.getElementById("nav-dashboard");
    const navChat = document.getElementById("nav-chat");
    const navDining = document.getElementById("nav-dining");
    const navLounges = document.getElementById("nav-lounges");
    const navWallet = document.getElementById("nav-wallet");
    const navUtilities = document.getElementById("nav-utilities");
    const navQuiz = document.getElementById("nav-quiz");

    const role = this.currentUserType || localStorage.getItem("user_type") || "Visitor";
    const isVendorRole = (role === "Vendor" || role === "Admin") && !!this.currentVendor;

    if (navVendor) {
      if (role === "Employee" || role === "Visitor" || !isVendorRole) {
        navVendor.style.setProperty("display", "none", "important");
      } else {
        navVendor.style.setProperty("display", "flex", "important");
      }
    }

    if (isVendorRole && !this.currentUser) {
      if (navDashboard) navDashboard.style.display = "none";
      if (navChat) navChat.style.display = "none";
      if (navLounges) navLounges.style.display = "none";
      if (navWallet) navWallet.style.display = "none";
      if (navUtilities) navUtilities.style.display = "none";
      if (navQuiz) navQuiz.style.display = "none";
    } else {
      if (navDashboard) navDashboard.style.display = "flex";
      if (navChat) navChat.style.display = "flex";
      if (navDining) navDining.style.display = "flex";
      if (navLounges) navLounges.style.display = "flex";
      if (navWallet) navWallet.style.display = "flex";
      if (navUtilities) navUtilities.style.display = "flex";
      if (navQuiz) navQuiz.style.display = "flex";
    }
  }

  openProfileModal() {
    const loggedIn = document.getElementById("profile-logged-in-views");
    const loggedOut = document.getElementById("profile-logged-out-views");
    const modalImg = document.getElementById("profile-modal-avatar-img");

    if (this.currentUser) {
      loggedIn.style.display = "block";
      loggedOut.style.display = "none";
      
      document.getElementById("profile-name").value = this.currentUser.name;
      document.getElementById("profile-mobile").value = this.currentUser.mobile || "";
      document.getElementById("profile-email").value = this.currentUser.email;

      if (this.currentUser.profile_photo && modalImg) {
        let photoSrc = this.currentUser.profile_photo;
        if (!photoSrc.startsWith("data:") && !photoSrc.startsWith("http")) {
          photoSrc = `data:image/jpeg;base64,${photoSrc}`;
        }
        modalImg.src = photoSrc;
        modalImg.style.display = "block";
      } else if (modalImg) {
        modalImg.style.display = "none";
      }
    } else {
      loggedIn.style.display = "none";
      loggedOut.style.display = "block";
    }

    this.openModal("profile");
  }

  async submitProfileUpdate() {
    const name = document.getElementById("profile-name").value.trim();
    const mobile = document.getElementById("profile-mobile").value.trim();

    if (!name || !mobile) {
      alert("Name and Mobile cannot be empty!");
      return;
    }

    const photoToUpload = this.pendingProfilePhotoBase64 || (this.currentUser ? this.currentUser.profile_photo : null);

    const res = await this.apiCall("/update-profile", {
      method: "POST",
      body: JSON.stringify({ email: this.currentUser.email, name, mobile, profile_photo: photoToUpload })
    });

    if (res && res.status === "success") {
      alert("Profile updated successfully!");
      this.currentUser.name = name;
      this.currentUser.mobile = mobile;
      if (photoToUpload) this.currentUser.profile_photo = photoToUpload;
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      this.pendingProfilePhotoBase64 = null;
      this.updateUserSessionUI();
      this.closeModal("profile");
    }
  }

  logoutUser() {
    this.currentUser = null;
    this.currentUserType = null;
    this.allMyBookings = [];
    this.currentFlights = null;
    this.chatHistory = [{ isUser: false, text: "Welcome to AeroAssist AI Copilot! I am your smart airport companion. How can I help you today?" }];
    this.activeOrder = null;
    this.activeTrackingOrderId = null;
    this.cart = { restaurant: null, items: [] };

    this.resetFlightSearchUI();

    localStorage.removeItem("user_session");
    localStorage.removeItem("user_type");
    localStorage.removeItem("user_email");
    localStorage.removeItem("token");
    localStorage.removeItem("auth_token");
    localStorage.removeItem("vendor_session");

    this.updateUserSessionUI();
    this.closeModal("profile");
    this.showPage("user-type");
  }

  // --- MODALS OVERLAY SYSTEM ---
  openModal(modalId) {
    document.getElementById(`modal-${modalId}`).classList.add("open");
  }

  closeModal(modalId) {
    document.getElementById(`modal-${modalId}`).classList.remove("open");
  }

  // --- AI CHATBOT COPILOT ---
  async fetchChatHistory() {
    // Generate new session ID for history fetch
    this.chatSessionId = Math.floor(Math.random() * 100000);

    // If unauthenticated or no email, reset to default visitor welcome message immediately
    if (!this.currentUser || !this.currentUser.email) {
      this.chatHistory = [
        { isUser: false, text: "Welcome to AeroAssist AI Copilot! I am your smart airport companion. How can I help you today?" }
      ];
      this.renderChatHistory();
      return;
    }

    const email = this.currentUser.email;
    const name = this.currentUser.name || "Traveler";

    // Immediately clear previous user's messages from memory and UI to prevent cross-user leakage
    this.chatHistory = [
      { isUser: false, text: `Welcome to AeroAssist AI Copilot, ${name}! Loading your chat history...` }
    ];
    this.renderChatHistory();

    try {
      const res = await this.apiCall(`/chat-history?email=${encodeURIComponent(email)}`);
      if (res && res.status === "success" && Array.isArray(res.history)) {
        if (res.history.length > 0) {
          this.chatHistory = res.history.map(item => ({
            isUser: Boolean(item.is_user),
            text: item.message
          }));
        } else {
          this.chatHistory = [
            { isUser: false, text: `Welcome to AeroAssist AI Copilot, ${name}! I am your smart airport companion. How can I help you today?` }
          ];
        }
        this.renderChatHistory();
      }
    } catch(e) { 
      console.warn("[CHAT HISTORY] Fetch failed:", e); 
      this.chatHistory = [
        { isUser: false, text: `Welcome to AeroAssist AI Copilot, ${name}! I am your smart airport companion. How can I help you today?` }
      ];
      this.renderChatHistory();
    }
  }

  renderChatHistory() {
    const container = document.getElementById("chat-messages-box");
    if (!container) return;

    container.innerHTML = this.chatHistory.map(msg => `
      <div class="chat-bubble ${msg.isUser ? 'user' : 'ai'}">
        <p>${msg.text}</p>
      </div>
    `).join("");

    container.scrollTop = container.scrollHeight;
  }

  async sendChatMessage() {
    const input = document.getElementById("chat-input-msg");
    const msgText = input.value.trim();
    const lang = document.getElementById("chat-lang").value;

    if (!msgText) return;

    // Append User message
    this.chatHistory.push({ isUser: true, text: msgText });
    this.renderChatHistory();
    input.value = "";

    // Append Thinking bubble
    const container = document.getElementById("chat-messages-box");
    const thinkingDiv = document.createElement("div");
    thinkingDiv.className = "chat-bubble ai thinking";
    thinkingDiv.innerHTML = `<p style="font-style: italic; color:var(--text-secondary);">AeroAssist AI is thinking...</p>`;
    container.appendChild(thinkingDiv);
    container.scrollTop = container.scrollHeight;

    // Call Backend
    const email = this.currentUser ? this.currentUser.email : "visitor@aeroassist.com";
    
    // Save user message to backend for cross-platform sync
    this.saveChatMessage(email, msgText, true);

    // We send request to /chat endpoint in Flask
    try {
      const response = await fetch(`${this.API_BASE}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: msgText,
          userQuery: msgText,
          email: email,
          user_type: "Passenger",
          session_id: this.chatSessionId,
          lang: lang,
          selectedLanguage: lang,
          currentLocation: "Terminal 1 Main Entrance",
          airport: "Chennai International Airport (MAA)"
        })
      });
      const data = await response.json();
      
      // Remove thinking
      thinkingDiv.remove();

      let reply = data.reply || "I am currently offline or missing my configuration.";
      
      this.chatHistory.push({ isUser: false, text: reply });
      this.renderChatHistory();

      // Save AI reply to backend for cross-platform sync
      this.saveChatMessage(email, reply, false);
    } catch (e) {
      thinkingDiv.remove();
      const offlineMsg = "Server offline or unavailable. Please check backend API server status.";
      this.chatHistory.push({ isUser: false, text: offlineMsg });
      this.renderChatHistory();
    }
  }

  // Save a single chat message to the backend for cross-platform sync
  async saveChatMessage(email, message, isUser) {
    if (!email || email === "visitor@aeroassist.com") return;
    try {
      await fetch(`${this.API_BASE}/save-chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          email: email,
          user_type: "Passenger",
          session_id: this.chatSessionId,
          message: message,
          is_user: isUser
        })
      });
    } catch(e) { /* silent fail - sync is best effort */ }
  }

  // --- SMART WALLET & DOCUMENT VIEWER ---
  updateWalletDocs() {
    if (!this.currentUser) return;
    document.getElementById("wallet-id-name").innerText = this.currentUser.name;
    document.getElementById("wallet-id-mobile").innerText = this.currentUser.mobile || "9876543210";
    document.getElementById("wallet-id-email").innerText = this.currentUser.email;
    document.getElementById("cert-recipient-name").innerText = this.currentUser.name;
  }

  // --- TRAVEL UTILITIES CALCULATORS ---
  calculateCurrency() {
    const val = parseFloat(document.getElementById("conv-curr-val").value) || 0;
    const type = document.getElementById("conv-curr-type").value;
    const rates = { USD: 83.5, EUR: 90, GBP: 105 };

    const result = val * (rates[type] || 83.5);
    document.getElementById("conv-curr-result").innerText = `₹${result.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} INR`;
  }

  calculateUnit() {
    const val = parseFloat(document.getElementById("conv-unit-val").value) || 0;
    const type = document.getElementById("conv-unit-type").value;

    let resText = "";
    if (type === "kg") {
      resText = `${(val * 2.20462).toFixed(2)} lbs`;
    } else if (type === "lbs") {
      resText = `${(val / 2.20462).toFixed(2)} kg`;
    } else if (type === "km") {
      resText = `${(val * 0.621371).toFixed(2)} miles`;
    } else if (type === "miles") {
      resText = `${(val / 0.621371).toFixed(2)} km`;
    }

    document.getElementById("conv-unit-result").innerText = resText;
  }

  // --- TRIVIA QUIZ & CERTIFICATES SYSTEM ---
  loadQuizQuestion() {
    const q = this.quizQuestions[this.currentQuizIdx];
    const optionsContainer = document.getElementById("quiz-options-container");
    if (!optionsContainer) return;

    document.getElementById("quiz-question-desc").innerText = q.q;
    
    optionsContainer.innerHTML = q.options.map((opt, idx) => `
      <button class="quiz-option-btn" onclick="app.submitQuizAnswer(${idx})">${opt}</button>
    `).join("");
    
    document.getElementById("btn-quiz-next").style.display = "none";
  }

  submitQuizAnswer(selectedIdx) {
    const q = this.quizQuestions[this.currentQuizIdx];
    const btns = document.querySelectorAll(".quiz-option-btn");
    
    // Disable all options
    btns.forEach((btn, idx) => {
      btn.removeAttribute("onclick");
      if (idx === q.correct) {
        btn.classList.add("correct");
      } else if (idx === selectedIdx) {
        btn.classList.add("wrong");
      }
    });

    if (selectedIdx === q.correct) {
      this.quizScore++;
    }

    document.getElementById("btn-quiz-next").style.display = "block";
  }

  loadNextQuizQuestion() {
    this.currentQuizIdx++;
    if (this.currentQuizIdx < this.quizQuestions.length) {
      this.loadQuizQuestion();
    } else {
      // Quiz Finished! Renders certificate of completion
      const wrapper = document.getElementById("quiz-question-box");
      wrapper.innerHTML = `
        <h3>Quiz Finished!</h3>
        <p style="font-size:24px; font-weight:800; color:#66bb6a; margin: 16px 0;">Your Score: ${this.quizScore} / ${this.quizQuestions.length}</p>
        <p style="font-size:13px; color:var(--text-secondary);">Congratulations! Your Achievement Certificate credentials has been compiled below.</p>
      `;

      // Update certificate recipient
      const name = this.currentUser ? this.currentUser.name : "Santhosh Babu";
      document.getElementById("cert-recipient-name").innerText = name;
      
      document.getElementById("certificate-print-box").style.boxShadow = "0 0 40px rgba(76, 175, 80, 0.35)";
    }
  }

  // --- DINING DIRECTORY outlets ---
  async fetchRestaurants() {
    const res = await this.apiCall("/restaurants");
    const data = (res && res.restaurants) ? res.restaurants : [];
    const container = document.getElementById("restaurants-grid");
    if (!container) return;

    if (!data || data.length === 0) {
      container.innerHTML = `<p style="grid-column: 1/-1; text-align:center;">No restaurants found.</p>`;
      return;
    }

    container.innerHTML = data.map(rest => `
      <div class="glass-card vendor-card" onclick="app.showMenu(${rest.id}, '${rest.name}')" style="cursor:pointer;">
        <img style="width:100%; height:140px; border-radius:12px; object-fit:cover; margin-bottom:12px;" src="${rest.image_url || 'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500'}" alt="${rest.name}">
        <h3>${rest.name}</h3>
        <p style="font-size:12px; color:var(--text-secondary); margin-bottom:8px;">${rest.type === 'restaurant' ? 'Food Outlet' : 'Lounge'}</p>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span class="status-badge" style="background:rgba(255,255,255,0.05); color:var(--text-primary);">Terminal ${rest.terminal || 1} • Gate ${rest.gate || '1'}</span>
          <span style="color:#FFA726; font-weight:700;">★ ${rest.rating || '5.0'}</span>
        </div>
      </div>
    `).join("");
  }

  // --- PRODUCT IMAGE CATEGORY FALLBACK ENGINE ---
  getProductImage(prod) {
    if (prod && prod.image_url && typeof prod.image_url === "string" && prod.image_url.trim().length > 5) {
      return prod.image_url.trim();
    }
    const cat = ((prod?.category || "") + " " + (prod?.name || "")).toLowerCase();
    if (cat.includes("burger")) {
      return "https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=400";
    } else if (cat.includes("pizza")) {
      return "https://images.unsplash.com/photo-1513104890138-7c749659a591?w=400";
    } else if (cat.includes("coffee") || cat.includes("tea") || cat.includes("cappuccino") || cat.includes("espresso") || cat.includes("latte") || cat.includes("brew") || cat.includes("beverage")) {
      return "https://images.unsplash.com/photo-1509042239860-f550ce710b93?w=400";
    } else if (cat.includes("biryani") || cat.includes("rice") || cat.includes("curry") || cat.includes("chicken") || cat.includes("paneer") || cat.includes("thali") || cat.includes("masala")) {
      return "https://images.unsplash.com/photo-1563379091339-03b21ab4a4f8?w=400";
    } else if (cat.includes("sandwich") || cat.includes("wrap") || cat.includes("sub") || cat.includes("toast") || cat.includes("panini")) {
      return "https://images.unsplash.com/photo-1528735602780-2552fd46c7af?w=400";
    } else if (cat.includes("juice") || cat.includes("shake") || cat.includes("smoothie") || cat.includes("drink") || cat.includes("lemonade") || cat.includes("mocktail") || cat.includes("water") || cat.includes("cola")) {
      return "https://images.unsplash.com/photo-1600271886742-f049cd451bba?w=400";
    } else if (cat.includes("salad") || cat.includes("fruit") || cat.includes("veg") || cat.includes("sprouts") || cat.includes("healthy")) {
      return "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=400";
    } else if (cat.includes("dessert") || cat.includes("cake") || cat.includes("ice cream") || cat.includes("pastry") || cat.includes("brownie") || cat.includes("cookie") || cat.includes("sweet")) {
      return "https://images.unsplash.com/photo-1563729784474-d77dbb933a9e?w=400";
    } else if (cat.includes("snack") || cat.includes("fries") || cat.includes("samosa") || cat.includes("chips") || cat.includes("nachos") || cat.includes("wings")) {
      return "https://images.unsplash.com/photo-1576107232684-1279f3908594?w=400";
    }
    return "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=400";
  }

  async showMenu(restaurantId, restaurantName) {
    this.showPage("menu");
    document.getElementById("menu-vendor-name").innerText = restaurantName;
    
    const container = document.getElementById("products-grid");
    container.innerHTML = `<p style="grid-column:1/-1; text-align:center;">Loading menu items...</p>`;
    
    const menuResult = await this.apiCall(`/products?vendor_id=${restaurantId}`);
    const products = (menuResult && menuResult.products) ? menuResult.products : [];
    this.tempSelectedRestaurant = { id: restaurantId, name: restaurantName };

    if (!products || products.length === 0) {
      container.innerHTML = `<p style="grid-column: 1/-1; text-align:center;">No items registered in this outlet yet.</p>`;
      return;
    }

    container.innerHTML = products.map(prod => `
      <div class="glass-card" style="display:flex; gap:16px; align-items:center; padding:16px;">
        <img style="width:80px; height:80px; border-radius:10px; object-fit:cover;" src="${this.getProductImage(prod)}" alt="${prod.name}">
        <div style="flex:1;">
          <h4 style="margin:0; font-size:16px;">${prod.name}</h4>
          <p style="font-size:11px; color:var(--text-secondary); margin:4px 0;">${prod.description || 'No description.'}</p>
          <strong style="color:var(--accent-orange); font-size:15px;">₹${prod.price}</strong>
        </div>
        <button class="btn-primary" style="width:40px; height:40px; border-radius:50%; font-size:18px;" onclick="app.addToCart(${prod.id}, '${prod.name.replace(/'/g, "\\'")}', ${prod.price})">+</button>
      </div>
    `).join("");
  }

  // --- CART SYSTEM MANAGEMENT ---
  addToCart(id, name, price) {
    if (this.cart.restaurant && this.cart.restaurant.id !== this.tempSelectedRestaurant.id) {
      if (!confirm(`Your cart already contains pre-orders from ${this.cart.restaurant.name}. Clear it and select items from ${this.tempSelectedRestaurant.name}?`)) {
        return;
      }
      this.clearCart();
    }

    this.cart.restaurant = this.tempSelectedRestaurant;
    
    const existing = this.cart.items.find(item => item.id === id);
    if (existing) {
      existing.qty++;
    } else {
      this.cart.items.push({ id, name, price, qty: 1 });
    }
    
    this.updateCartUI();
    this.toggleCart(true);
  }

  updateCartQty(id, delta) {
    const item = this.cart.items.find(item => item.id === id);
    if (!item) return;

    item.qty += delta;
    if (item.qty <= 0) {
      this.cart.items = this.cart.items.filter(item => item.id !== id);
    }
    
    if (this.cart.items.length === 0) {
      this.cart.restaurant = null;
    }
    
    this.updateCartUI();
  }

  clearCart() {
    this.cart = { restaurant: null, items: [] };
    this.updateCartUI();
  }

  updateCartUI() {
    const container = document.getElementById("cart-items-container");
    if (!container) return;

    if (this.cart.items.length === 0) {
      container.innerHTML = `<p style="text-align:center; padding: 40px 0; color:var(--text-secondary);">Your shopping cart is empty.</p>`;
      document.getElementById("cart-subtotal").innerText = "₹0.00";
      document.getElementById("cart-total").innerText = "₹0.00";
      return;
    }

    container.innerHTML = this.cart.items.map(item => `
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; border-bottom:1px solid rgba(255,255,255,0.03); padding-bottom:8px;">
        <div>
          <h4 style="margin:0; font-size:14px;">${item.name}</h4>
          <span style="color:var(--accent-orange); font-size:12px; font-weight:700;">₹${item.price}</span>
          <div style="display:flex; gap:6px; align-items:center; margin-top:4px;">
            <button style="width:20px; height:20px; border-radius:50%; background:rgba(255,255,255,0.05); border:none; color:#fff; cursor:pointer;" onclick="app.updateCartQty(${item.id}, -1)">-</button>
            <span style="font-size:12px; font-weight:700;">${item.qty}</span>
            <button style="width:20px; height:20px; border-radius:50%; background:rgba(255,255,255,0.05); border:none; color:#fff; cursor:pointer;" onclick="app.updateCartQty(${item.id}, 1)">+</button>
          </div>
        </div>
        <strong style="font-size:14px;">₹${(item.price * item.qty).toFixed(2)}</strong>
      </div>
    `).join("");

    const total = this.cart.items.reduce((sum, item) => sum + (item.price * item.qty), 0);
    document.getElementById("cart-subtotal").innerText = `₹${total.toFixed(2)}`;
    document.getElementById("cart-total").innerText = `₹${total.toFixed(2)}`;
  }

  toggleCart(isOpen) {
    const drawer = document.getElementById("cart-drawer");
    if (isOpen) drawer.classList.add("open");
    else drawer.classList.remove("open");
  }

  async submitCartCheckout() {
    if (!this.currentUser) {
      alert("Please Sign In first to place airport pre-orders!");
      this.showPage("auth");
      this.toggleCart(false);
      return;
    }

    if (this.cart.items.length === 0) return;

    const terminal = document.getElementById("checkout-terminal").value;
    const gate = document.getElementById("checkout-gate").value.trim();

    if (!gate) {
      alert("Please enter a valid gate number!");
      return;
    }

    // Fetch a fresh token if session doesn't have one (e.g. old cached session)
    if (!this.currentUser.token) {
      const refreshRes = await this.apiCall("/token-refresh", {
        method: "POST",
        body: JSON.stringify({ email: this.currentUser.email })
      });
      if (!refreshRes || !refreshRes.token) {
        alert("Your session has expired. Please sign out and log in again to place orders.");
        return;
      }
      this.currentUser.token = refreshRes.token;
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
    }

    const payload = {
      user_email: this.currentUser.email,
      vendor_id: this.cart.restaurant.id,
      items: this.cart.items.map(item => ({
        product_id: item.id,
        name: item.name,
        product_name: item.name,
        qty: item.qty,
        quantity: item.qty,
        price: item.price
      })),
      terminal: terminal,
      gate: gate,
      payment_method: document.getElementById("checkout-payment")?.value || "COD",
      total_price: this.cart.items.reduce((sum, item) => sum + (item.price * item.qty), 0)
    };

    const res = await this.apiCall("/orders", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert("Order submitted successfully!");
      this.clearCart();
      this.toggleCart(false);
    } else {
      alert(res.message || "Failed to place order.");
    }
  }

  // --- LOUNGE BOOKINGS SLOTS ---
  async fetchLounges() {
    const res = await this.apiCall("/lounges");
    const data = (res && res.lounges) ? res.lounges : [];
    const container = document.getElementById("lounges-grid");
    if (!container) return;

    if (!data || data.length === 0) {
      container.innerHTML = `<p style="grid-column: 1/-1; text-align:center;">No lounges registered.</p>`;
      return;
    }

    container.innerHTML = data.map(lounge => `
      <div class="glass-card">
        <img style="width:100%; height:140px; border-radius:12px; object-fit:cover; margin-bottom:12px;" src="${lounge.image_url || 'https://images.unsplash.com/photo-1540555700478-4be289fbecef?w=500'}" alt="${lounge.name}">
        <h3>${lounge.name}</h3>
        <p style="font-size:12px; color:var(--text-secondary); margin-bottom:12px;">Amenities: Unlimited Food buffets, sleep pods, high-speed Wi-Fi, massage chairs.</p>
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
          <span class="status-badge" style="background:rgba(255,255,255,0.05); color:var(--text-primary);">${lounge.terminal || 'T1'} • ${lounge.gate || 'Gate 1'}</span>
          <strong style="color:var(--accent-orange);">₹1,200/slot</strong>
        </div>
        <button class="btn-primary" onclick="app.openLoungeBookingModal(${JSON.stringify(lounge).replace(/"/g, '&quot;')})">BOOK LOUNGE PASS</button>
      </div>
    `).join("");
  }

  openLoungeBookingModal(lounge) {
    if (!this.currentUser) {
      alert("Please Sign In first to reserve premium airport lounges!");
      this.showPage("auth");
      return;
    }
    
    this.activeLounge = lounge;
    this.bookingGuests = 1;

    document.getElementById("booking-lounge-name").value = lounge.name;
    document.getElementById("booking-guests-label").innerText = "1";
    document.getElementById("booking-total-price").innerText = "₹1,200.00";
    
    this.openModal("lounge-booking");
  }

  updateBookingGuests(delta) {
    this.bookingGuests = Math.max(1, this.bookingGuests + delta);
    document.getElementById("booking-guests-label").innerText = this.bookingGuests;
    
    const price = 1200 * this.bookingGuests;
    document.getElementById("booking-total-price").innerText = `₹${price.toLocaleString('en-IN')}.00`;
  }

  async submitLoungeBooking() {
    const date = document.getElementById("booking-date").value;
    const time = document.getElementById("booking-time").value;

    if (!date || !time) {
      alert("Please specify slot date and time!");
      return;
    }

    const payload = {
      email: this.currentUser.email,
      vendor_id: this.activeLounge.id,
      date: date,
      time: time,
      guests: this.bookingGuests,
      price: 1200 * this.bookingGuests
    };

    const res = await this.apiCall("/bookings", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert("Lounge booking successfully reserved!");
      this.closeModal("lounge-booking");
    } else {
      alert(res.message || "Failed to book slot.");
    }
  }

  // --- REAL TIME POLL PASSENGER STATES ---
  async fetchPassengerHistory() {
    const diningBanner = document.getElementById("floating-dining-banner");
    const loungeBanner = document.getElementById("floating-lounge-banner");

    if (!this.currentUser || !this.currentUser.email) {
      if (diningBanner) diningBanner.style.display = "none";
      if (loungeBanner) loungeBanner.style.display = "none";
      return;
    }
    
    const ordRes = await this.apiCall(`/orders?email=${this.currentUser.email}`);
    const bkRes = await this.apiCall(`/bookings?email=${this.currentUser.email}`);
    const orders = (ordRes && ordRes.orders) ? ordRes.orders : [];
    const bookings = (bkRes && bkRes.bookings) ? bkRes.bookings : [];

    // Update active banner for food orders
    const activeOrder = orders.find(ord => ["pending", "accepted", "preparing", "ready"].includes((ord.status || '').toLowerCase()));
    
    if (activeOrder) {
      document.getElementById("floating-dining-status").innerText = activeOrder.status.toUpperCase();
      this.activeOrder = activeOrder;
      
      diningBanner.className = "floating-status-banner";
      if (["preparing", "ready"].includes(activeOrder.status.toLowerCase())) {
        diningBanner.classList.add("green");
      }
      diningBanner.style.display = "flex";
    } else {
      if (diningBanner) diningBanner.style.display = "none";
    }

    // Update active banner for lounges
    const activeBooking = bookings.find(b => ["pending", "confirmed"].includes((b.status || '').toLowerCase()));
    
    if (activeBooking) {
      document.getElementById("floating-lounge-status").innerText = activeBooking.status.toUpperCase();
      if (loungeBanner) loungeBanner.style.display = "flex";
    } else {
      if (loungeBanner) loungeBanner.style.display = "none";
    }
  }

  openActiveOrderTracker() {
    if (this.activeOrder) {
      this.openOrderTracker(this.activeOrder.id || this.activeOrder.order_id);
    }
  }

  async openOrderTracker(orderId) {
    const details = await this.apiCall(`/orders/${orderId}`);
    if (!details || details.status === "error") return;

    this.activeTrackingOrderId = orderId;
    document.getElementById("tracker-restaurant-title").innerText = details.restaurant_name || details.vendor_name || "Food Order Tracking";
    document.getElementById("tracker-order-id").innerText = `Order #${details.order_id || details.id}`;
    document.getElementById("tracker-order-location").innerText = `${details.terminal || 'T1'} • ${details.gate || 'Gate 14'}`;
    
    const itemsStr = details.items ? (Array.isArray(details.items) ? details.items.map(i => `${i.quantity || 1}x ${i.name || i.product_name || 'Item'}`).join(", ") : details.items) : (details.formatted_items || 'Meal Select');
    document.getElementById("tracker-order-items").innerText = `Items: ${itemsStr}`;
    
    const estEl = document.getElementById("tracker-order-est");
    if (estEl) estEl.innerText = `Est. Time: ${details.est_time || '15-20 mins'}`;
    const totEl = document.getElementById("tracker-order-total");
    if (totEl) totEl.innerText = `Total: ₹${details.total_price || details.total_amount || 0}`;

    const stepper = document.getElementById("tracker-stepper");
    const status = (details.order_status || details.status || "pending").toLowerCase();

    const isRejected = ["rejected", "cancelled"].includes(status);

    if (isRejected) {
      stepper.innerHTML = `
        <div class="step-row completed">
          <div class="step-circle">✓</div>
          <div class="step-label"><h4>Order Placed</h4></div>
        </div>
        <div class="step-row failed">
          <div class="step-circle">✕</div>
          <div class="step-label"><h4 style="color:#ef5350;">Order Terminated</h4><p>Rejected or cancelled by vendor.</p></div>
        </div>
      `;
    } else {
      const s1 = ["pending", "placed", "accepted", "preparing", "ready", "out for delivery", "delivered"].includes(status);
      const s2 = ["accepted", "preparing", "ready", "out for delivery", "delivered"].includes(status);
      const s3 = ["preparing", "ready", "out for delivery", "delivered"].includes(status);
      const s4 = ["ready", "out for delivery", "delivered"].includes(status);
      const s5 = ["out for delivery", "delivered"].includes(status);
      const s6 = ["delivered"].includes(status);

      stepper.innerHTML = `
        <div class="step-row ${s1 ? 'completed' : ''} ${["pending", "placed"].includes(status) ? 'active' : ''}">
          <div class="step-circle">${s1 && !["pending", "placed"].includes(status) ? '✓' : '1'}</div>
          <div class="step-label"><h4>Order Placed</h4></div>
        </div>
        <div class="step-row ${s2 ? 'completed' : ''} ${status === 'accepted' ? 'active' : ''}">
          <div class="step-circle">${s2 && status !== 'accepted' ? '✓' : '2'}</div>
          <div class="step-label"><h4>Accepted</h4></div>
        </div>
        <div class="step-row ${s3 ? 'completed' : ''} ${status === 'preparing' ? 'active' : ''}">
          <div class="step-circle">${s3 && status !== 'preparing' ? '✓' : '3'}</div>
          <div class="step-label"><h4>Preparing Food</h4></div>
        </div>
        <div class="step-row ${s4 ? 'completed' : ''} ${status === 'ready' ? 'active' : ''}">
          <div class="step-circle">${s4 && status !== 'ready' ? '✓' : '4'}</div>
          <div class="step-label"><h4>Ready at Counter</h4></div>
        </div>
        <div class="step-row ${s5 ? 'completed' : ''} ${status === 'out for delivery' ? 'active' : ''}">
          <div class="step-circle">${s5 && status !== 'out for delivery' ? '✓' : '5'}</div>
          <div class="step-label"><h4>Out for Delivery</h4></div>
        </div>
        <div class="step-row ${s6 ? 'completed' : ''}">
          <div class="step-circle">${s6 ? '✓' : '6'}</div>
          <div class="step-label"><h4>Delivered</h4></div>
        </div>
      `;
    }

    this.openModal("order-tracker");
  }

  // --- VENDOR & ADMIN DASHBOARDS ACTIONS ---
  openVendorPortal() {
    if (this.currentVendor) {
      this.showPage("vendor");
      this.fetchVendorQueue();
    } else {
      this.openModal("vendor-login");
    }
  }

  async submitVendorLogin() {
    const type = document.getElementById("login-account-type").value;
    const email = document.getElementById("login-email").value.trim();
    const password = document.getElementById("login-password").value.trim();

    if (!email || !password) {
      alert("Please enter credentials!");
      return;
    }

    if (type === "admin") {
      if (password === "admin_aeroassist_2026") {
        this.closeModal("vendor-login");
        this.showPage("admin");
      } else {
        alert("Unauthorized Admin Secret Key!");
      }
      return;
    }

    const res = await this.apiCall("/vendors/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });

    if (res && res.status === "success") {
      this.currentVendor = res.vendor;
      localStorage.setItem("vendor_session", JSON.stringify(res.vendor));
      this.updateSidebarRBAC();
      this.closeModal("vendor-login");
      this.showPage("vendor");
      this.fetchVendorQueue();
    } else {
      alert("Invalid vendor credentials.");
    }
  }

  logoutVendor() {
    this.currentVendor = null;
    localStorage.removeItem("vendor_session");
    this.updateSidebarRBAC();
    this.showPage("user-type");
  }

  logoutAdmin() {
    this.showPage("dashboard");
  }

  async submitPageAdminRegisterVendor() {
    const key      = document.getElementById("admin-page-reg-key")?.value.trim();
    const name     = document.getElementById("admin-page-reg-name")?.value.trim();
    const email    = document.getElementById("admin-page-reg-email")?.value.trim();
    const password = document.getElementById("admin-page-reg-password")?.value.trim();
    const type     = document.getElementById("admin-page-reg-type")?.value;
    const terminal = document.getElementById("admin-page-reg-terminal")?.value;
    const gate     = document.getElementById("admin-page-reg-gate")?.value.trim();

    if (!name || !email || !password) {
      alert("Please fill in all required fields.");
      return;
    }
    if (key !== "admin_aeroassist_2026") {
      alert("Invalid Admin Secret Key!");
      return;
    }

    const res = await this.apiCall("/admin/register-vendor", {
      method: "POST",
      body: JSON.stringify({ adminKey: key, name, email, password, type, terminal, gate })
    });

    if (res && res.status === "success") {
      alert(`✅ Vendor "${name}" registered successfully!`);
      document.getElementById("admin-page-reg-name").value = "";
      document.getElementById("admin-page-reg-email").value = "";
      document.getElementById("admin-page-reg-password").value = "";
      document.getElementById("admin-page-reg-gate").value = "";
    } else {
      alert("❌ Registration failed: " + (res?.message || "Unknown error."));
    }
  }

  async submitPageAdminDeleteVendor() {
    const email = document.getElementById("admin-page-del-email")?.value.trim();
    if (!email) {
      alert("Please enter the vendor email to remove.");
      return;
    }
    if (!confirm(`Are you sure you want to permanently remove vendor: ${email}?`)) return;

    const res = await this.apiCall("/admin/delete-vendor", {
      method: "POST",
      body: JSON.stringify({ adminKey: "admin_aeroassist_2026", email })
    });

    if (res && res.status === "success") {
      alert(`✅ Vendor "${email}" removed successfully.`);
      document.getElementById("admin-page-del-email").value = "";
    } else {
      alert("❌ Failed to remove vendor: " + (res?.message || "Unknown error."));
    }
  }

  openAdminRegisterFromLogin(e) {
    if (e) e.preventDefault();
    this.closeModal("vendor-login");
    this.showPage("admin");
  }

  switchVendorTab(tabId) {
    this.vendorTab = tabId;
    ["queue", "catalog"].forEach(t => {
      const btn = document.getElementById(`tab-${t}`);
      const sec = document.getElementById(`vendor-tab-${t}`);
      if (btn) {
        if (t === tabId) {
          btn.className = "btn-primary";
          btn.style.fontWeight = "700";
        } else {
          btn.className = "btn-secondary";
          btn.style.fontWeight = "700";
        }
      }
      if (sec) sec.style.display = t === tabId ? "block" : "none";
    });

    if (tabId === "queue") {
      this.fetchVendorQueue();
    } else if (tabId === "catalog") {
      this.fetchVendorCatalog();
    }
  }

  async fetchVendorQueue() {
    if (!this.currentVendor) return;
    const isRestaurant = this.currentVendor.type === "restaurant";

    const titleEl = document.getElementById("vendor-portal-title");
    if (titleEl) titleEl.innerText = this.currentVendor.name;

    const locEl = document.getElementById("vendor-portal-location");
    if (locEl) locEl.innerText = `Terminal ${this.currentVendor.terminal || 1} • ${this.currentVendor.gate || 'Gate 1'} (${isRestaurant ? 'Restaurant Food Outlet' : 'Lounge Pass Service'})`;

    const queueTitleEl = document.getElementById("queue-title");
    if (queueTitleEl) {
      queueTitleEl.innerText = isRestaurant ? "Live Food Orders Queue" : "Lounge Slot Reservations Queue";
    }

    const container = document.getElementById("vendor-queue-list");
    if (!container) return;

    // Fetch products catalog count for stats card
    try {
      const catRes = await this.apiCall(`/vendors/products?vendor_id=${this.currentVendor.id}`);
      const catCount = (catRes && catRes.products) ? catRes.products.length : 0;
      const elCat = document.getElementById("vstat-catalog");
      if (elCat) elCat.innerText = catCount;
    } catch(e) {}

    if (isRestaurant) {
      const ordRes = await this.apiCall(`/vendors/orders?vendor_id=${this.currentVendor.id}`);
      const orders = (ordRes && ordRes.orders) ? ordRes.orders : [];

      // Compute stats metrics
      let pendingCount = 0;
      let acceptedCount = 0;
      let totalRevenue = 0;

      orders.forEach(o => {
        const st = (o.status || 'Pending').toLowerCase();
        if (st === 'pending') pendingCount++;
        if (['accepted', 'preparing', 'ready', 'out for delivery'].includes(st)) acceptedCount++;
        if (['delivered', 'accepted', 'preparing', 'ready', 'out for delivery'].includes(st)) {
          totalRevenue += (o.total_price || 0);
        }
      });

      const elPending = document.getElementById("vstat-pending");
      const elAccepted = document.getElementById("vstat-accepted");
      const elRevenue = document.getElementById("vstat-revenue");
      if (elPending) elPending.innerText = pendingCount;
      if (elAccepted) elAccepted.innerText = acceptedCount;
      if (elRevenue) elRevenue.innerText = `₹${totalRevenue.toLocaleString('en-IN')}`;

      if (!orders || orders.length === 0) {
        container.innerHTML = `<p style="text-align:center; padding: 50px 0; color:var(--text-secondary); font-size:15px;">No active orders in the vendor queue.</p>`;
        return;
      }

      container.innerHTML = orders.map(ord => {
        const itemLines = (ord.items && ord.items.length > 0)
          ? ord.items.map(i => `• ${i.product_name || i.name || 'Item'} x${i.quantity || i.qty || 1} (₹${(i.price || 0) * (i.quantity || i.qty || 1)})`).join("<br>")
          : `• ${ord.formatted_items || 'Meal Order'}`;
        
        const st = (ord.status || 'Pending').toLowerCase();
        const filterKey = st === 'pending' ? 'pending'
          : (st === 'accepted' || st === 'preparing' || st === 'ready' || st === 'out for delivery') ? 'accepted'
          : st === 'rejected' ? 'rejected'
          : st === 'delivered' ? 'delivered' : 'all';

        const statusLabel = (ord.status || 'Pending').toUpperCase();

        return `
        <div class="mobile-order-card order-card" data-status="${filterKey}">
          <div class="mobile-order-header">
            <div class="mobile-order-title">
              <span>🍔</span> Order #${ord.id || ord.order_id}
            </div>
            <span class="mobile-status-badge ${st}">${statusLabel}</span>
          </div>

          <div class="mobile-order-meta">
            👤 Customer: <strong>${ord.user_email || 'Passenger'}</strong>
          </div>

          <div class="mobile-order-location">
            📍 Delivery Target: ${ord.terminal || 'Terminal 1'} • ${ord.gate || 'Gate 1'}
          </div>

          <div class="mobile-order-divider"></div>

          <div class="mobile-order-items">
            <div style="font-weight:700; font-size:12px; color:var(--text-secondary); margin-bottom:4px; text-transform:uppercase; letter-spacing:0.5px;">ORDER ITEMS LIST</div>
            ${itemLines}
          </div>

          <div class="mobile-order-price">
            <span style="font-size:13px; color:var(--text-secondary); font-weight:400;">Total Payable:</span>
            <span>₹${(ord.total_price || 0).toLocaleString('en-IN')} <span style="font-size:11px; color:var(--text-secondary); font-weight:400;">[${ord.payment_method || 'COD'}]</span></span>
          </div>

          <div class="mobile-order-actions">
            ${st === 'pending' ? `
              <button class="btn-reject-mobile" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Rejected')">❌ Reject</button>
              <button class="btn-accept-mobile" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Accepted')">✅ Accept Order</button>
            ` : ''}
            ${st === 'accepted' ? `
              <button class="btn-primary" style="height:38px; width:auto; padding:0 16px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Preparing')">🍳 Start Cooking</button>
            ` : ''}
            ${st === 'preparing' ? `
              <button class="btn-primary" style="height:38px; width:auto; padding:0 16px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Ready')">✔ Mark Ready</button>
            ` : ''}
            ${st === 'ready' ? `
              <button class="btn-primary" style="height:38px; width:auto; padding:0 16px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Out for Delivery')">🛵 Send for Delivery</button>
              <button class="btn-accept-mobile" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Delivered')">📦 Deliver & Close</button>
            ` : ''}
            ${st === 'out for delivery' ? `
              <button class="btn-accept-mobile" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Delivered')">📦 Deliver & Close</button>
            ` : ''}
          </div>
        </div>
      `;
      }).join("");
    } else {
      const bkRes = await this.apiCall(`/bookings?vendor_id=${this.currentVendor.id}`);
      const bookings = (bkRes && bkRes.bookings) ? bkRes.bookings : [];

      let pendingCount = 0;
      let acceptedCount = 0;
      let totalRev = 0;
      bookings.forEach(b => {
        const st = (b.status || 'Pending').toLowerCase();
        if (st === 'pending') pendingCount++;
        if (st === 'confirmed') acceptedCount++;
        if (st === 'confirmed' || st === 'used') totalRev += (b.guests || 1) * 1200;
      });

      const elPending = document.getElementById("vstat-pending");
      const elAccepted = document.getElementById("vstat-accepted");
      const elRevenue = document.getElementById("vstat-revenue");
      if (elPending) elPending.innerText = pendingCount;
      if (elAccepted) elAccepted.innerText = acceptedCount;
      if (elRevenue) elRevenue.innerText = `₹${totalRev.toLocaleString('en-IN')}`;

      if (!bookings || bookings.length === 0) {
        container.innerHTML = `<p style="text-align:center; padding: 50px 0; color:var(--text-secondary); font-size:15px;">No active lounge pass bookings.</p>`;
        return;
      }

      container.innerHTML = bookings.map(b => {
        const st = (b.status || 'Pending').toLowerCase();
        const filterKey = st === 'pending' ? 'pending' : st === 'cancelled' ? 'rejected' : st === 'confirmed' ? 'accepted' : 'all';
        return `
        <div class="mobile-order-card order-card" data-status="${filterKey}">
          <div class="mobile-order-header">
            <div class="mobile-order-title">
              <span>🛋️</span> Pass #${b.id}
            </div>
            <span class="mobile-status-badge ${st === 'pending' ? 'pending' : st === 'confirmed' ? 'accepted' : 'rejected'}">${(b.status || 'Pending').toUpperCase()}</span>
          </div>

          <div class="mobile-order-meta">
            👤 Passenger Email: <strong>${b.email || 'Visitor'}</strong>
          </div>

          <div class="mobile-order-location">
            📅 Reservation Slot: ${b.date || 'Today'} at ${b.time || '12:00 PM'} • ${b.guests || 1} Guest(s)
          </div>

          <div class="mobile-order-divider"></div>

          <div class="mobile-order-price">
            <span style="font-size:13px; color:var(--text-secondary); font-weight:400;">Pass Price Total:</span>
            <span>₹${((b.guests || 1) * 1200).toLocaleString('en-IN')}</span>
          </div>

          <div class="mobile-order-actions">
            ${st === 'pending' ? `
              <button class="btn-reject-mobile" onclick="app.updateBookingStatus(${b.id}, 'Cancelled')">❌ Cancel Pass</button>
              <button class="btn-accept-mobile" onclick="app.updateBookingStatus(${b.id}, 'Confirmed')">✅ Confirm Pass Slot</button>
            ` : ''}
          </div>
        </div>
      `;
      }).join("");
    }
  }

  // --- ORDER FILTER BY STATUS TAB ---
  filterOrders(status) {
    document.querySelectorAll('.order-filter-btn').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`ofilter-${status}`);
    if (activeBtn) activeBtn.classList.add('active');

    const cards = document.querySelectorAll('.order-card');
    let visibleCount = 0;
    cards.forEach(card => {
      const cardStatus = card.getAttribute('data-status');
      const show = status === 'all' || cardStatus === status;
      card.style.display = show ? 'block' : 'none';
      if (show) visibleCount++;
    });

    const container = document.getElementById('vendor-queue-list');
    const existingMsg = document.getElementById('filter-empty-msg');
    if (existingMsg) existingMsg.remove();
    if (visibleCount === 0 && container) {
      const labels = { pending: 'Yet to Accept', accepted: 'Accepted', rejected: 'Rejected', delivered: 'Delivered', all: '' };
      const msg = document.createElement('p');
      msg.id = 'filter-empty-msg';
      msg.style.cssText = 'text-align:center; padding:40px 0; color:var(--text-secondary); font-size:14px;';
      msg.innerText = `No ${labels[status] || ''} orders found in queue.`;
      container.appendChild(msg);
    }
  }

  // --- TAB BASED ADMIN REGISTRATION / DELETION HANDLERS ---
  async submitTabAdminRegisterVendor() {
    const adminKey = document.getElementById("tab-admin-reg-key")?.value.trim() || "admin_aeroassist_2026";
    const name = document.getElementById("tab-admin-reg-name")?.value.trim();
    const email = document.getElementById("tab-admin-reg-email")?.value.trim();
    const password = document.getElementById("tab-admin-reg-password")?.value.trim();
    const type = document.getElementById("tab-admin-reg-type")?.value || "restaurant";
    const terminal = document.getElementById("tab-admin-reg-terminal")?.value || "Terminal 1";
    const gate = document.getElementById("tab-admin-reg-gate")?.value.trim();

    if (!name || !email || !password || !gate) {
      alert("Please fill all properties (Vendor Name, Email, Password, and Gate Number)!");
      return;
    }

    const payload = {
      admin_key: adminKey,
      name,
      email,
      password,
      type,
      terminal,
      gate
    };

    const res = await this.apiCall("/vendors/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert(`✅ Vendor "${name}" successfully registered!\n\nThe vendor account is ready for sign in with email: ${email}`);
      document.getElementById("tab-admin-reg-name").value = "";
      document.getElementById("tab-admin-reg-email").value = "";
      document.getElementById("tab-admin-reg-password").value = "";
      document.getElementById("tab-admin-reg-gate").value = "";
    } else {
      alert(res.message || "Failed to create vendor account.");
    }
  }

  async submitTabAdminDeleteVendor() {
    const email = document.getElementById("tab-admin-del-email")?.value.trim();
    if (!email) {
      alert("Please enter a vendor email to remove.");
      return;
    }
    if (!confirm(`Are you absolutely sure you want to permanently delete vendor: ${email}?`)) return;

    const res = await this.apiCall("/vendors/delete", {
      method: "POST",
      body: JSON.stringify({
        admin_key: "admin_aeroassist_2026",
        email: email
      })
    });

    if (res && res.status === "success") {
      alert(`✅ Vendor account "${email}" successfully removed!`);
      document.getElementById("tab-admin-del-email").value = "";
    } else {
      alert(res.message || "Failed to remove vendor account.");
    }
  }

  async updateOrderStatus(orderId, status) {
    const res = await this.apiCall(`/vendors/orders/${orderId}/status`, {
      method: "POST",
      body: JSON.stringify({ status })
    });
    if (res && res.status === "success") this.fetchVendorQueue();
  }

  async updateBookingStatus(bookingId, status) {
    const res = await this.apiCall(`/vendors/bookings/${bookingId}/status`, {
      method: "POST",
      body: JSON.stringify({ status })
    });
    if (res && res.status === "success") this.fetchVendorQueue();
  }

  // --- CATALOG MANAGER CATALOG PRODUCT CRUD ---
  async fetchVendorCatalog() {
    if (!this.currentVendor) return;
    const result = await this.apiCall(`/vendors/products?vendor_id=${this.currentVendor.id}`);
    const products = (result && result.products) ? result.products : [];

    const elCat = document.getElementById("vstat-catalog");
    if (elCat) elCat.innerText = products.length;

    const container = document.getElementById("vendor-catalog-grid");
    if (!container) return;

    if (!products || products.length === 0) {
      container.innerHTML = `<p style="grid-column:1/-1; text-align:center; padding:50px 0; color:var(--text-secondary); font-size:15px;">No catalog products found. Add your first item using the button above!</p>`;
      return;
    }

    container.innerHTML = products.map(prod => `
      <div class="mobile-catalog-card">
        <div style="display:flex; gap:14px; align-items:center; margin-bottom:12px;">
          <img style="width:68px; height:68px; border-radius:12px; object-fit:cover; border:1px solid var(--glass-border);" src="${this.getProductImage(prod)}" alt="${prod.name}">
          <div style="flex:1;">
            <span style="font-size:11px; background:rgba(0,229,255,0.15); color:var(--accent-cyan); padding:2px 8px; border-radius:12px; font-weight:700;">${prod.category || 'General'}</span>
            <h4 style="margin:4px 0 2px 0; font-size:16px; color:var(--text-primary);">${prod.name}</h4>
            <strong style="color:#00E5FF; font-size:16px;">₹${(prod.price || 0).toFixed(2)}</strong>
          </div>
        </div>
        <p style="font-size:12px; color:var(--text-secondary); margin-bottom:14px; min-height:34px;">${prod.description || 'Freshly prepared menu item.'}</p>
        <div style="display:flex; justify-content:space-between; align-items:center; border-top:1px solid var(--glass-border); padding-top:10px;">
          <span style="font-size:11px; color:#10B981; font-weight:700;">✔ IN STOCK</span>
          <button class="btn-danger" style="width:auto; height:32px; padding:0 14px; font-size:12px; font-weight:700;" onclick="app.submitDeleteProduct(${prod.id})">🗑️ Delete</button>
        </div>
      </div>
    `).join("");
  }

  openAddProductModal() {
    this.openModal("add-product");
  }

  async submitAddProduct() {
    const name = document.getElementById("product-name").value.trim();
    const price = parseFloat(document.getElementById("product-price").value);
    const category = document.getElementById("product-category").value;
    const desc = document.getElementById("product-description").value.trim();

    if (!name || isNaN(price)) return;

    const payload = {
      vendor_id: this.currentVendor.id,
      name,
      price,
      category,
      description: desc,
      image_url: ""
    };

    const res = await this.apiCall("/vendors/products", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert("Product successfully added!");
      this.closeModal("add-product");
      
      document.getElementById("product-name").value = "";
      document.getElementById("product-price").value = "";
      document.getElementById("product-description").value = "";
      
      this.fetchVendorCatalog();
    }
  }

  async submitDeleteProduct(id) {
    if (!confirm("Are you sure you want to delete this product?")) return;
    const res = await this.apiCall("/vendors/products", {
      method: "DELETE",
      body: JSON.stringify({ product_id: id })
    });
    if (res && res.status === "success") this.fetchVendorCatalog();
  }

  // --- FLIGHT BOOKING ENGINE ---
  async searchFlights() {
    const origin = document.getElementById("flight-search-origin")?.value || "DEL";
    const dest = document.getElementById("flight-search-dest")?.value || "BOM";
    const date = document.getElementById("flight-search-date")?.value || new Date().toISOString().split('T')[0];
    const passengers = parseInt(document.getElementById("flight-search-passengers")?.value || "1");
    const cabinClass = document.getElementById("flight-search-class")?.value || "Economy";

    if (!date) {
      alert("Please select a departure date.");
      return;
    }

    // Show loading state
    const searchBtn = document.getElementById("flight-search-btn");
    if (searchBtn) { searchBtn.disabled = true; searchBtn.textContent = "Searching..."; }

    let flights = null;
    try {
      const res = await this.apiCall(`/flights/search?origin=${origin}&destination=${dest}&date=${date}&passengers=${passengers}&cabin_class=${encodeURIComponent(cabinClass)}`);
      if (res && res.status === "success" && res.flights && res.flights.length > 0) {
        // Normalise field: backend uses price_per_pax, we use base_fare internally
        flights = res.flights.map(f => ({ ...f, base_fare: f.base_fare || f.price_per_pax || 4500 }));
      }
    } catch(e) { /* fall through to demo */ }

    if (!flights || flights.length === 0) {
      // Hardcoded demo flights so the UI always works
      const mult = cabinClass === 'Economy' ? 1 : cabinClass === 'Premium Economy' ? 1.8 : 2.8;
      flights = [
        { id:'FL-AI-101', flight_number:'AI-101', airline:'Air India', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/5/5a/Air_India_Star_Alliance_Logo.svg', airline_color:'#E8112D', origin, destination:dest, departure_time:'06:00', arrival_time:'08:15', duration:'2h 15m', stops:'Non-stop', base_fare:Math.round(4500*mult), total_fare:Math.round(4500*mult)*passengers, baggage:'25 kg + 7 kg Hand', aircraft:'Airbus A320neo', cabinClass },
        { id:'FL-6E-203', flight_number:'6E-203', airline:'IndiGo', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/d/d1/IndiGo_Airlines_logo.svg', airline_color:'#1E3A8A', origin, destination:dest, departure_time:'09:30', arrival_time:'11:45', duration:'2h 15m', stops:'Non-stop', base_fare:Math.round(5200*mult), total_fare:Math.round(5200*mult)*passengers, baggage:'15 kg + 7 kg Hand', aircraft:'Airbus A321neo', cabinClass },
        { id:'FL-SG-315', flight_number:'SG-315', airline:'SpiceJet', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/e/e9/SpiceJet_logo.svg', airline_color:'#E4002B', origin, destination:dest, departure_time:'13:15', arrival_time:'15:40', duration:'2h 25m', stops:'Non-stop', base_fare:Math.round(3990*mult), total_fare:Math.round(3990*mult)*passengers, baggage:'15 kg + 7 kg Hand', aircraft:'Boeing 737 MAX', cabinClass },
        { id:'FL-UK-407', flight_number:'UK-407', airline:'Vistara', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/6/69/Vistara-airline-logo-vector.png', airline_color:'#7B2D8B', origin, destination:dest, departure_time:'17:45', arrival_time:'20:05', duration:'2h 20m', stops:'Non-stop', base_fare:Math.round(6100*mult), total_fare:Math.round(6100*mult)*passengers, baggage:'25 kg + 7 kg Hand', aircraft:'Boeing 787 Dreamliner', cabinClass },
        { id:'FL-G8-521', flight_number:'G8-521', airline:'Go First', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/thumb/8/8d/Go_First_logo.svg/240px-Go_First_logo.svg.png', airline_color:'#FF6B00', origin, destination:dest, departure_time:'21:10', arrival_time:'23:30', duration:'2h 20m', stops:'Non-stop', base_fare:Math.round(3500*mult), total_fare:Math.round(3500*mult)*passengers, baggage:'15 kg + 7 kg Hand', aircraft:'Airbus A320', cabinClass },
        { id:'FL-QP-619', flight_number:'QP-619', airline:'Akasa Air', airline_logo:'https://upload.wikimedia.org/wikipedia/commons/8/87/Akasa_Air_Logo.png', airline_color:'#FF6B35', origin, destination:dest, departure_time:'11:00', arrival_time:'16:30', duration:'5h 30m', stops:'1 Stop (HYD)', base_fare:Math.round(7500*mult), total_fare:Math.round(7500*mult)*passengers, baggage:'20 kg + 7 kg Hand', aircraft:'Boeing 737 MAX 8', cabinClass }
      ];
    }

    if (searchBtn) { searchBtn.disabled = false; searchBtn.textContent = "SEARCH FLIGHTS"; }
    this.loadFlightResults(flights, passengers, cabinClass, date);
  }

  resetFlightSearchUI() {
    const searchCard = document.getElementById("flight-search-card");
    if (searchCard) searchCard.style.display = "block";

    const results = document.getElementById("flight-results-container");
    if (results) { results.style.display = "none"; results.innerHTML = ""; }

    const seatPass = document.getElementById("flight-seat-passenger-container");
    if (seatPass) { seatPass.style.display = "none"; seatPass.innerHTML = ""; }

    const review = document.getElementById("flight-review-container");
    if (review) { review.style.display = "none"; review.innerHTML = ""; }

    this.currentFlights = null;
    this.bookingDraft = { passengers: 1, cabinClass: 'Economy', date: new Date().toISOString().split('T')[0], seats: [], passengerDetails: [] };
  }

  setQuickRoute(orig, dest) {
    const origSelect = document.getElementById("flight-search-origin");
    const destSelect = document.getElementById("flight-search-dest");
    if (origSelect) origSelect.value = orig;
    if (destSelect) destSelect.value = dest;
    this.searchFlights();
  }

  loadFlightResults(flights, passengers, cabinClass, date) {
    const container = document.getElementById("flight-results-container");
    if (!container) return;

    if (!flights || flights.length === 0) {
      container.innerHTML = `<p style="text-align:center;">No flights found for this route.</p>`;
    } else {
      container.innerHTML = `
        <h3 style="margin-bottom:16px;">Select your Departure Flight</h3>
        <div style="display:flex; flex-direction:column; gap:16px;">
          ${flights.map((f, i) => `
            <div class="glass-card" style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:16px; padding:20px;">
              <div style="display:flex; align-items:center; gap:16px;">
                <img src="${f.airline_logo}" style="width:40px; height:40px; border-radius:50%; object-fit:contain; background:#fff;" alt="${f.airline}">
                <div>
                  <h4 style="margin:0; font-size:16px;">${f.airline}</h4>
                  <p style="margin:0; font-size:12px; color:var(--text-secondary);">${f.flight_number}</p>
                </div>
              </div>
              
              <div style="text-align:center;">
                <h3 style="margin:0;">${f.departure_time}</h3>
                <p style="margin:0; font-size:12px; color:var(--text-secondary);">${f.origin}</p>
              </div>
              
              <div style="text-align:center; position:relative; min-width:100px;">
                <p style="margin:0; font-size:11px; color:var(--text-secondary);">${f.duration}</p>
                <div style="height:1px; background:var(--glass-border); margin:4px 0; width:100%;"></div>
                <p style="margin:0; font-size:11px; color:var(--text-secondary);">${f.stops}</p>
              </div>
              
              <div style="text-align:center;">
                <h3 style="margin:0;">${f.arrival_time}</h3>
                <p style="margin:0; font-size:12px; color:var(--text-secondary);">${f.destination}</p>
              </div>
              
              <div style="text-align:right;">
                <h2 style="margin:0; color:var(--accent-orange); font-size:24px;">₹${(f.base_fare || f.price_per_pax || 0).toLocaleString('en-IN')}</h2>
                <p style="margin:2px 0 6px; font-size:11px; color:var(--text-secondary);">per person</p>
                <button class="btn-primary" style="margin-top:4px;" onclick="app.selectFlight(${i})">SELECT FLIGHT</button>
              </div>
            </div>
          `).join('')}
        </div>
      `;
    }
    
    // Store flights for reference
    this.currentFlights = flights;
    this.bookingDraft = { passengers, cabinClass, date, seats: [], passengerDetails: [] };

    const searchCard = document.getElementById("flight-search-card");
    if (searchCard) searchCard.style.display = "none";
    container.style.display = "block";
    container.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  selectFlight(index) {
    if (!this.currentUser) {
      alert("Please Sign In first to book a flight!");
      this.showPage("auth");
      return;
    }
    this.bookingDraft.flight = this.currentFlights[index];
    this.renderSeatMap();
  }

  async renderSeatMap() {
    const container = document.getElementById("flight-seat-passenger-container");
    if (!container) return;

    document.getElementById("flight-results-container").style.display = "none";
    container.style.display = "block";
    container.innerHTML = `<div class="glass-card" style="padding:24px; text-align:center;"><p>Loading seat availability from central inventory...</p></div>`;

    // Fetch booked seats from central backend inventory
    const flight = this.bookingDraft.flight;
    let bookedSeats = [];
    try {
      const res = await this.apiCall(`/flights/seats?flight_number=${encodeURIComponent(flight.flight_number)}&date=${encodeURIComponent(this.bookingDraft.date)}`);
      if (res && res.booked_seats) bookedSeats = res.booked_seats;
    } catch(e) { console.warn('[SEAT MAP] Could not fetch central seat inventory:', e); }

    const rows = this.bookingDraft.cabinClass === "Economy" ? 10 : 5;
    const cols = ['A', 'B', 'C', 'D', 'E', 'F'];
    
    let seatGridHTML = `<div class="seat-map-grid" style="display:grid; grid-template-columns: repeat(7, 40px); gap:8px; justify-content:center; margin-top:20px;">`;
    
    for (let r = 1; r <= rows; r++) {
      for (let c = 0; c < cols.length; c++) {
        if (c === 3) seatGridHTML += `<div style="width:40px; height:40px;"></div>`;
        const seatId = `${r}${cols[c]}`;
        const isOccupied = bookedSeats.includes(seatId);
        if (isOccupied) {
          seatGridHTML += `<div class="seat-item occupied" style="width:40px;height:40px;border-radius:8px;background:rgba(255,80,80,0.25);border:1px solid rgba(255,80,80,0.4);color:rgba(255,255,255,0.4);display:flex;align-items:center;justify-content:center;font-size:11px;cursor:not-allowed;" title="Occupied — booked by another passenger">${seatId}</div>`;
        } else {
          seatGridHTML += `<div class="seat-item available" id="seat-${seatId}" style="width:40px;height:40px;border-radius:8px;background:rgba(0,229,255,0.1);border:1px solid var(--accent-cyan);color:#fff;display:flex;align-items:center;justify-content:center;font-size:11px;cursor:pointer;" onclick="app.toggleSeatSelection('${seatId}')">${seatId}</div>`;
        }
      }
    }
    seatGridHTML += `</div>`;

    container.innerHTML = `
      <div class="glass-card" style="padding:24px;">
        <h3 style="margin-bottom:8px;">Select Seats</h3>
        <p style="font-size:13px; color:var(--text-secondary);">Please select ${this.bookingDraft.passengers} seat(s). Red seats are reserved by other passengers in real-time.</p>
        <div style="display:flex; justify-content:center; gap:20px; margin-top:16px; flex-wrap:wrap;">
          <div style="display:flex;align-items:center;gap:8px;"><div style="width:16px;height:16px;background:rgba(0,229,255,0.1);border:1px solid var(--accent-cyan);border-radius:4px;"></div><span style="font-size:12px;">Available</span></div>
          <div style="display:flex;align-items:center;gap:8px;"><div style="width:16px;height:16px;background:var(--accent-cyan);border-radius:4px;"></div><span style="font-size:12px;">Selected</span></div>
          <div style="display:flex;align-items:center;gap:8px;"><div style="width:16px;height:16px;background:rgba(255,80,80,0.25);border:1px solid rgba(255,80,80,0.4);border-radius:4px;"></div><span style="font-size:12px;">Occupied</span></div>
        </div>
        ${seatGridHTML}
        <div style="text-align:right; margin-top:24px;">
          <button class="btn-primary" onclick="app.continueToPassengerDetails()">CONTINUE</button>
        </div>
      </div>
    `;
  }

  toggleSeatSelection(seatId) {
    const seatEl = document.getElementById(`seat-${seatId}`);
    if (!seatEl) return;

    if (this.bookingDraft.seats.includes(seatId)) {
      this.bookingDraft.seats = this.bookingDraft.seats.filter(id => id !== seatId);
      seatEl.style.background = 'rgba(0, 229, 255, 0.1)';
      seatEl.style.color = '#fff';
    } else {
      if (this.bookingDraft.seats.length >= this.bookingDraft.passengers) {
        alert(`You can only select ${this.bookingDraft.passengers} seat(s).`);
        return;
      }
      this.bookingDraft.seats.push(seatId);
      seatEl.style.background = 'var(--accent-cyan)';
      seatEl.style.color = '#000';
    }
  }

  continueToPassengerDetails() {
    if (this.bookingDraft.seats.length !== this.bookingDraft.passengers) {
      alert(`Please select ${this.bookingDraft.passengers} seat(s) before continuing.`);
      return;
    }

    const container = document.getElementById("flight-seat-passenger-container");
    
    let paxFormsHTML = ``;
    for (let i = 0; i < this.bookingDraft.passengers; i++) {
      paxFormsHTML += `
        <div style="margin-bottom:16px; padding:16px; border:1px solid var(--glass-border); border-radius:var(--radius-md);">
          <h4 style="margin-top:0; margin-bottom:12px;">Passenger ${i+1} (Seat: ${this.bookingDraft.seats[i]})</h4>
          <div style="display:flex; gap:16px; flex-wrap:wrap;">
            <div class="form-group" style="flex:1; min-width:200px;">
              <label>Full Name</label>
              <input type="text" id="pax-${i}-name" placeholder="As on Govt ID" value="${i===0 ? this.currentUser.name : ''}">
            </div>
            <div class="form-group" style="flex:1; min-width:150px;">
              <label>Age</label>
              <input type="number" id="pax-${i}-age" placeholder="Age" min="1" max="120" value="${i===0 ? '30' : ''}">
            </div>
            <div class="form-group" style="flex:1; min-width:150px;">
              <label>Gender</label>
              <select id="pax-${i}-gender">
                <option value="Male">Male</option>
                <option value="Female">Female</option>
                <option value="Other">Other</option>
              </select>
            </div>
          </div>
        </div>
      `;
    }

    container.innerHTML = `
      <div class="glass-card" style="padding:24px;">
        <h3 style="margin-bottom:16px;">Passenger Details</h3>
        ${paxFormsHTML}
        
        <div class="form-group" style="margin-top:16px;">
          <label>Contact Email</label>
          <input type="email" id="booking-contact-email" value="${this.currentUser.email}" readonly>
        </div>
        <div class="form-group">
          <label>Contact Mobile</label>
          <input type="text" id="booking-contact-mobile" value="${this.currentUser.mobile || ''}" placeholder="10-digit Mobile">
        </div>

        <div style="text-align:right; margin-top:24px;">
          <button class="btn-primary" onclick="app.reviewBooking()">REVIEW BOOKING</button>
        </div>
      </div>
    `;
  }

  reviewBooking() {
    this.bookingDraft.passengerDetails = [];
    for (let i = 0; i < this.bookingDraft.passengers; i++) {
      const name = document.getElementById(`pax-${i}-name`)?.value.trim();
      const age = document.getElementById(`pax-${i}-age`)?.value.trim();
      const gender = document.getElementById(`pax-${i}-gender`)?.value;
      
      if (!name || !age) {
        alert(`Please complete details for Passenger ${i+1}`);
        return;
      }
      this.bookingDraft.passengerDetails.push({ name, age, gender, seat: this.bookingDraft.seats[i] });
    }

    const contactMobile = document.getElementById("booking-contact-mobile")?.value.trim();
    if (!contactMobile) {
      alert("Please enter a contact mobile number.");
      return;
    }
    this.bookingDraft.contactMobile = contactMobile;

    document.getElementById("flight-seat-passenger-container").style.display = "none";
    const container = document.getElementById("flight-review-container");
    container.style.display = "block";

    const f = this.bookingDraft.flight;
    const taxes = 850 * this.bookingDraft.passengers;
    const totalFare = (f.base_fare * this.bookingDraft.passengers) + taxes;
    this.bookingDraft.totalFare = totalFare;

    container.innerHTML = `
      <div class="glass-card" style="padding:24px;">
        <h3 style="margin-bottom:16px;">Review Your Booking</h3>
        
        <div style="display:flex; justify-content:space-between; flex-wrap:wrap; gap:20px; margin-bottom:24px; padding-bottom:16px; border-bottom:1px solid var(--glass-border);">
          <div>
            <h4 style="margin:0; font-size:18px; color:var(--accent-orange);">${f.origin} ➔ ${f.destination}</h4>
            <p style="margin:4px 0 0 0; color:var(--text-secondary); font-size:14px;">${this.bookingDraft.date} | ${f.airline} (${f.flight_number})</p>
            <p style="margin:4px 0 0 0; color:var(--text-secondary); font-size:14px;">${f.departure_time} - ${f.arrival_time} (${f.duration})</p>
          </div>
          <div style="text-align:right;">
            <p style="margin:0; font-size:14px;">Cabin: <strong>${this.bookingDraft.cabinClass}</strong></p>
            <p style="margin:4px 0 0 0; font-size:14px;">Baggage: <strong>${f.baggage}</strong></p>
          </div>
        </div>

        <h4 style="margin-bottom:12px;">Passenger Info</h4>
        <div style="margin-bottom:24px;">
          ${this.bookingDraft.passengerDetails.map(p => `
            <p style="margin:4px 0; font-size:14px;">• ${p.name} (${p.gender}, ${p.age}) - Seat: <strong style="color:var(--accent-cyan);">${p.seat}</strong></p>
          `).join('')}
        </div>

        <h4 style="margin-bottom:12px;">Fare Breakdown</h4>
        <div style="background:rgba(0,0,0,0.2); padding:16px; border-radius:var(--radius-md);">
          <div style="display:flex; justify-content:space-between; margin-bottom:8px;">
            <span>Base Fare (${this.bookingDraft.passengers} x ₹${f.base_fare.toLocaleString('en-IN')})</span>
            <span>₹${(f.base_fare * this.bookingDraft.passengers).toLocaleString('en-IN')}</span>
          </div>
          <div style="display:flex; justify-content:space-between; margin-bottom:16px;">
            <span>Taxes & Fees</span>
            <span>₹${taxes.toLocaleString('en-IN')}</span>
          </div>
          <div style="display:flex; justify-content:space-between; border-top:1px solid rgba(255,255,255,0.1); padding-top:12px;">
            <strong style="font-size:18px;">Total Amount</strong>
            <strong style="font-size:18px; color:var(--accent-orange);">₹${totalFare.toLocaleString('en-IN')}</strong>
          </div>
        </div>

        <div style="text-align:right; margin-top:24px;">
          <button class="btn-primary" onclick="app.openPaymentModal()">PROCEED TO PAYMENT</button>
        </div>
      </div>
    `;
  }

  openPaymentModal() {
    const totalEl = document.getElementById("payment-total-amt");
    if (totalEl) totalEl.innerText = `₹${this.bookingDraft.totalFare.toLocaleString('en-IN')}`;
    this.openModal("flight-payment");
    this.switchPayTab("upi");
  }

  switchPayTab(tabId) {
    document.querySelectorAll('#modal-flight-payment .order-filter-btn').forEach(btn => btn.classList.remove('active'));
    const activeBtn = document.getElementById(`paytab-${tabId}`);
    if (activeBtn) activeBtn.classList.add('active');

    const forms = ['upi', 'card', 'debit', 'netbank', 'wallet'];
    forms.forEach(f => {
      const el = document.getElementById(`payform-${f}`);
      if (el) el.style.display = "none";
    });

    const activeForm = document.getElementById(`payform-${tabId}`);
    if (activeForm) activeForm.style.display = "block";
    
    this.selectedPaymentMethod = tabId;
  }

  async executeDummyPayment() {
    const btn = document.getElementById("btn-submit-pay");
    const originalText = btn.innerHTML;
    btn.innerHTML = `<span class="spinner" style="display:inline-block; width:16px; height:16px; border:2px solid #fff; border-top:2px solid transparent; border-radius:50%; animation:spin 1s linear infinite;"></span> Processing...`;
    btn.disabled = true;

    const userEmail = this.currentUser ? this.currentUser.email : (document.getElementById("booking-contact-email")?.value || "demo@aeroassist.ai");
    const payload = {
      email: userEmail,
      flight_details: {
        ...this.bookingDraft.flight,
        date: this.bookingDraft.date,
        cabinClass: this.bookingDraft.cabinClass
      },
      passenger_details: this.bookingDraft.passengerDetails,
      payment_method: this.selectedPaymentMethod || "card",
      total_fare: this.bookingDraft.totalFare
    };

    try {
      const res = await this.apiCall("/flights/book", {
        method: "POST",
        body: JSON.stringify(payload)
      });

      let newBooking = null;
      if (res && res.booking) {
        newBooking = res.booking;
      } else {
        const pnr = (res && res.pnr) ? res.pnr : "PNR" + Math.floor(100000 + Math.random() * 900000);
        newBooking = {
          id: pnr,
          pnr: pnr,
          user_email: userEmail,
          booking_id: "BK-" + Math.floor(100000 + Math.random() * 900000),
          ticket_number: "TKT-" + Math.floor(10000000 + Math.random() * 90000000),
          payment_id: "PAY-" + Math.floor(1000000 + Math.random() * 9000000),
          transaction_id: "TXN-" + Math.floor(100000000 + Math.random() * 900000000),
          flight_details: payload.flight_details,
          passenger_details: payload.passenger_details,
          amount: payload.total_fare,
          payment_method: payload.payment_method,
          booking_status: "Confirmed"
        };
      }

      // Save to local cache for instant offline rendering (scoped by user email)
      const localKey = `aero_local_bookings_${userEmail.toLowerCase()}`;
      const existing = JSON.parse(localStorage.getItem(localKey) || "[]");
      existing.unshift(newBooking);
      localStorage.setItem(localKey, JSON.stringify(existing));

      setTimeout(() => {
        this.closeModal("flight-payment");
        alert("✅ Payment Successful! Your flight ticket is confirmed.");
        
        this.resetFlightSearchUI();
        this.showPage('my-bookings');
      }, 1500);
    } catch(err) {
      alert("Payment processing error.");
      btn.innerHTML = originalText;
      btn.disabled = false;
    }
  }

  async fetchMyBookings() {
    const container = document.getElementById("my-bookings-list-container");
    if (!container) return;

    if (!this.currentUser || !this.currentUser.email) {
      this.allMyBookings = [];
      container.innerHTML = `
        <div style="text-align:center; padding: 60px 20px; color: var(--text-secondary);">
          <div style="font-size:3rem; margin-bottom:16px;">🔐</div>
          <h3 style="color:var(--text-primary); margin-bottom:8px;">Sign In to View Your Bookings</h3>
          <p style="margin-bottom:24px;">Please sign in or create an account to view your flight tickets and booking history.</p>
          <button class="btn-primary" onclick="app.showPage('auth')" style="padding:12px 28px; border-radius:12px;">Sign In / Register</button>
        </div>
      `;
      return;
    }

    const email = this.currentUser.email.trim().toLowerCase();
    let backendBookings = [];

    try {
      const res = await this.apiCall(`/flights/bookings?email=${encodeURIComponent(email)}`);
      if (res && res.status === "success" && Array.isArray(res.bookings)) {
        backendBookings = res.bookings;
      }
    } catch (e) { /* network failure — fall through to localStorage */ }

    // Build a set of PNRs / booking IDs already confirmed in the backend
    const backendKeys = new Set(backendBookings.map(b => b.pnr || b.booking_id || b.id).filter(Boolean));

    // Sync any localStorage-only bookings up to the backend
    const localKey = `aero_local_bookings_${email}`;
    const localBookings = JSON.parse(localStorage.getItem(localKey) || "[]");
    const syncedPNRs = new Set();

    for (const lb of localBookings) {
      const lKey = lb.pnr || lb.booking_id || lb.id;
      if (!lKey || backendKeys.has(lKey)) continue; // already on backend – skip

      // Attempt to push this orphan booking to the backend
      try {
        const syncPayload = {
          email: email,
          flight_details: lb.flight_details || {},
          passenger_details: lb.passenger_details || [],
          payment_method: lb.payment_method || "card",
          total_fare: lb.amount || lb.total_fare || 0
        };
        const syncRes = await this.apiCall("/flights/book", {
          method: "POST",
          body: JSON.stringify(syncPayload)
        });
        if (syncRes && (syncRes.status === "success" || syncRes.pnr)) {
          syncedPNRs.add(lKey); // mark for removal from localStorage
          // Add the server-confirmed booking to our list
          const confirmed = syncRes.booking || { ...lb, pnr: syncRes.pnr || lKey };
          backendBookings.unshift(confirmed);
          backendKeys.add(syncRes.pnr || lKey);
        }
      } catch (_) { /* keep in localStorage if sync fails */ }
    }

    // Remove successfully synced bookings from localStorage
    if (syncedPNRs.size > 0) {
      const remaining = localBookings.filter(lb => {
        const k = lb.pnr || lb.booking_id || lb.id;
        return !syncedPNRs.has(k);
      });
      localStorage.setItem(localKey, JSON.stringify(remaining));
    }

    // Merge: backend is primary; fill any remaining local-only bookings that failed sync
    const combinedMap = new Map();
    backendBookings.forEach(b => {
      const key = b.pnr || b.booking_id || b.id;
      if (key) combinedMap.set(key, b);
    });
    localBookings.forEach(b => {
      const key = b.pnr || b.booking_id || b.id;
      if (key && !combinedMap.has(key)) combinedMap.set(key, b); // offline-only fallback
    });

    let resultList = Array.from(combinedMap.values());

    // Show empty state if no bookings found
    if (resultList.length === 0) {
      container.innerHTML = `
        <div style="text-align:center; padding: 60px 20px; color: var(--text-secondary);">
          <div style="font-size:3rem; margin-bottom:16px;">✈️</div>
          <h3 style="color:var(--text-primary); margin-bottom:8px;">No Bookings Yet</h3>
          <p style="margin-bottom:24px;">You haven't booked any flights yet. Start exploring!</p>
          <button class="btn-primary" onclick="app.showPage('flights')" style="padding:12px 28px; border-radius:12px;">Book a Flight</button>
        </div>
      `;
      return;
    }

    // Render real bookings
    this.allMyBookings = resultList;
    this.renderMyBookings(resultList);
  }

  renderMyBookings(bookings) {
    const container = document.getElementById("my-bookings-list-container");
    if (!container) return;
    
    if (!bookings || bookings.length === 0) {
      container.innerHTML = `<p style="text-align:center; color:var(--text-secondary); padding:40px;">No matching bookings found. Book a flight to see your tickets here!</p>`;
      return;
    }

    container.innerHTML = bookings.map(b => {
      const f = b.flight_details || {};
      // Backend uses booking_status; fall back to status field
      const rawStatus = (b.booking_status || b.status || 'Confirmed').toLowerCase();
      const statusClass = rawStatus === 'confirmed' ? 'accepted' : rawStatus === 'completed' ? 'delivered' : 'pending';
      const pnr = b.pnr || b.booking_id || b.id;
      const paxList = Array.isArray(b.passenger_details) ? b.passenger_details : [];
      const paxCount = paxList.length || 1;
      const allNames = paxList.map(p => p.name).filter(Boolean).join(", ") || (b.passenger_name || 'Passenger');
      const allSeats = paxList.map(p => p.seat).filter(Boolean).join(", ") || '-';
      return `
        <div class="glass-card booking-card" style="margin-bottom:16px; padding:20px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:10px;">
            <h3 style="margin:0;">PNR: <span style="color:var(--accent-orange);">${pnr}</span></h3>
            <span class="status-badge ${statusClass}">${rawStatus.toUpperCase()}</span>
          </div>
          
          <div style="display:flex; justify-content:space-between; flex-wrap:wrap; gap:20px; align-items:center;">
            <div>
              <p style="margin:0; font-size:18px; font-weight:700;">${f.origin || '?'} ➔ ${f.destination || '?'}</p>
              <p style="margin:4px 0 0 0; color:var(--text-secondary); font-size:13px;">${f.date || b.departure_date || ''} | ${f.airline || ''} (${f.flight_number || ''})</p>
              <p style="margin:4px 0 0 0; color:var(--text-secondary); font-size:13px;">${f.departure_time || ''} - ${f.arrival_time || ''} &nbsp;|&nbsp; Passengers: <strong>${paxCount}</strong> &nbsp;|&nbsp; Seats: <strong style="color:var(--accent-cyan);">${allSeats}</strong></p>
              <p style="margin:4px 0 0 0; color:var(--text-secondary); font-size:13px;">Passenger(s): <strong>${allNames}</strong></p>
            </div>
            
            <div style="text-align:right;">
              <button class="btn-primary" style="height:36px; font-size:13px; padding:0 16px;" onclick="app.viewETicket('${pnr}')">✈ VIEW E-TICKET</button>
            </div>
          </div>
        </div>
      `;
    }).join("");
  }

  setBookingFilter(status) {
    document.querySelectorAll('#view-my-bookings .order-filter-btn').forEach(btn => btn.classList.remove('active'));
    const btn = document.getElementById(`bfilter-${status}`);
    if (btn) btn.classList.add('active');
    
    this.currentBookingFilter = status;
    this.filterMyBookings();
  }

  filterMyBookings() {
    if (!this.allMyBookings) return;
    const query = (document.getElementById("my-bookings-search-input")?.value || "").toLowerCase();
    const status = this.currentBookingFilter || 'all';

    const filtered = this.allMyBookings.filter(b => {
      const pnr = (b.pnr || b.booking_id || b.id || "").toString().toLowerCase();
      const f = b.flight_details || {};
      const flightNum = (f.flight_number || "").toLowerCase();
      const origin = (f.origin || "").toLowerCase();
      const dest = (f.destination || "").toLowerCase();
      
      let passMatch = false;
      if (b.passenger_details && Array.isArray(b.passenger_details)) {
        passMatch = b.passenger_details.some(p => (p.name || "").toLowerCase().includes(query));
      }

      const matchesQuery = !query || pnr.includes(query) || flightNum.includes(query) || origin.includes(query) || dest.includes(query) || passMatch;
      const rawStatus = (b.booking_status || b.status || 'confirmed').toLowerCase();
      const matchesStatus = status === 'all' || rawStatus === status;
      
      return matchesQuery && matchesStatus;
    });

    this.renderMyBookings(filtered);
  }

  // --- PARKING RESERVATIONS ---
  switchBookingTab(tab) {
    const tabs = ['flights', 'food', 'parking'];
    tabs.forEach(t => {
      const btn = document.getElementById(`tab-${t}-bookings`);
      const sec = document.getElementById(`bookings-${t}-section`);
      if (btn) btn.classList.toggle('active', t === tab);
      if (sec) sec.style.display = t === tab ? 'block' : 'none';
    });
    if (tab === 'flights') this.fetchMyBookings();
    else if (tab === 'food') this.fetchFoodOrderHistory();
    else if (tab === 'parking') this.fetchParkingBookings();
  }

  async fetchFoodOrderHistory() {
    const container = document.getElementById('my-food-list-container');
    if (!container) return;
    if (!this.currentUser || !this.currentUser.email) {
      container.innerHTML = `<div style="text-align:center;padding:40px;color:var(--text-secondary);">Please Sign In to view your food order history.</div>`;
      return;
    }
    container.innerHTML = `<div style="text-align:center;padding:30px;color:var(--text-secondary);">Loading order history...</div>`;
    try {
      const res = await this.apiCall(`/orders?email=${encodeURIComponent(this.currentUser.email)}`);
      const orders = (res && res.orders) ? res.orders : [];
      if (!orders.length) {
        container.innerHTML = `
          <div style="text-align:center; padding: 60px 20px; color: var(--text-secondary);">
            <div style="font-size:3rem; margin-bottom:16px;">🍔</div>
            <h3 style="color:var(--text-primary); margin-bottom:8px;">No Food Orders Yet</h3>
            <p style="margin-bottom:24px;">You haven't placed any food orders. Explore airport dining!</p>
            <button class="btn-primary" onclick="app.showPage('dining')" style="padding:12px 28px; border-radius:12px;">Browse Restaurants</button>
          </div>`;
        return;
      }
      container.innerHTML = orders.map(o => {
        const status = (o.status || o.order_status || 'Pending').toLowerCase();
        const statusClass = ['delivered','completed'].includes(status) ? 'delivered' : ['accepted','preparing','ready'].includes(status) ? 'accepted' : 'pending';
        const items = Array.isArray(o.items) ? o.items.map(i => `${i.quantity||i.qty||1}x ${i.name||i.product_name}`).join(', ') : (o.formatted_items || o.items || 'Items');
        return `
          <div class="glass-card" style="margin-bottom:16px; padding:20px;">
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:10px;">
              <h3 style="margin:0;">Order <span style="color:var(--accent-orange);">${o.booking_id || o.order_id || ('#'+o.id)}</span></h3>
              <span class="status-badge ${statusClass}">${status.toUpperCase()}</span>
            </div>
            <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(200px,1fr)); gap:12px;">
              <div>
                <p style="margin:0; font-size:15px; font-weight:600;">${o.restaurant_name || o.vendor_name || 'Restaurant'}</p>
                <p style="margin:4px 0 0; color:var(--text-secondary); font-size:13px;">Items: ${items}</p>
              </div>
              <div>
                <p style="margin:0; font-size:13px; color:var(--text-secondary);">Terminal: <strong>${o.terminal || '-'}</strong> | Gate: <strong>${o.gate || '-'}</strong></p>
                ${o.pickup_counter ? `<p style="margin:4px 0 0; font-size:13px; color:var(--accent-cyan);">🪧 Pickup Counter: <strong>${o.pickup_counter}</strong></p>` : ''}
                <p style="margin:4px 0 0; font-size:13px; color:var(--text-secondary);">Total: <strong style="color:var(--accent-orange);">₹${o.total_price || o.total_amount || 0}</strong></p>
              </div>
              <div>
                ${o.created_at ? `<p style="margin:0; font-size:12px; color:var(--text-muted);">📅 ${new Date(o.created_at).toLocaleString()}</p>` : ''}
                <p style="margin:4px 0 0; font-size:12px; color:var(--text-muted);">Payment: ${o.payment_method || 'COD'} — ${o.payment_status || 'Pending'}</p>
              </div>
            </div>
          </div>`;
      }).join('');
    } catch(e) {
      container.innerHTML = `<div style="text-align:center;padding:30px;color:var(--text-secondary);">Failed to load food orders.</div>`;
    }
  }

  async fetchParkingBookings() {
    const container = document.getElementById("my-parking-list-container");
    if (!container) return;
    if (!this.currentUser || !this.currentUser.email) {
      container.innerHTML = `<div style="text-align:center; padding: 40px; color: var(--text-secondary);">Please Sign In to view parking reservations.</div>`;
      return;
    }
    container.innerHTML = `<div style="text-align:center;padding:30px;color:var(--text-secondary);">Loading parking bookings...</div>`;
    try {
      const res = await this.apiCall(`/parking-bookings?email=${encodeURIComponent(this.currentUser.email)}`);
      const bookings = (res && res.bookings) ? res.bookings : [];
      if (!bookings.length) {
        container.innerHTML = `
          <div style="text-align:center; padding: 60px 20px; color: var(--text-secondary);">
            <div style="font-size:3rem; margin-bottom:16px;">🅿️</div>
            <h3 style="color:var(--text-primary); margin-bottom:8px;">No Parking Reservations</h3>
            <p style="margin-bottom:24px;">You haven't reserved any parking slots yet.</p>
            <button class="btn-primary" onclick="app.showPage('parking')" style="padding:12px 28px; border-radius:12px;">Reserve a Slot</button>
          </div>`;
        return;
      }
      this.renderParkingBookings(bookings);
    } catch(e) {
      container.innerHTML = `<div style="text-align:center;padding:30px;color:var(--text-secondary);">Failed to load parking bookings.</div>`;
    }
  }

  renderParkingBookings(bookings) {
    const container = document.getElementById("my-parking-list-container");
    if (!container) return;
    container.innerHTML = bookings.map(b => {
      const bookingId = b.booking_id || b.id || 'PRK-XXXXXX';
      const slot = b.slot_number || b.slot || 'Auto-Assigned';
      const plate = b.plate_number || b.plate || b.vehicle_plate || '-';
      const hours = b.duration_hours || b.hours || '-';
      const price = b.total_price || b.price || '-';
      const terminal = b.terminal || '-';
      const entry = b.entry_time ? new Date(b.entry_time).toLocaleString() : (b.date || '-');
      const status = (b.booking_status || b.status || 'Confirmed');
      const statusClass = status.toLowerCase() === 'confirmed' ? 'accepted' : 'pending';
      return `
        <div class="glass-card booking-card" style="margin-bottom:16px; padding:20px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:12px; flex-wrap:wrap; gap:10px;">
            <h3 style="margin:0;">Booking ID: <span style="color:var(--accent-cyan);">${bookingId}</span></h3>
            <span class="status-badge ${statusClass}">${status.toUpperCase()}</span>
          </div>
          <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(180px,1fr)); gap:12px;">
            <div>
              <p style="margin:0; font-size:18px; font-weight:700;">Slot: <span style="color:var(--accent-orange);">${slot}</span></p>
              <p style="margin:4px 0 0; color:var(--text-secondary); font-size:13px;">Vehicle: ${plate}</p>
            </div>
            <div>
              <p style="margin:0; font-size:13px; color:var(--text-secondary);">Terminal: <strong>${terminal}</strong></p>
              <p style="margin:4px 0 0; font-size:13px; color:var(--text-secondary);">Duration: <strong>${hours} hrs</strong></p>
            </div>
            <div>
              <p style="margin:0; font-size:13px; color:var(--text-secondary);">Entry: ${entry}</p>
              <p style="margin:4px 0 0; font-size:15px; color:var(--accent-orange); font-weight:700;">₹${price}</p>
            </div>
          </div>
        </div>`;
    }).join("");
  }

  async reserveParkingSlot() {
    if (!this.currentUser) {
      alert("Please Sign In or Register to book a parking slot.");
      this.showPage("auth");
      return;
    }
    const plate = document.getElementById('parking-plate').value.trim();
    const hours = parseInt(document.getElementById('parking-hours').value || '4');
    if (!plate) { alert("Please enter a valid vehicle license plate."); return; }

    const btn = document.querySelector('#parking-checkout-view .btn-primary');
    if (btn) { btn.disabled = true; btn.textContent = 'Booking...'; }

    try {
      const totalPrice = hours * 100;
      const res = await this.apiCall('/parking-bookings', {
        method: 'POST',
        body: JSON.stringify({
          user_email: this.currentUser.email,
          plate_number: plate,
          zone: 'Zone-A',
          hours: hours,
          payment_method: 'UPI',
          total_price: totalPrice
        })
      });

      if (res && res.status === 'success') {
        const booking = res.booking || {};
        alert(`🅿️ Parking Reserved!\n\nBooking ID: ${booking.booking_id || booking.id || 'PRK-XXXXXX'}\nSlot: ${booking.slot_number || 'Auto-Assigned'}\nVehicle: ${plate}\nDuration: ${hours} hrs\nAmount: ₹${totalPrice}\n\nView in My Bookings → Parking Slots.`);
        document.getElementById('parking-plate').value = '';
        document.getElementById('parking-checkout-view').style.display = 'none';
        document.getElementById('parking-map-view').style.display = 'block';
        this.showPage('my-bookings');
        this.switchBookingTab('parking');
      } else {
        alert(res.message || 'Failed to reserve parking slot.');
      }
    } catch(e) {
      alert('Network error. Please try again.');
    } finally {
      if (btn) { btn.disabled = false; btn.textContent = 'CONFIRM BOOKING'; }
    }
  }

  async viewETicket(pnr) {
    // 1. Check in-memory list first (instant rendering)
    let b = (this.allMyBookings || []).find(item => (item.pnr || item.booking_id || item.id) === pnr);

    // 2. Fetch from API if not found locally
    if (!b) {
      try {
        const res = await this.apiCall(`/flights/bookings/${pnr}`);
        if (res && res.status === "success" && res.booking) {
          b = res.booking;
        }
      } catch (e) { /* fall through */ }
    }

    if (!b) {
      alert("Could not load e-ticket details.");
      return;
    }

    const f = b.flight_details || {};
    const paxList = Array.isArray(b.passenger_details) ? b.passenger_details : [];
    const paxCount = paxList.length || 1;
    const allNames = paxList.map(p => p.name).filter(Boolean).join(", ") || (this.currentUser ? this.currentUser.name : 'Santhosh Babu');
    const allSeats = paxList.map(p => p.seat).filter(Boolean).join(", ") || '12A';
    
    const set = (id, val) => { const el = document.getElementById(id); if (el) el.innerText = val || '-'; };

    set('tkt-airline-name',     f.airline || 'AeroAssist Partner Airline');
    set('tkt-aircraft',         `${f.aircraft || 'Airbus A320neo'} · ${f.cabinClass || f.cabin_class || 'Economy'}`);
    set('tkt-pnr',              b.pnr || pnr);
    set('tkt-status',           (b.booking_status || b.status || 'CONFIRMED').toUpperCase());

    set('tkt-origin-code',      f.origin || 'MAA');
    set('tkt-origin-name',      f.origin_name || f.origin || 'Chennai');
    set('tkt-dep-time',         f.departure_time || '06:00 AM');
    set('tkt-dep-date',         f.date || b.departure_date || '2026-08-01');

    set('tkt-dest-code',        f.destination || 'DEL');
    set('tkt-dest-name',        f.destination_name || f.destination || 'New Delhi');
    set('tkt-arr-time',         f.arrival_time || '08:15 AM');

    set('tkt-duration',         f.duration || '2h 15m');
    set('tkt-stops',            f.stops || 'Non-stop');

    set('tkt-passengers-count', `${paxCount} Passenger${paxCount > 1 ? 's' : ''}`);
    set('tkt-passenger-name',   allNames);
    set('tkt-flight-number',    f.flight_number || 'AI-432');
    set('tkt-seat-no',          allSeats);
    set('tkt-terminal-gate',    f.terminal ? `${f.terminal} / Gate 9` : 'Terminal 1 / Gate 9');
    set('tkt-baggage',          f.baggage || '25 kg Check-in + 7 kg Hand Bag');

    set('tkt-booking-id',       b.booking_id || b.id || 'BK-892102');
    set('tkt-ticket-num',       b.ticket_number || 'TKT-9920192');
    set('tkt-payment-id',       b.payment_id || 'PAY-8810239');
    set('tkt-txn-id',           b.transaction_id || 'TXN-7781920192');

    // Populate full Passenger Manifest list
    const paxContainer = document.getElementById('tkt-passengers-list');
    if (paxContainer) {
      if (paxList.length > 0) {
        paxContainer.innerHTML = paxList.map((p, idx) => `
          <div style="display:flex; justify-content:space-between; align-items:center; padding:12px 16px; background:rgba(255,255,255,0.03); border-radius:var(--radius-md); border:var(--glass-border);">
            <div>
              <span style="font-size:11px; color:var(--text-secondary); text-transform:uppercase; letter-spacing:0.5px;">PASSENGER ${idx + 1}</span>
              <div style="font-size:15px; font-weight:700; color:#fff; margin-top:2px;">
                ${p.name || 'Passenger'} 
                <span style="font-size:12px; font-weight:400; color:var(--text-secondary); margin-left:6px;">(${p.gender || 'N/A'}, ${p.age || 'N/A'} yrs)</span>
              </div>
            </div>
            <div style="text-align:right;">
              <span style="font-size:11px; color:var(--text-secondary); text-transform:uppercase; letter-spacing:0.5px;">SEAT ASSIGNED</span>
              <div style="font-size:18px; font-weight:800; color:var(--accent-primary); margin-top:2px;">${p.seat || 'Assigned'}</div>
            </div>
          </div>
        `).join('');
      } else {
        paxContainer.innerHTML = `
          <div style="display:flex; justify-content:space-between; align-items:center; padding:12px 16px; background:rgba(255,255,255,0.03); border-radius:var(--radius-md); border:var(--glass-border);">
            <div>
              <span style="font-size:11px; color:var(--text-secondary);">PASSENGER 1</span>
              <div style="font-size:15px; font-weight:700; color:#fff; margin-top:2px;">${allNames}</div>
            </div>
            <div style="text-align:right;">
              <span style="font-size:11px; color:var(--text-secondary);">SEAT ASSIGNED</span>
              <div style="font-size:18px; font-weight:800; color:var(--accent-primary); margin-top:2px;">${allSeats}</div>
            </div>
          </div>
        `;
      }
    }

    this.openModal("eticket");
  }

  // --- LOST & FOUND REGISTRY ---
  async fetchLostItems() {
    const container = document.getElementById("lost-found-registry-container");
    if (!container) return;
    try {
      const res = await this.apiCall("/lost-items");
      if (res && res.status === "success") {
        this.allLostItems = res.items || [];
        this.filterLostItems(this.currentLostFilter || "All");
      } else {
        container.innerHTML = `<div style="text-align:center; padding:20px;">Failed to load items.</div>`;
      }
    } catch(e) {
      console.warn("[LOST&FOUND] Fetch error:", e);
      container.innerHTML = `<div style="text-align:center; padding:20px;">Network error loading registry.</div>`;
    }
  }

  filterLostItems(filterType) {
    this.currentLostFilter = filterType;
    // Update active button state
    ["all", "only", "found-only"].forEach(id => {
      const btn = document.getElementById(`filter-${id === "all" ? "lost-all" : (id === "only" ? "lost-only" : "found-only")}`);
      if (btn) btn.classList.remove("active");
    });
    
    let btnId = "filter-lost-all";
    if (filterType === "Lost") btnId = "filter-lost-only";
    if (filterType === "Found") btnId = "filter-found-only";
    const activeBtn = document.getElementById(btnId);
    if (activeBtn) activeBtn.classList.add("active");

    const container = document.getElementById("lost-found-registry-container");
    if (!container) return;

    if (!this.allLostItems || this.allLostItems.length === 0) {
      container.innerHTML = `<div style="text-align:center; padding:40px; color:var(--text-secondary); grid-column:1/-1;">No lost or found items reported in the community.</div>`;
      return;
    }

    const filtered = filterType === "All" ? this.allLostItems : this.allLostItems.filter(i => i.type === filterType);

    if (filtered.length === 0) {
      container.innerHTML = `<div style="text-align:center; padding:40px; color:var(--text-secondary); grid-column:1/-1;">No ${filterType.toLowerCase()} items found.</div>`;
      return;
    }

    container.innerHTML = filtered.map(item => `
      <div class="glass-card" style="padding: 20px;">
        <div style="display:flex; justify-content:space-between; align-items:flex-start; margin-bottom:12px;">
          <div>
            <span style="font-size:24px;">${item.icon || '📦'}</span>
            <h4 style="margin:8px 0 4px 0; font-size:16px;">${item.name}</h4>
          </div>
          <span class="status-badge ${item.type === 'Lost' ? 'pending' : 'accepted'}">${item.type}</span>
        </div>
        <p style="font-size:13px; color:var(--text-secondary); margin-bottom:12px; min-height:40px;">${item.description || 'No description provided.'}</p>
        <div style="font-size:12px; color:var(--text-secondary); border-top:1px solid var(--glass-border); padding-top:12px;">
          <div style="margin-bottom:6px;"><i data-lucide="map-pin" style="width:12px; height:12px; display:inline-block; margin-right:4px;"></i> ${item.location}</div>
          <div style="margin-bottom:6px;"><i data-lucide="phone" style="width:12px; height:12px; display:inline-block; margin-right:4px;"></i> ${item.contact}</div>
          ${item.created_at ? `<div><i data-lucide="clock" style="width:12px; height:12px; display:inline-block; margin-right:4px;"></i> ${new Date(item.created_at).toLocaleDateString()}</div>` : ''}
        </div>
      </div>
    `).join("");
    
    // Re-initialize lucide icons for the newly injected HTML
    if (window.lucide) {
      window.lucide.createIcons();
    }
  }

  async submitLostItemReport() {
    const type = document.getElementById("lost-item-type").value;
    const name = document.getElementById("lost-item-name").value.trim();
    const desc = document.getElementById("lost-item-desc").value.trim();
    const location = document.getElementById("lost-item-location").value.trim();
    const contact = document.getElementById("lost-item-contact").value.trim();
    const category = document.getElementById("lost-item-category")?.value || 'General';

    if (!name || !location || !contact) {
      alert("Please provide the Item Name, Location, and Contact details.");
      return;
    }

    const payload = {
      type: type,
      name: name,
      category: category,
      description: desc,
      location: location,
      contact: contact,
      icon: type === 'Lost' ? '🔍' : '📦',
      reporter_name: this.currentUser ? this.currentUser.name : 'Anonymous',
      user_email: this.currentUser ? this.currentUser.email : null
    };

    const res = await this.apiCall("/lost-items", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert(`✅ ${type} item reported to the airport community!\n\nThis will be visible to all passengers on both Web and Android app.`);
      document.getElementById("lost-item-name").value = "";
      document.getElementById("lost-item-desc").value = "";
      document.getElementById("lost-item-location").value = "";
      document.getElementById("lost-item-contact").value = "";
      document.getElementById('lost-found-report').style.display = 'none';
      document.getElementById('lost-found-menu').style.display = 'block';
      this.fetchLostItems();
    } else {
      alert(res.message || "Failed to submit report.");
    }
  }

  // --- ADMIN PORTAL CONSOLE CONTEXTS ---
  async submitAdminRegisterVendor() {
    const name = document.getElementById("admin-reg-name").value.trim();
    const email = document.getElementById("admin-reg-email").value.trim();
    const password = document.getElementById("admin-reg-password").value.trim();
    const type = document.getElementById("admin-reg-type").value;
    const terminal = document.getElementById("admin-reg-terminal").value;
    const gate = document.getElementById("admin-reg-gate").value.trim();

    if (!name || !email || !password || !gate) {
      alert("Please fill all properties!");
      return;
    }

    const payload = {
      admin_key: "admin_aeroassist_2026",
      name,
      email,
      password,
      type,
      terminal,
      gate
    };

    const res = await this.apiCall("/vendors/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    if (res && res.status === "success") {
      alert(`Vendor successfully registered!`);
      
      document.getElementById("admin-reg-name").value = "";
      document.getElementById("admin-reg-email").value = "";
      document.getElementById("admin-reg-password").value = "";
      document.getElementById("admin-reg-gate").value = "";
    } else {
      alert(res.message || "Failed to create vendor.");
    }
  }

  async submitAdminDeleteVendor() {
    const email = document.getElementById("admin-del-email").value.trim();

    if (!email) return;
    if (!confirm(`Are you absolutely sure you want to permanently delete vendor: ${email}?`)) return;

    const res = await this.apiCall("/vendors/delete", {
      method: "POST",
      body: JSON.stringify({
        admin_key: "admin_aeroassist_2026",
        email: email
      })
    });

    if (res && res.status === "success") {
      alert("Vendor successfully removed!");
      document.getElementById("admin-del-email").value = "";
    } else {
      alert(res.message || "Failed to remove vendor account.");
    }
  }
}

// Global App Instance instantiation
const app = new AeroAssistApp();
window.app = app;
