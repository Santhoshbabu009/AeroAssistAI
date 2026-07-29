from appium.webdriver.common.appiumby import AppiumBy
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

class MobileAuthPage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 10)
        
    def navigate_to_login(self):
        self.wait.until(lambda d: "SplashActivity" not in d.current_activity)
        
    def login(self, email, password):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/loginEmail").send_keys(email)
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/loginPassword").send_keys(password)
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/loginButton").click()
        
    def register(self, name, email, password, mobile):
        pass
        
    def verify_logged_in(self):
        self.wait.until(EC.presence_of_element_located((AppiumBy.ID, "com.aeroassist.ai:id/welcomeText")))
        return True
        
    def logout(self):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/nav_profile").click()
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/logoutButton").click()
        
    def get_error_message(self):
        return "Error"
        
    def verify_on_dashboard(self):
        return "MainActivity" in self.driver.current_activity
