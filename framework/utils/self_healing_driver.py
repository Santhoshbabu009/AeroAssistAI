import time
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, NoSuchElementException
from framework.utils.screenshot_utils import ScreenshotUtils
from framework.utils.logger import setup_logger

logger = setup_logger('SelfHealingDriver')

class SelfHealingDriver:
    MAX_RETRIES = 3
    WAIT_TIMEOUT = 15

    def __init__(self, driver):
        self.driver = driver
        self.wait = WebDriverWait(self.driver, self.WAIT_TIMEOUT)
        
    def find_element(self, locator_strategies: list):
        for locator in locator_strategies:
            try:
                element = self.wait.until(EC.presence_of_element_located(locator))
                logger.info(f"Successfully found element using locator: {locator}")
                return element
            except (TimeoutException, NoSuchElementException):
                logger.debug(f"Failed to find element using locator: {locator}. Trying next...")
                continue
        raise NoSuchElementException(f"Could not find element using any of the provided locators: {locator_strategies}")

    def auto_wait_and_click(self, locators: list):
        for _ in range(self.MAX_RETRIES):
            try:
                element = self.find_element(locators)
                self.wait.until(EC.element_to_be_clickable(locators[0]))
                element.click()
                return
            except Exception as e:
                logger.warning(f"Click failed, retrying... Exception: {e}")
                time.sleep(1)
        raise Exception(f"Failed to click element after {self.MAX_RETRIES} retries. Locators: {locators}")

    def auto_wait_and_type(self, locators: list, text: str):
        for _ in range(self.MAX_RETRIES):
            try:
                element = self.find_element(locators)
                self.wait.until(EC.visibility_of(element))
                element.clear()
                element.send_keys(text)
                return
            except Exception as e:
                logger.warning(f"Type failed, retrying... Exception: {e}")
                time.sleep(1)
        raise Exception(f"Failed to type into element after {self.MAX_RETRIES} retries. Locators: {locators}")

    def capture_on_failure(self, test_name):
        path = ScreenshotUtils.capture(self.driver, test_name)
        logs = self.get_browser_logs()
        logger.error(f"Test {test_name} failed. Screenshot: {path}")
        logger.error(f"Browser logs: {logs}")
        return path

    def get_browser_logs(self):
        try:
            return self.driver.get_log('browser')
        except:
            return []
