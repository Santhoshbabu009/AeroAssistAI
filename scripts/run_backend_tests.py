import os
import sys
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from scripts.generate_excel import create_styled_excel

os.environ["USE_SQLITE_TEST"] = "1"
os.environ["DISABLE_RATE_LIMIT"] = "1"

def run_backend_verification():
    print("==================================================")
    print("[RUN] RUNNING BACKEND VERIFICATION & UNIT TESTS")
    print("==================================================")

    test_modules = [
        ("API Endpoints", "backend/tests/test_api.py"),
        ("Authentication & RBAC", "backend/tests/test_auth.py"),
        ("Database Security", "backend/tests/test_database_security.py"),
        ("Vendor & Products", "backend/tests/test_vendor.py"),
        ("Parameterized Probes", "backend/tests/test_parameterized.py")
    ]

    all_sheets = {}

    for title, mod_path in test_modules:
        if not os.path.exists(mod_path):
            continue

        class TestPlugin:
            def __init__(self):
                self.results = []
            def pytest_runtest_logreport(self, report):
                if report.when == "call":
                    self.results.append({
                        "test_id": report.nodeid.split("::")[-1],
                        "module": os.path.basename(report.location[0]),
                        "test_name": report.nodeid.split("::")[-1],
                        "status": "PASSED" if report.passed else "FAILED",
                        "duration_sec": round(report.duration, 4),
                        "details": str(report.longrepr) if report.failed else "Validated successfully"
                    })

        plugin = TestPlugin()
        pytest.main(["-q", mod_path], plugins=[plugin])
        all_sheets[title] = plugin.results

    output_file = os.path.join("Test_Results", "Backend_Report.xlsx")
    create_styled_excel(output_file, "Backend Verification Report", all_sheets)
    print("==================================================")
    print(f"[OK] Backend verification completed: {output_file}")
    print("==================================================")

if __name__ == "__main__":
    run_backend_verification()
