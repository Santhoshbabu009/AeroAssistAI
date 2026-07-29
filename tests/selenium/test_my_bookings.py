import pytest
from framework.pages.auth_page import AuthPage
from framework.pages.dashboard_page import DashboardPage
from framework.config.config import Config

@pytest.mark.usefixtures("browser")
class TestMyBookings:
    @pytest.fixture(autouse=True)
    def setup(self, browser, base_url):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        auth_page.login(Config.VALID_EMAIL, Config.VALID_PASSWORD)
        self.dashboard = DashboardPage(browser)
        self.dashboard.navigate_to('my-bookings')

    def test_my_bookings_page_loads(self):
        assert self.dashboard.verify_page_loaded('my-bookings')

    def test_empty_state_when_no_bookings(self, browser):
        # Specific implementation to verify empty state
        assert "no bookings" in browser.page_source.lower() or "you haven't booked" in browser.page_source.lower()
