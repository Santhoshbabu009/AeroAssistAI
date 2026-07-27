/**
 * AeroAssist AI - Complete Glassmorphic Web App Suite
 * Seamlessly integrates Passenger, Vendor, and Administrative portals.
 * Connected directly to local Flask python backend services.
 */

class AeroAssistApp {
  constructor() {
    this.API_BASE = "https://web-production-6e916.up.railway.app/api";
    
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

    // Background Polling
    this.pollingInterval = null;
    
    this.init();
  }

  init() {
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
    }, 5000);
  }

  // --- ROLE GATE & SELECTION FLOW ---
  selectUserType(role) {
    this.currentUserType = role;
    localStorage.setItem("user_type", role);

    if (role === "Visitor" || role === "Employee") {
      this.showPage("auth");
      this.switchAuthMode("login");
      
      // Update label greeting
      const title = document.getElementById("auth-title");
      title.innerText = `Sign In as Airport ${role}`;
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

  // --- DYNAMIC WELCOME STATS ---
  updateDashboardStats() {
    if (this.currentUser) {
      document.getElementById("dash-rewards-val").innerText = "950 pts";
    } else {
      document.getElementById("dash-rewards-val").innerText = "150 pts";
    }
  }

  // --- UNIVERSAL API HELPER ---
  async apiCall(endpoint, options = {}) {
    try {
      const response = await fetch(`${this.API_BASE}${endpoint}`, {
        headers: { "Content-Type": "application/json" },
        ...options
      });
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
        mobile: "9876543210"
      };
      localStorage.setItem("user_session", JSON.stringify(this.currentUser));
      this.updateUserSessionUI();
      this.showPage("dashboard");
    } else {
      alert(res.message || "Incorrect verification code.");
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
        mobile: res.mobile || "9876543210"
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
      const response = await fetch("https://web-production-6e916.up.railway.app/chat", {
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
      this.chatHistory.push({ isUser: false, text: "Server offline. Make sure your Python Flask backend is running on Railway!" });
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
    const data = await this.apiCall("/restaurants");
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

  async showMenu(restaurantId, restaurantName) {
    this.showPage("menu");
    document.getElementById("menu-vendor-name").innerText = restaurantName;
    
    const container = document.getElementById("products-grid");
    container.innerHTML = `<p style="grid-column:1/-1; text-align:center;">Loading menu items...</p>`;
    
    const products = await this.apiCall(`/vendors/products?vendor_id=${restaurantId}`);
    this.tempSelectedRestaurant = { id: restaurantId, name: restaurantName };

    if (!products || products.length === 0) {
      container.innerHTML = `<p style="grid-column: 1/-1; text-align:center;">No items registered in this outlet yet.</p>`;
      return;
    }

    container.innerHTML = products.map(prod => `
      <div class="glass-card" style="display:flex; gap:16px; align-items:center; padding:16px;">
        <img style="width:80px; height:80px; border-radius:10px; object-fit:cover;" src="https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=150" alt="${prod.name}">
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

    const payload = {
      user_email: this.currentUser.email,
      vendor_id: this.cart.restaurant.id,
      items: this.cart.items.map(item => ({
        product_id: item.id,
        name: item.name,
        qty: item.qty,
        price: item.price
      })),
      terminal: terminal,
      gate: gate,
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
    const data = await this.apiCall("/lounges");
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
    
    const orders = await this.apiCall(`/orders?email=${this.currentUser.email}`);
    const bookings = await this.apiCall(`/bookings?email=${this.currentUser.email}`);

    // Update active banner for food orders
    const activeOrder = (orders || []).find(ord => ["pending", "accepted", "preparing", "ready"].includes(ord.status.toLowerCase()));
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
    const activeBooking = (bookings || []).find(b => ["pending", "confirmed"].includes(b.status.toLowerCase()));
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

    document.getElementById("tracker-restaurant-title").innerText = details.restaurant_name || "Food Order Tracking";
    document.getElementById("tracker-order-id").innerText = `Order #${details.order_id}`;
    document.getElementById("tracker-order-location").innerText = `${details.terminal || 'T1'} • ${details.gate || 'Gate 1'}`;
    document.getElementById("tracker-order-items").innerText = `Items: ${details.items || details.formatted_items || 'Meal Select'}`;

    const stepper = document.getElementById("tracker-stepper");
    const status = details.order_status.toLowerCase();

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
      const s1 = ["pending", "accepted", "preparing", "ready", "delivered"].includes(status);
      const s2 = ["accepted", "preparing", "ready", "delivered"].includes(status);
      const s3 = ["preparing", "ready", "delivered"].includes(status);
      const s4 = ["ready", "delivered"].includes(status);
      const s5 = ["delivered"].includes(status);

      stepper.innerHTML = `
        <div class="step-row ${s1 ? 'completed' : ''} ${status === 'pending' ? 'active' : ''}">
          <div class="step-circle">${s1 && status !== 'pending' ? '✓' : '1'}</div>
          <div class="step-label"><h4>Pending Confirmation</h4></div>
        </div>
        <div class="step-row ${s2 ? 'completed' : ''} ${status === 'accepted' ? 'active' : ''}">
          <div class="step-circle">${s2 && status !== 'accepted' ? '✓' : '2'}</div>
          <div class="step-label"><h4>Order Accepted</h4></div>
        </div>
        <div class="step-row ${s3 ? 'completed' : ''} ${status === 'preparing' ? 'active' : ''}">
          <div class="step-circle">${s3 && status !== 'preparing' ? '✓' : '3'}</div>
          <div class="step-label"><h4>Preparing Food</h4></div>
        </div>
        <div class="step-row ${s4 ? 'completed' : ''} ${status === 'ready' ? 'active' : ''}">
          <div class="step-circle">${s4 && status !== 'ready' ? '✓' : '4'}</div>
          <div class="step-label"><h4>Ready at Counter</h4></div>
        </div>
        <div class="step-row ${s5 ? 'completed' : ''}">
          <div class="step-circle">${s5 ? '✓' : '5'}</div>
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
      const orders = await this.apiCall(`/orders?vendor_id=${this.currentVendor.id}`);
      if (!orders || orders.length === 0) {
        container.innerHTML = `<p style="text-align:center; padding: 40px 0; color:var(--text-secondary);">No orders in queue.</p>`;
        return;
      }

      container.innerHTML = orders.map(ord => `
        <div class="glass-card" style="margin-bottom:12px; padding:16px;">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
            <h4 style="margin:0;">Order #${ord.id || ord.order_id} | Passenger: ${ord.user_email}</h4>
            <span class="status-badge ${ord.status.toLowerCase()}">${ord.status}</span>
          </div>
          <p style="font-size:13px; margin-bottom:12px;">Items: ${ord.formatted_items || 'Meal Select'}</p>
          <div style="display:flex; gap:10px;">
            ${ord.status.toLowerCase() === 'pending' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Accepted')">Accept Order</button>
              <button class="btn-danger" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Rejected')">Reject</button>
            ` : ''}
            ${ord.status.toLowerCase() === 'accepted' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Preparing')">Start Cooking</button>
            ` : ''}
            ${ord.status.toLowerCase() === 'preparing' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Ready')">Mark Ready</button>
            ` : ''}
            ${ord.status.toLowerCase() === 'ready' ? `
              <button class="btn-primary" style="height:36px; width:auto; padding:0 12px; font-size:13px;" onclick="app.updateOrderStatus(${ord.id || ord.order_id}, 'Delivered')">Deliver & Close</button>
            ` : ''}
          </div>
        </div>
      `).join("");
    } else {
      const bookings = await this.apiCall(`/bookings?vendor_id=${this.currentVendor.id}`);
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
    const products = await this.apiCall(`/vendors/products?vendor_id=${this.currentVendor.id}`);
    const container = document.getElementById("vendor-catalog-grid");
    if (!container) return;

    if (!products || products.length === 0) {
      container.innerHTML = `<p style="grid-column:1/-1; text-align:center;">No catalog products found.</p>`;
      return;
    }

    container.innerHTML = products.map(prod => `
      <div class="glass-card" style="display:flex; gap:16px; align-items:center; padding:16px;">
        <img style="width:60px; height:60px; border-radius:8px; object-fit:cover;" src="https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=150" alt="${prod.name}">
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
