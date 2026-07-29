import pytest
import os
import time
from framework.drivers.web_driver_factory import WebDriverFactory
from framework.utils.self_healing_driver import SelfHealingDriver
from framework.reports.excel.selenium_report import SeleniumExcelReporter
from framework.config.config import Config

@pytest.fixture(scope="session")
def base_url():
    return Config.BASE_URL

@pytest.fixture(scope="session")
def excel_reporter():
    reporter = SeleniumExcelReporter()
    yield reporter
    reporter.save()

@pytest.fixture(params=["chrome"]) # Can add "firefox", "edge"
def browser(request):
    browser_name = request.param if hasattr(request, 'param') else Config.BROWSER
    driver = WebDriverFactory.create_driver(browser_name, headless=Config.HEADLESS)
    yield driver
    driver.quit()

@pytest.fixture
def self_healing_driver(browser):
    return SelfHealingDriver(browser)

@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    setattr(item, "rep_" + rep.when, rep)

@pytest.fixture(autouse=True)
def test_wrapper(request, browser, excel_reporter):
    start_time = time.time()
    yield
    end_time = time.time()
    
    node = request.node
    if hasattr(node, 'rep_call'):
        status = "PASS" if node.rep_call.passed else "FAIL" if node.rep_call.failed else "SKIPPED"
        screenshot_path = ""
        
        if node.rep_call.failed:
            from framework.utils.screenshot_utils import ScreenshotUtils
            screenshot_path = ScreenshotUtils.capture(browser, node.name)
            
        excel_reporter.add_result({
            "Test ID": node.name,
            "Module": request.module.__name__,
            "Status": status,
            "Execution Time (s)": round(end_time - start_time, 2),
            "Screenshot Path": screenshot_path or "",
            "Browser": browser.name
        })
