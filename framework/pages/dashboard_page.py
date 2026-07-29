from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.common.keys import Keys

class DashboardPage:
    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(driver, 15)
        
        self.THEME_TOGGLE = (By.CSS_SELECTOR, '.theme-toggle-btn')
        self.LANG_SELECT = (By.ID, 'global-lang-select')
        self.FLIGHT_SEARCH_INPUT = (By.ID, 'dash-flight-search-input')
        self.TRACK_BTN = (By.ID, 'dash-flight-track-btn')
        self.NAV_LINKS = (By.CSS_SELECTOR, '.nav-link-btn')
        
    def navigate_to(self, page_name):
        self.driver.execute_script(f"if(window.app) window.app.showPage('{page_name}');")
        
    def verify_page_loaded(self, page_id):
        element = self.wait.until(EC.visibility_of_element_located((By.ID, f'view-{page_id}')))
        return element.is_displayed()
        
    def search_flight(self, iata_code):
        input_el = self.wait.until(EC.visibility_of_element_located(self.FLIGHT_SEARCH_INPUT))
        input_el.clear()
        input_el.send_keys(iata_code)
        self.driver.find_element(*self.TRACK_BTN).click()
        
    def toggle_theme(self):
        self.wait.until(EC.element_to_be_clickable(self.THEME_TOGGLE)).click()
        
    def open_command_palette(self):
        # Using execute script for key combinations can be more reliable than ActionChains sometimes in headless
        self.driver.execute_script("document.dispatchEvent(new KeyboardEvent('keydown', {'key': 'k', 'ctrlKey': true}));")
        
    def change_language(self, lang_code):
        select = self.wait.until(EC.element_to_be_clickable(self.LANG_SELECT))
        select.click()
        # Find option
        option = self.wait.until(EC.element_to_be_clickable((By.CSS_SELECTOR, f"option[value='{lang_code}']")))
        option.click()
        
    def get_nav_links(self):
        return self.wait.until(EC.presence_of_all_elements_located(self.NAV_LINKS))
        
    def click_nav(self, nav_id):
        link = self.wait.until(EC.element_to_be_clickable((By.ID, nav_id)))
        link.click()
