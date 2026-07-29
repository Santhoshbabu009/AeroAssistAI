import pytest
import json
import os
from framework.pages.auth_page import AuthPage
from framework.config.config import Config

def load_data():
    base_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
    data_path = os.path.join(base_dir, 'framework', 'testdata', 'selenium_test_data.json')
    with open(data_path, 'r') as f:
        return json.load(f)

test_data = load_data()
auth_data = test_data.get('auth_tests', [])

@pytest.mark.usefixtures("browser")
class TestAuthentication:
    
    @pytest.mark.parametrize("data", auth_data, ids=[item['id'] for item in auth_data])
    def test_auth_scenarios(self, browser, base_url, data):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        
        email = data['email']
        password = data['password']
        
        auth_page.login(email, password)
        
        if data['expected_result'] == 'success':
            assert auth_page.verify_dashboard_loaded(), "Dashboard failed to load on successful login"
        else:
            assert auth_page.verify_login_error(), "Error message not displayed on invalid login"

    def test_guest_access(self, browser, base_url):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        auth_page.enter_as_guest()
        assert auth_page.verify_dashboard_loaded(), "Dashboard failed to load for guest"
        
    def test_logout(self, browser, base_url):
        browser.get(base_url)
        auth_page = AuthPage(browser)
        auth_page.navigate_to_auth()
        auth_page.login(Config.VALID_EMAIL, Config.VALID_PASSWORD)
        assert auth_page.verify_dashboard_loaded()
        auth_page.logout()
        # Verify back to auth or home
        assert "login" in browser.page_source.lower() or "visitor" in browser.page_source.lower()
