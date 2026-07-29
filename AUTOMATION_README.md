# AeroAssist AI — Enterprise Automation Testing Framework

![Selenium](https://img.shields.io/badge/Selenium-4.x-green?logo=selenium)
![Appium](https://img.shields.io/badge/Appium-2.x-blue?logo=appium)
![k6](https://img.shields.io/badge/k6-Load%20Testing-purple?logo=k6)
![Python](https://img.shields.io/badge/Python-3.11-yellow?logo=python)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI%2FCD-black?logo=githubactions)

---

## Overview

A complete, production-ready, enterprise-grade QA Automation Testing Framework for the **AeroAssist AI** platform covering:

| Domain | Tests | Framework |
|--------|-------|-----------|
| Web E2E (Selenium) | 400+ | Python + Pytest + POM |
| Mobile E2E (Appium) | 400+ | Python + Pytest + UIAutomator2 |
| Vulnerability / Security | 400+ | Python + OWASP ZAP + Bandit |
| Load / Performance | 400+ | k6 (JavaScript) |
| **Total** | **1600+** | **Hybrid Data-Driven** |

---

## Project Structure

```
AeroAssistAI/
├── tests/
│   ├── selenium/              # Web E2E test scripts
│   │   ├── conftest.py
│   │   ├── test_authentication.py
│   │   ├── test_dashboard.py
│   │   ├── test_flight_booking.py
│   │   ├── test_my_bookings.py
│   │   ├── test_ui_components.py
│   │   └── test_negative.py
│   ├── appium/               # Mobile E2E test scripts
│   │   ├── conftest.py
│   │   ├── test_app_launch.py
│   │   ├── test_mobile_auth.py
│   │   ├── test_mobile_booking.py
│   │   ├── test_mobile_ui.py
│   │   ├── test_mobile_offline.py
│   │   └── test_sync.py
│   ├── security/             # OWASP + Injection + API Security
│   │   ├── conftest.py
│   │   ├── test_owasp.py
│   │   ├── test_injection.py
│   │   ├── test_auth_security.py
│   │   └── test_api_security.py
│   └── load/                 # k6 performance scripts
│       ├── k6_load_test.js
│       ├── k6_stress_test.js
│       ├── k6_spike_test.js
│       ├── k6_soak_test.js
│       └── generate_load_report.py
│
├── framework/
│   ├── pages/
│   │   ├── auth_page.py
│   │   ├── dashboard_page.py
│   │   ├── flight_booking_page.py
│   │   └── mobile/
│   │       ├── mobile_auth_page.py
│   │       ├── mobile_dashboard_page.py
│   │       └── mobile_booking_page.py
│   ├── drivers/
│   │   ├── web_driver_factory.py
│   │   └── mobile_driver_factory.py
│   ├── utils/
│   │   ├── self_healing_driver.py
│   │   ├── logger.py
│   │   └── screenshot_utils.py
│   ├── config/
│   │   └── config.py
│   ├── security/
│   │   └── vulnerability_scanner.py
│   ├── testdata/
│   │   ├── selenium_test_data.json
│   │   ├── appium_test_data.json
│   │   ├── security_payloads.json
│   │   └── load_scenarios.json
│   ├── reports/
│   │   ├── excel/
│   │   │   ├── selenium_report.py
│   │   │   ├── appium_report.py
│   │   │   └── security_report.py
│   │   ├── html/
│   │   ├── json/
│   │   └── xml/
│   └── logs/
│       ├── screenshots/
│       └── videos/
│
├── selenium-tests/
│   ├── generate_enterprise_report.py   # Master Excel Dashboard
│   └── generate_report.py
├── appium-tests/
│   └── generate_report.py
│
├── requirements/
│   ├── selenium_requirements.txt
│   ├── appium_requirements.txt
│   └── security_requirements.txt
│
├── Test_Results/                        # Generated reports output
│
├── .github/
│   └── workflows/
│       ├── selenium.yml
│       ├── appium.yml
│       ├── security.yml
│       └── load.yml
│
└── README.md
```

---

## Quick Start

### 1. Prerequisites

| Tool | Version |
|------|---------|
| Python | 3.11+ |
| Node.js | 20+ |
| Java JDK | 17+ |
| Android SDK | API 34+ |
| k6 | Latest |
| Chrome/Firefox | Latest |

### 2. Install Selenium Dependencies
```bash
pip install -r requirements/selenium_requirements.txt
```

### 3. Run Selenium Tests
```bash
# Run all Selenium tests (parallel, Chrome)
pytest tests/selenium/ -n auto --html=framework/reports/html/selenium_report.html --self-contained-html

# Run specific module
pytest tests/selenium/test_authentication.py -v

# Cross-browser
BROWSER=firefox pytest tests/selenium/ -v

# With retries
pytest tests/selenium/ --rerun-failures 3
```

### 4. Install Appium Dependencies
```bash
pip install -r requirements/appium_requirements.txt
npm install -g appium@2.2.3
appium driver install uiautomator2
```

### 5. Run Appium Tests
```bash
# Start Appium server
appium --address 127.0.0.1 --port 4723 &

# Run mobile tests
pytest tests/appium/ -v --html=framework/reports/html/appium_report.html
```

### 6. Run Security Tests
```bash
pip install -r requirements/security_requirements.txt

# SAST (Static Analysis)
bandit -r backend/ --severity-level medium

# Security test suite
pytest tests/security/ -v --html=framework/reports/html/security_report.html

# Dependency scan
safety check
```

### 7. Run Load Tests
```bash
# Install k6
# Linux: sudo apt install k6
# macOS: brew install k6
# Windows: choco install k6

# Smoke test
k6 run tests/load/k6_load_test.js -e API_URL=https://aeroassistai.onrender.com/api

# Stress test
k6 run tests/load/k6_stress_test.js

# Generate load report
python tests/load/generate_load_report.py
```

### 8. Generate Enterprise Excel Report
```bash
python selenium-tests/generate_enterprise_report.py
# Output: Test_Results/Enterprise_QA_Dashboard.xlsx
```

---

## CI/CD: GitHub Actions

All 4 workflows are in `.github/workflows/`.

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `selenium.yml` | Push / PR / Manual | Web E2E on Chrome + Firefox matrix |
| `appium.yml` | Push / PR / Manual | Mobile E2E on Android API 31/33/34 |
| `security.yml` | Push / Weekly | SAST + DAST + Security Suite |
| `load.yml` | Push to main / Weekly | k6 Smoke/Load/Stress/Spike |

**Triggering manually:**
1. Go to GitHub → Actions tab
2. Select a workflow
3. Click "Run workflow"
4. Choose options (browser, scenario, etc.)
5. Download artifacts from the completed run

---

## Self-Healing Framework

The `SelfHealingDriver` (`framework/utils/self_healing_driver.py`) automatically:

1. **Tries multiple locator strategies** in priority order: ID → CSS → XPath → Text
2. **Applies smart waits** with configurable polling intervals
3. **Logs which locator succeeded** to build a healing history
4. **Retries on StaleElementException** automatically up to 3 times
5. **Captures screenshot + console logs** on any test failure

> Tests failing due to **automation issues** (locator changes, timing) are auto-repaired.  
> Tests failing due to **real application bugs** remain FAILED and generate full bug reports.

---

## Reporting

| Report Type | Location | Format |
|-------------|----------|--------|
| Enterprise Dashboard | `Test_Results/Enterprise_QA_Dashboard.xlsx` | Excel |
| Selenium HTML | `framework/reports/html/` | HTML |
| Appium HTML | `framework/reports/html/` | HTML |
| Security HTML | `framework/reports/html/` | HTML |
| JUnit XML | `framework/reports/xml/` | XML |
| JSON Results | `framework/reports/json/` | JSON |
| Load Report | `Test_Results/Load_Report_*.xlsx` | Excel |
| Screenshots | `framework/logs/screenshots/` | PNG |

---

## Test Coverage

### Selenium (400+ tests)
- Authentication: Login, Registration, OTP, Google OAuth, Role-Based, Logout
- Dashboard: Navigation, Theme, Language, Command Palette, Live Flight Search
- Flight Booking: Search, Seat Selection, Payment, E-Ticket, PNR
- My Bookings: History, Sync, E-Ticket View, Cancellation
- UI: Components, Modals, Forms, Responsiveness, Accessibility
- Negative: SQL Injection inputs, XSS in fields, Boundary values, Empty fields

### Appium (400+ tests)
- App Launch: All Android API levels 10-15, Portrait/Landscape, Kill/Resume
- Authentication: Login, Registration, Biometric, Session persistence
- Booking: Full flow, Payment, E-Ticket, QR/Barcode
- UI: Orientation, Dark Mode, Back Navigation, Deep Links
- Offline: Booking while offline, Sync on reconnect, Cache display
- Sync: Cross-platform (book on web → verify on app and vice versa)

### Security (400+ tests)
- OWASP Top 10 coverage (A01–A10)
- SQL Injection (80 payloads)
- XSS — Reflected, Stored, DOM (60 payloads)
- Auth: JWT manipulation, expired tokens, none algorithm, role escalation
- API: CORS, IDOR, Mass assignment, HTTP method enumeration
- Headers: CSP, HSTS, X-Frame-Options, X-Content-Type-Options

### Load (400+ scenarios)
- Smoke: 5 VUs, 30s
- Load: 10 → 250 VUs ramping
- Stress: 0 → 1000 VUs (breaking point)
- Spike: Sudden burst to 500 VUs
- Soak: 50 VUs sustained (memory leak detection)
- Metrics: p95, p99, error rate, throughput

---

## Configuration

All configuration via environment variables (see `framework/config/config.py`):

```bash
export BASE_URL="http://127.0.0.1:5500/web/index.html"
export API_URL="https://aeroassistai.onrender.com/api"
export BROWSER="chrome"
export HEADLESS="true"
export TEST_EMAIL="testuser@aeroassist.com"
export TEST_PASSWORD="Test@1234"
export ENVIRONMENT="staging"
export BUILD_VERSION="v2.4.1"
export MAX_RETRIES="3"
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| ChromeDriver version mismatch | `pip install webdriver-manager` handles auto-install |
| Appium connection refused | Ensure `appium` server is running on port 4723 |
| No emulator found | Run `avdmanager list avd` and check device is started |
| k6 not found | Install via package manager (apt/brew/choco) |
| Permission denied Excel | Close the file in Excel before regenerating |
| Element not found (Selenium) | Self-healing driver will retry with alternate locators |

---

*Generated by AeroAssist AI Enterprise QA Framework v1.0.0*
