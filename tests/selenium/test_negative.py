import pytest
from framework.pages.auth_page import AuthPage
from framework.config.config import Config

@pytest.mark.usefixtures("browser")
class TestNegativeCases:
    
    def test_unauthorized_access(self, browser, base_url):
        # Attempt to access dashboard directly
        browser.get(f"{base_url}#dashboard")
        # Should be redirected or show error
        assert "dashboard" not in browser.title.lower() or "login" in browser.page_source.lower()

    def test_network_error_simulation(self, browser):
        # This would typically require a proxy like BrowserMob or executing CDP commands to offline mode
        pass
