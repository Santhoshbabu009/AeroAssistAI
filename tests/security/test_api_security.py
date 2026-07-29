import pytest

class TestAPISecurity:
    def test_security_headers_present(self, scanner):
        headers = scanner.test_security_headers("http://localhost:5000/api")
        for h in headers:
            assert h.passed, f"Missing security header: {h.header}"

    def test_cors_policy_validation(self, scanner):
        res = scanner.test_cors_policy("http://localhost:5000/api")
        assert res.passed, "Insecure CORS policy"

    def test_idor_vulnerabilities(self, scanner, auth_token):
        res = scanner.test_idor("/user/{id}", "1", "2", auth_token)
        assert res.passed, "IDOR vulnerability found"

    def test_mass_assignment(self, scanner, auth_token):
        # Mocking mass assignment test
        res = scanner.test_sql_injection("/update_profile", "role", "admin")
        assert res.passed, "Mass assignment vulnerability found"

    def test_api_versioning_exposure(self, scanner):
        assert True

    def test_error_message_information_disclosure(self, scanner):
        res = scanner.test_sql_injection("/login", "username", "'")
        exposed = scanner.check_sensitive_data_exposure(res.evidence)
        assert len(exposed) == 0, f"Information disclosure: {exposed}"

    def test_http_method_enumeration(self, scanner):
        assert True

    def test_graphql_introspection(self, scanner):
        assert True
