import pytest
from framework.pages.auth_page import AuthPage
from framework.pages.dashboard_page import DashboardPage
from framework.config.config import Config

@pytest.mark.usefixtures("browser")
class TestDashboard:
    @pytest.fixture(autouse=True)
    def setup(self, browser, base_url):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        auth_page.login(Config.VALID_EMAIL, Config.VALID_PASSWORD)
        self.dashboard = DashboardPage(browser)

    def test_dashboard_loads_after_login(self):
        assert self.dashboard.verify_page_loaded('dashboard')

    @pytest.mark.parametrize("page_name, page_id", [
        ('flights', 'flights'),
        ('my-bookings', 'my-bookings'),
        ('chat', 'chat'),
        ('dining', 'dining'),
        ('lounges', 'lounges'),
        ('wallet', 'wallet'),
        ('utilities', 'utilities'),
        ('baggage', 'baggage'),
        ('lost-found', 'lost-found'),
        ('parking', 'parking'),
        ('navigation', 'navigation'),
        ('community', 'community'),
        ('quiz', 'quiz'),
        ('profile', 'profile')
    ])
    def test_navigation_links(self, page_name, page_id):
        self.dashboard.navigate_to(page_name)
        assert self.dashboard.verify_page_loaded(page_id)

    def test_dark_mode_toggle(self):
        self.dashboard.toggle_theme()
        # Add assertion based on body class or style changes
        
    def test_command_palette_open(self):
        self.dashboard.open_command_palette()
        # Assert palette is visible
        
    def test_language_change_spanish(self):
        self.dashboard.change_language('es')
        # Add assertion for language text
