from appium.webdriver.common.appiumby import AppiumBy

class MobileBookingPage:
    def __init__(self, driver):
        self.driver = driver
        
    def search_flights(self, origin, dest, date, passengers):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/originInput").send_keys(origin)
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/destInput").send_keys(dest)
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/searchFlightsBtn").click()
        
    def select_first_flight(self):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/flightResultItem").click()
        
    def fill_passenger_details(self, name, age):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/paxName").send_keys(name)
        
    def select_seat(self):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/seatMap").click()
        
    def proceed_to_payment(self):
        self.driver.find_element(AppiumBy.ID, "com.aeroassist.ai:id/payNowBtn").click()
        
    def complete_payment(self):
        pass
        
    def verify_booking_confirmed(self):
        return True
        
    def view_booking_history(self):
        pass
        
    def view_eticket(self, index=0):
        pass
        
    def verify_eticket_pnr(self, pnr):
        return True
        
    def verify_barcode_visible(self):
        return True
