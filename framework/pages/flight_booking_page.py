from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

class FlightBookingPage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 15)
        
        self.ORIGIN_INPUT = (By.ID, 'flight-origin')
        self.DESTINATION_INPUT = (By.ID, 'flight-destination')
        self.DATE_INPUT = (By.ID, 'flight-date')
        self.PASSENGER_INPUT = (By.ID, 'flight-passengers')
        self.SEARCH_BTN = (By.ID, 'flight-search-btn')
        self.FIRST_SELECT_BTN = (By.CSS_SELECTOR, '.flight-result .select-btn')
        self.PAX_NAME = (By.ID, 'pax-name')
        self.PAX_AGE = (By.ID, 'pax-age')
        self.PAX_PASSPORT = (By.ID, 'pax-passport')
        self.SEAT_MAP = (By.ID, 'seat-map')
        self.PROCEED_PAYMENT_BTN = (By.ID, 'proceed-payment-btn')
        self.CARD_NUM = (By.ID, 'card-number')
        self.CARD_EXP = (By.ID, 'card-exp')
        self.CARD_CVV = (By.ID, 'card-cvv')
        self.CONFIRM_PAYMENT_BTN = (By.ID, 'confirm-payment-btn')
        self.CONFIRMATION_MSG = (By.ID, 'booking-confirmation')
        self.VIEW_ETICKET_BTN = (By.ID, 'view-eticket-btn')

    def navigate_to_flights(self):
        self.driver.execute_script("if(window.app) window.app.showPage('flights');")
        
    def search_flights(self, origin, destination, date, passengers):
        self.wait.until(EC.visibility_of_element_located(self.ORIGIN_INPUT)).send_keys(origin)
        self.driver.find_element(*self.DESTINATION_INPUT).send_keys(destination)
        self.driver.find_element(*self.DATE_INPUT).send_keys(date)
        self.driver.find_element(*self.PASSENGER_INPUT).send_keys(str(passengers))
        self.driver.find_element(*self.SEARCH_BTN).click()
        
    def select_first_available_flight(self):
        self.wait.until(EC.element_to_be_clickable(self.FIRST_SELECT_BTN)).click()
        
    def fill_passenger_details(self, name, age, passport):
        self.wait.until(EC.visibility_of_element_located(self.PAX_NAME)).send_keys(name)
        self.driver.find_element(*self.PAX_AGE).send_keys(str(age))
        self.driver.find_element(*self.PAX_PASSPORT).send_keys(passport)
        
    def select_seat(self, seat_code):
        seat = self.wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, f".seat[data-seat='{seat_code}']")))
        seat.click()
        
    def proceed_to_payment(self):
        self.driver.find_element(*self.PROCEED_PAYMENT_BTN).click()
        
    def complete_dummy_payment(self):
        self.wait.until(EC.visibility_of_element_located(self.CARD_NUM)).send_keys("4111111111111111")
        self.driver.find_element(*self.CARD_EXP).send_keys("12/25")
        self.driver.find_element(*self.CARD_CVV).send_keys("123")
        self.driver.find_element(*self.CONFIRM_PAYMENT_BTN).click()
        
    def verify_booking_confirmed(self, pnr=None):
        msg = self.wait.until(EC.visibility_of_element_located(self.CONFIRMATION_MSG)).text
        if pnr:
            return pnr in msg
        return True
        
    def view_eticket(self, pnr=None):
        self.wait.until(EC.element_to_be_clickable(self.VIEW_ETICKET_BTN)).click()
