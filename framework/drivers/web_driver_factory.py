from selenium import webdriver
from selenium.webdriver.chrome.service import Service as ChromeService
from selenium.webdriver.firefox.service import Service as FirefoxService
from selenium.webdriver.edge.service import Service as EdgeService
from webdriver_manager.chrome import ChromeDriverManager
from webdriver_manager.firefox import GeckoDriverManager
from webdriver_manager.microsoft import EdgeChromiumDriverManager

class WebDriverFactory:
    @staticmethod
    def create_driver(browser, headless=True):
        browser = browser.lower()
        if browser == 'chrome':
            options = webdriver.ChromeOptions()
            if headless:
                options.add_argument('--headless=new')
            options.add_argument('--no-sandbox')
            options.add_argument('--disable-dev-shm-usage')
            options.add_argument('--window-size=1920,1080')
            driver = webdriver.Chrome(service=ChromeService(ChromeDriverManager().install()), options=options)
        elif browser == 'firefox':
            options = webdriver.FirefoxOptions()
            if headless:
                options.add_argument('-headless')
            driver = webdriver.Firefox(service=FirefoxService(GeckoDriverManager().install()), options=options)
        elif browser == 'edge':
            options = webdriver.EdgeOptions()
            if headless:
                options.add_argument('headless')
            driver = webdriver.Edge(service=EdgeService(EdgeChromiumDriverManager().install()), options=options)
        elif browser == 'safari':
            driver = webdriver.Safari()
        else:
            raise ValueError(f"Unsupported browser: {browser}")
        
        driver.implicitly_wait(10)
        driver.set_page_load_timeout(30)
        return driver
