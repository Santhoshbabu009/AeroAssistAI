/**
 * AeroAssist AI - Complete Glassmorphic Web App Suite
 * Seamlessly integrates Passenger, Vendor, and Administrative portals.
 * Connected directly to local Flask python backend services.
 */

class AeroAssistApp {
  constructor() {
    this.API_BASE = (window.location.protocol === 'file:' || window.location.origin.includes('localhost') || window.location.origin.includes('127.0.0.1') || !window.location.origin) ? "https://aeroassistai.onrender.com/api" : "/api";
    this.AVIATION_STACK_KEY = "322876eed5ec416a01fffd3e4429c29e";
    
    // User Authentication Session State
    this.currentUser = JSON.parse(localStorage.getItem("user_session")) || null;
    this.currentUserType = localStorage.getItem("user_type") || null; // Visitor, Employee, Vendor
    this.authMode = "login"; // login, signup, verify
    
    // Vendor Session State
    this.currentVendor = JSON.parse(localStorage.getItem("vendor_session")) || null;
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
    
    this.init();
  }

  init() {
    this.initTheme();
    this.setupCommandPalette();
    this.setupInactivityListener();
    this.changeLanguage(this.currentLang);
    this.updateUserSessionUI();
    this.renderChatHistory();
    this.loadQuizQuestion();

    // Initial Fetch triggers
    this.fetchRestaurants();
    this.fetchLounges();
    
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
    
    // Setup background interval polling (Every 5 seconds)
    this.pollingInterval = setInterval(() => {
      this.fetchPassengerHistory();
      if (this.currentVendor) {
        this.fetchVendorQueue();
      }
      const trackerModal = document.getElementById("modal-order-tracker");
      if (this.activeTrackingOrderId && trackerModal && trackerModal.classList.contains("open")) {
        this.openOrderTracker(this.activeTrackingOrderId);
      }
    }, 5000);
  }

  // --- INACTIVITY AUTO-LOGOUT (20 MINUTES) ---
  setupInactivityListener() {
    this.inactivityTimeout = null;
    this.INACTIVITY_LIMIT = 20 * 60 * 1000; // 20 minutes in milliseconds
    const events = ["mousemove", "mousedown", "keydown", "touchstart", "scroll", "click"];
    events.forEach(evt => {
      window.addEventListener(evt, () => this.resetInactivityTimer(), { passive: true });
    });
    this.resetInactivityTimer();
  }

  resetInactivityTimer() {
    if (this.inactivityTimeout) {
      clearTimeout(this.inactivityTimeout);
    }
    if (!this.currentUser && !this.currentVendor) return;
    this.inactivityTimeout = setTimeout(() => {
      this.handleInactivityLogout();
    }, this.INACTIVITY_LIMIT);
  }

  handleInactivityLogout() {
    if (this.currentUser || this.currentVendor) {
      if (this.currentUser) {
        this.currentUser = null;
        this.currentUserType = null;
        localStorage.removeItem("user_session");
        localStorage.removeItem("user_type");
      }
      if (this.currentVendor) {
        this.currentVendor = null;
        localStorage.removeItem("vendor_session");
      }
      localStorage.removeItem("token");
      localStorage.removeItem("auth_token");
      this.updateUserSessionUI();
      this.updateSidebarRBAC();
      this.showPage("user-type");
      alert("You have been automatically logged out due to 20 minutes of inactivity.");
    }
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

  async submitUserVerification() {
    const otp = document.getElementById("auth-verify-otp").value.trim();
    
    if (!otp) {
      alert("Please enter the verification code!");
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
      
      const clientIdParam = googleClientId || "1082531649964-aeroassist.apps.googleusercontent.com";
      const redirectUri = encodeURIComponent(`${window.location.origin}/api/google-callback`);
      const googleOAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${clientIdParam}&redirect_uri=${redirectUri}&response_type=code%20id_token&scope=openid%20email%20profile&prompt=select_account`;

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
            await this.handleGoogleOAuthSuccess(userEmail);
          } else {
            this.promptGoogleOAuthEmail();
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

      // If popup blocked or client ID placeholder, trigger Google OAuth Account Picker
      if (!popup || popup.closed || !googleClientId) {
        this.promptGoogleOAuthEmail();
      }

    } catch (err) {
      console.error("[GOOGLE OAUTH ERROR]", err);
      this.promptGoogleOAuthEmail();
    } finally {
      if (btn) {
        btn.innerHTML = '<svg width="20" height="20" viewBox="0 0 48 48"><path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/><path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/><path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/><path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.18 1.48-4.97 2.31-8.16 2.31-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/></svg> Sign in with Google';
        btn.disabled = false;
      }
    }
  }

  async promptGoogleOAuthEmail() {
    const loginEmailInput = document.getElementById("auth-login-email");
    let email = loginEmailInput ? loginEmailInput.value.trim() : "";
    if (!email || !email.includes("@")) {
      email = prompt("Google Account OAuth Login - Select or enter your Gmail address:", "santhosh@gmail.com");
    }
    if (email && email.includes("@")) {
      await this.handleGoogleOAuthSuccess(email);
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

    if (res && res.status === "success" && res.token) {
      this.currentUser = {
        name: res.name || name,
        email: email.trim().toLowerCase(),
        token: res.token
      };
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      localStorage.setItem("token", res.token);
      localStorage.setItem("auth_token", res.token);

      this.updateUserSessionUI();
      this.showPage("dashboard");
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
        name: res.name || "Santhosh Babu",
        email: email,
        mobile: res.mobile || "9876543210",
        token: res.token || null
      };
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      this.updateUserSessionUI();
      this.showPage("dashboard");
    } else {
      alert(res.message || "Invalid credentials.");
    }
  }

  updateUserSessionUI() {
    const charBadge = document.getElementById("user-avatar-char");
    const nameLabel = document.getElementById("user-display-name");
    const emailLabel = document.getElementById("user-display-email");

    if (this.currentUser) {
      charBadge.innerText = this.currentUser.name.charAt(0).toUpperCase();
      nameLabel.innerText = this.currentUser.name;
      emailLabel.innerText = this.currentUser.email;
    } else {
      charBadge.innerText = "V";
      nameLabel.innerText = "Visitor Account";
      emailLabel.innerText = "Sign In / Sign Up";
    }
    this.updateSidebarRBAC();
    this.resetInactivityTimer();
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
      // NEVER show Vendor Portal for Employee or Visitor
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

    if (this.currentUser) {
      loggedIn.style.display = "block";
      loggedOut.style.display = "none";
      
      document.getElementById("profile-name").value = this.currentUser.name;
      document.getElementById("profile-mobile").value = this.currentUser.mobile || "";
      document.getElementById("profile-email").value = this.currentUser.email;
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

    const res = await this.apiCall("/update-profile", {
      method: "POST",
      body: JSON.stringify({ email: this.currentUser.email, name, mobile })
    });

    if (res && res.status === "success") {
      alert("Profile updated successfully!");
      this.currentUser.name = name;
      this.currentUser.mobile = mobile;
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      this.updateUserSessionUI();
      this.closeModal("profile");
    } else {
      alert("Failed to save profile changes.");
    }
  }

  logoutUser() {
    this.currentUser = null;
    this.currentUserType = null;
    localStorage.removeItem("user_session");
    localStorage.removeItem("user_type");
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
    const name = this.currentUser ? this.currentUser.name : "Santhosh Babu";
    
    // We send request to /chat endpoint in Flask
    try {
      const response = await fetch(`${this.API_BASE}/chat`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: msgText,
          email: email,
          user_type: "Passenger",
          session_id: this.chatSessionId,
          lang: lang
        })
      });
      const data = await response.json();
      
      // Remove thinking
      thinkingDiv.remove();

      let reply = data.reply || "I am currently offline or missing my configuration.";
      
      this.chatHistory.push({ isUser: false, text: reply });
      this.renderChatHistory();
    } catch (e) {
      thinkingDiv.remove();
      this.chatHistory.push({ isUser: false, text: "Server offline or unavailable. Please check backend API server status." });
      this.renderChatHistory();
    }
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
    if (!this.currentUser) return;
    
    const ordRes = await this.apiCall(`/orders?email=${this.currentUser.email}`);
    const bkRes = await this.apiCall(`/bookings?email=${this.currentUser.email}`);
    const orders = (ordRes && ordRes.orders) ? ordRes.orders : [];
    const bookings = (bkRes && bkRes.bookings) ? bkRes.bookings : [];

    // Update active banner for food orders
    const activeOrder = orders.find(ord => ["pending", "accepted", "preparing", "ready"].includes((ord.status || '').toLowerCase()));
    const diningBanner = document.getElementById("floating-dining-banner");
    
    if (activeOrder) {
      document.getElementById("floating-dining-status").innerText = activeOrder.status.toUpperCase();
      this.activeOrder = activeOrder;
      
      diningBanner.className = "floating-status-banner";
      if (["preparing", "ready"].includes(activeOrder.status.toLowerCase())) {
        diningBanner.classList.add("green");
      }
      diningBanner.style.display = "flex";
    } else {
      diningBanner.style.display = "none";
    }

    // Update active banner for lounges
    const activeBooking = bookings.find(b => ["pending", "confirmed"].includes((b.status || '').toLowerCase()));
    const loungeBanner = document.getElementById("floating-lounge-banner");
    
    if (activeBooking) {
      document.getElementById("floating-lounge-status").innerText = activeBooking.status.toUpperCase();
      loungeBanner.style.display = "flex";
    } else {
      loungeBanner.style.display = "none";
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
        this.openModal("admin-panel");
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

  switchVendorTab(tabId) {
    this.vendorTab = tabId;
    document.querySelectorAll(".tab-link").forEach(tab => tab.classList.remove("active"));
    document.getElementById(`tab-${tabId}`).classList.add("active");

    if (tabId === "queue") {
      this.fetchVendorQueue();
    } else {
      this.fetchVendorCatalog();
    }
  }

  async fetchVendorQueue() {
    if (!this.currentVendor) return;
    const isRestaurant = this.currentVendor.type === "restaurant";

    document.getElementById("vendor-portal-title").innerText = this.currentVendor.name;
    document.getElementById("vendor-portal-location").innerText = `Terminal ${this.currentVendor.terminal} • ${this.currentVendor.gate}`;

    const container = document.getElementById("vendor-queue-list");
    if (!container) return;

    if (isRestaurant) {
      const ordRes = await this.apiCall(`/vendors/orders?vendor_id=${this.currentVendor.id}`);
      const orders = (ordRes && ordRes.orders) ? ordRes.orders : [];
      if (!orders || orders.length === 0) {
        container.innerHTML = `<p style="text-align:center; padding: 40px 0; color:var(--text-secondary);">No orders in queue.</p>`;
        return;
      }

      container.innerHTML = orders.map(ord => {
        const itemStr = (ord.items && ord.items.length > 0)
          ? ord.items.map(i => `${i.product_name || i.name || 'Item'} x${i.quantity || i.qty || 1}`).join(", ")
          : (ord.formatted_items || 'Meal Select');
        const st = (ord.status || 'Pending').toLowerCase();
        return `
        <div class="glass-card" style="margin-bottom:12px; padding:16px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
            <h4 style="margin:0;">Order #${ord.id || ord.order_id} | Passenger: ${ord.user_email}</h4>
            <span class="status-badge ${st}">${ord.status}</span>
          </div>
          <p style="font-size:13px; margin:0 0 4px 0; color:var(--accent-blue);">📍 Delivery: ${ord.terminal || 'Terminal 1'} • ${ord.gate || 'Gate 1'}</p>
          <p style="font-size:13px; margin-bottom:12px;"><strong>Items:</strong> ${itemStr} | <strong>Total:</strong> ₹${(ord.total_price || 0).toFixed(2)} [${ord.payment_method || 'COD'}]</p>
          <div style="display:flex; gap:10px;">
            ${st === 'pending' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Accepted')">Accept Order</button>
              <button class="btn-danger" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Rejected')">Reject</button>
            ` : ''}
            ${st === 'accepted' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Preparing')">Start Cooking</button>
            ` : ''}
            ${st === 'preparing' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Ready')">Mark Ready</button>
            ` : ''}
            ${st === 'ready' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Out for Delivery')">Send for Delivery</button>
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Delivered')">Deliver & Close</button>
            ` : ''}
            ${st === 'out for delivery' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Delivered')">Deliver & Close</button>
            ` : ''}
          </div>
        </div>
      `;
      }).join("");
    } else {
      const bkRes = await this.apiCall(`/bookings?vendor_id=${this.currentVendor.id}`);
      const bookings = (bkRes && bkRes.bookings) ? bkRes.bookings : [];
      if (!bookings || bookings.length === 0) {
        container.innerHTML = `<p style="text-align:center; padding: 40px 0; color:var(--text-secondary);">No active bookings.</p>`;
        return;
      }

      container.innerHTML = bookings.map(b => `
        <div class="glass-card" style="margin-bottom:12px; padding:16px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
            <h4 style="margin:0;">Pass #${b.id} | Passenger: ${b.email}</h4>
            <span class="status-badge ${b.status.toLowerCase()}">${b.status}</span>
          </div>
          <p style="font-size:13px; margin-bottom:12px;">Slot: ${b.date} at ${b.time} | Guests: ${b.guests}</p>
          <div style="display:flex; gap:10px;">
            ${b.status.toLowerCase() === 'pending' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateBookingStatus(${b.id}, 'Confirmed')">Confirm Slot</button>
              <button class="btn-danger" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateBookingStatus(${b.id}, 'Cancelled')">Cancel Pass</button>
            ` : ''}
          </div>
        </div>
      `).join("");
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
    const container = document.getElementById("vendor-catalog-grid");
    if (!container) return;

    if (!products || products.length === 0) {
      container.innerHTML = `<p style="grid-column:1/-1; text-align:center;">No catalog products found. Add your first item above!</p>`;
      return;
    }

    container.innerHTML = products.map(prod => `
      <div class="glass-card" style="display:flex; gap:16px; align-items:center; padding:16px;">
        <img style="width:60px; height:60px; border-radius:8px; object-fit:cover;" src="${this.getProductImage(prod)}" alt="${prod.name}">
        <div style="flex:1;">
          <h4 style="margin:0; font-size:15px;">${prod.name}</h4>
          <strong style="color:var(--accent-orange);">₹${prod.price}</strong>
        </div>
        <button class="btn-danger" style="width:auto; height:32px; padding:0 12px; font-size:12px;" onclick="app.submitDeleteProduct(${prod.id})">Delete</button>
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
