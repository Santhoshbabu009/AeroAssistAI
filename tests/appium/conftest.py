import pytest
import os
from appium import webdriver
from appium.options.android import UiAutomator2Options

class MobileHelpers:
    def __init__(self, driver):
        self.driver = driver

@pytest.fixture(scope="session")
def appium_driver():
    options = UiAutomator2Options()
    options.platform_name = 'Android'
    options.automation_name = 'UiAutomator2'
    options.device_name = 'Android Emulator'
    options.app_package = 'com.aeroassist.ai'
    options.app_activity = 'com.aeroassist.ai.SplashActivity'
    options.no_reset = False
    options.auto_grant_permissions = True
    options.new_command_timeout = 300
    
    driver = webdriver.Remote("http://127.0.0.1:4723", options=options)
    driver.implicitly_wait(10)
    yield driver
    driver.quit()

@pytest.fixture(scope="function")
def mobile_helpers(appium_driver):
    return MobileHelpers(appium_driver)

@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    if rep.when == "call" and rep.failed:
        driver_fixture = item.funcargs.get("appium_driver")
        if driver_fixture:
            os.makedirs("screenshots", exist_ok=True)
            driver_fixture.save_screenshot(f"screenshots/{item.name}.png")
