import pytest

class TestInjectionAttacks:
    def test_sql_injection_login(self, scanner, sql_payload):
        res = scanner.test_sql_injection("/login", "username", sql_payload)
        assert res.passed, f"SQLi failed on login for {sql_payload}"

    def test_sql_injection_search(self, scanner, sql_payload):
        res = scanner.test_sql_injection("/search_flights", "query", sql_payload)
        assert res.passed, f"SQLi failed on search for {sql_payload}"

    def test_sql_injection_booking(self, scanner, sql_payload):
        res = scanner.test_sql_injection("/book_flight", "flight_id", sql_payload)
        assert res.passed, f"SQLi failed on booking for {sql_payload}"

    def test_xss_all_inputs(self, scanner, xss_payload):
        res = scanner.test_xss("/profile", "bio", xss_payload)
        assert res.passed, f"XSS failed on profile for {xss_payload}"

    def test_command_injection(self, scanner, cmd_payload):
        # Mocking generic command injection test on an export endpoint
        res = scanner.test_sql_injection("/export", "filename", cmd_payload)
        assert res.passed, f"Command injection failed for {cmd_payload}"

    def test_path_traversal(self, scanner, path_payload):
        res = scanner.test_path_traversal("/download", path_payload)
        assert res.passed, f"Path traversal failed for {path_payload}"

    def test_xxe_endpoints(self, scanner):
        xxe_payload = '<?xml version="1.0"?><!DOCTYPE root [<!ENTITY test SYSTEM "file:///etc/passwd">]><root>&test;</root>'
        res = scanner.test_sql_injection("/upload_xml", "data", xxe_payload)
        assert res.passed, "XXE injection vulnerability found"

    def test_nosql_injection(self, scanner):
        res = scanner.test_sql_injection("/login", "username", '{"$gt": ""}')
        assert res.passed, "NoSQL injection vulnerability found"

    def test_ldap_injection(self, scanner):
        res = scanner.test_sql_injection("/login", "username", "*)(uid=*))(|(uid=*")
        assert res.passed, "LDAP injection vulnerability found"
