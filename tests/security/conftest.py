import pytest
import requests
import json
import os
import sys

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../../')))
from framework.security.vulnerability_scanner import VulnerabilityScanner
from framework.reports.excel.security_report import SecurityExcelReporter

@pytest.fixture(scope="session")
def api_session():
    return requests.Session()

@pytest.fixture(scope="session")
def scanner(api_session):
    return VulnerabilityScanner(base_url="http://localhost:5000", api_url="http://localhost:5000/api", session=api_session)

@pytest.fixture(scope="session")
def security_reporter():
    reporter = SecurityExcelReporter(filepath="framework/reports/excel/security_report.xlsx")
    yield reporter
    reporter.generate_report()

@pytest.fixture(scope="session")
def auth_token(api_session):
    # Mock token for testing
    return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.mocktoken"

def load_payloads():
    payloads_file = os.path.join(os.path.dirname(__file__), '../../framework/testdata/security_payloads.json')
    try:
        with open(payloads_file, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        return {}

def pytest_generate_tests(metafunc):
    payloads = load_payloads()
    if 'sql_payload' in metafunc.fixturenames:
        metafunc.parametrize('sql_payload', payloads.get('sql_injection_payloads', []))
    if 'xss_payload' in metafunc.fixturenames:
        metafunc.parametrize('xss_payload', payloads.get('xss_payloads', []))
    if 'path_payload' in metafunc.fixturenames:
        metafunc.parametrize('path_payload', payloads.get('path_traversal', []))
    if 'cmd_payload' in metafunc.fixturenames:
        metafunc.parametrize('cmd_payload', payloads.get('command_injection', []))
