from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import time

class AuthPage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 15)
        
        self.VISITOR_BTN = (By.ID, 'visitor-btn')
        self.EMAIL_INPUT = (By.ID, 'login-email')
        self.PASSWORD_INPUT = (By.ID, 'login-password')
        self.LOGIN_BTN = (By.ID, 'login-btn')
        self.GUEST_BTN = (By.ID, 'guest-btn')
        self.SIGNUP_LINK = (By.ID, 'switch-to-signup')
        self.LOGIN_LINK = (By.ID, 'switch-to-login')
        self.SIGNUP_NAME = (By.ID, 'signup-name')
        self.SIGNUP_EMAIL = (By.ID, 'signup-email')
        self.SIGNUP_PASSWORD = (By.ID, 'signup-password')
        self.SIGNUP_MOBILE = (By.ID, 'signup-mobile')
        self.SIGNUP_BTN = (By.ID, 'signup-btn')
        self.ERROR_TOAST = (By.CSS_SELECTOR, '.toast-error')
        self.DASHBOARD_VIEW = (By.ID, 'view-dashboard')
        self.LOGOUT_BTN = (By.ID, 'logout-btn')

    def navigate_to_auth(self):
        # Open BASE_URL, handled by conftest
        self.wait.until(EC.element_to_be_clickable(self.VISITOR_BTN)).click()
        time.sleep(1) # wait for animation
        
    def login(self, email, password):
        self.wait.until(EC.visibility_of_element_located(self.EMAIL_INPUT)).send_keys(email)
        self.driver.find_element(*self.PASSWORD_INPUT).send_keys(password)
        self.driver.find_element(*self.LOGIN_BTN).click()
        
    def switch_to_signup_mode(self):
        self.wait.until(EC.element_to_be_clickable(self.SIGNUP_LINK)).click()
        
    def switch_to_login_mode(self):
        self.wait.until(EC.element_to_be_clickable(self.LOGIN_LINK)).click()

    def register(self, name, email, password, mobile):
        self.switch_to_signup_mode()
        self.wait.until(EC.visibility_of_element_located(self.SIGNUP_NAME)).send_keys(name)
        self.driver.find_element(*self.SIGNUP_EMAIL).send_keys(email)
        self.driver.find_element(*self.SIGNUP_PASSWORD).send_keys(password)
        self.driver.find_element(*self.SIGNUP_MOBILE).send_keys(mobile)
        self.driver.find_element(*self.SIGNUP_BTN).click()
        
    def enter_as_guest(self):
        self.wait.until(EC.element_to_be_clickable(self.GUEST_BTN)).click()
        
    def logout(self):
        self.driver.execute_script("localStorage.clear(); sessionStorage.clear();")
        self.driver.refresh()
        
    def verify_login_error(self):
        return self.wait.until(EC.visibility_of_element_located(self.ERROR_TOAST)).is_displayed()
        
    def verify_dashboard_loaded(self):
        return self.wait.until(EC.visibility_of_element_located(self.DASHBOARD_VIEW)).is_displayed()
