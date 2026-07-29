import os
from datetime import datetime

class ScreenshotUtils:
    @staticmethod
    def get_screenshot_path(test_name):
        base_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
        date_str = datetime.now().strftime('%Y%m%d')
        screenshot_dir = os.path.join(base_dir, 'framework', 'logs', 'screenshots', date_str)
        os.makedirs(screenshot_dir, exist_ok=True)
        return os.path.join(screenshot_dir, f"{test_name}_{datetime.now().strftime('%H%M%S')}.png")

    @staticmethod
    def capture(driver, test_name):
        try:
            path = ScreenshotUtils.get_screenshot_path(test_name)
            driver.save_screenshot(path)
            return path
        except Exception as e:
            print(f"Failed to capture screenshot: {e}")
            return None
