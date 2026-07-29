from appium import webdriver
from appium.options.android import UiAutomator2Options

class MobileDriverFactory:
    @staticmethod
    def create_driver(device_name="Android Emulator", platform_version="14.0", app_path=None):
        options = UiAutomator2Options()
        options.platform_name = 'Android'
        options.automation_name = 'UiAutomator2'
        options.device_name = device_name
        options.platform_version = platform_version
        options.app_package = 'com.aeroassist.ai'
        options.app_activity = 'com.aeroassist.ai.SplashActivity'
        options.no_reset = False
        options.auto_grant_permissions = True
        options.new_command_timeout = 300
        
        if app_path:
            options.app = app_path

        driver = webdriver.Remote("http://127.0.0.1:4723", options=options)
        driver.implicitly_wait(10)
        return driver
