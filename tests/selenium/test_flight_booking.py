import pytest
from framework.pages.auth_page import AuthPage
from framework.pages.flight_booking_page import FlightBookingPage
from framework.config.config import Config
import time

@pytest.mark.usefixtures("browser")
class TestFlightBooking:
    @pytest.fixture(autouse=True)
    def setup(self, browser, base_url):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        auth_page.login(Config.VALID_EMAIL, Config.VALID_PASSWORD)
        self.flights_page = FlightBookingPage(browser)
        self.flights_page.navigate_to_flights()

    def test_flight_search(self):
        self.flights_page.search_flights("JFK", "LHR", "2024-12-01", 1)
        # Check results
        
    def test_full_booking_flow(self):
        self.flights_page.search_flights("JFK", "LHR", "2024-12-01", 1)
        time.sleep(2)
        self.flights_page.select_first_available_flight()
        self.flights_page.fill_passenger_details("John Doe", 30, "A1234567")
        self.flights_page.select_seat("1A")
        self.flights_page.proceed_to_payment()
        self.flights_page.complete_dummy_payment()
        assert self.flights_page.verify_booking_confirmed()
        
    def test_view_eticket(self):
        self.test_full_booking_flow()
        self.flights_page.view_eticket()
        # assert ticket is visible
