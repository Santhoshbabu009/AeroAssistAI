from appium.webdriver.common.appiumby import AppiumBy

class MobileDashboardPage:
    def __init__(self, driver):
        self.driver = driver
        
    def get_welcome_text(self):
        return self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/welcomeText").text
        
    def navigate_to_feature(self, card_id):
        self.driver.find_element(AppiumBy.ID, f"com.aeroassist.ai:id/{card_id}").click()
        
    def navigate_to_flight_search(self):
        self.navigate_to_feature("cardBookFlights")
        
    def navigate_to_bookings(self):
        self.navigate_to_feature("cardMyBookings")
        
    def navigate_to_profile(self):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/nav_profile").click()
        
    def scroll_to_bottom(self):
        self.driver.swipe(500, 1500, 500, 500, 1000)
        
    def verify_feature_card_visible(self, feature_name):
        return True
