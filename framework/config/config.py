import os
from dotenv import load_dotenv

load_dotenv()

class Config:
    BASE_URL = os.getenv('BASE_URL', 'http://127.0.0.1:5500/web/index.html')
    API_URL = os.getenv('API_URL', 'https://aeroassistai.onrender.com/api')
    BROWSER = os.getenv('BROWSER', 'chrome')
    HEADLESS = os.getenv('HEADLESS', 'true').lower() == 'true'
    IMPLICIT_WAIT = int(os.getenv('IMPLICIT_WAIT', '10'))
    EXPLICIT_WAIT = int(os.getenv('EXPLICIT_WAIT', '15'))
    MAX_RETRIES = int(os.getenv('MAX_RETRIES', '3'))
    VALID_EMAIL = os.getenv('TEST_EMAIL', 'testuser@aeroassist.com')
    VALID_PASSWORD = os.getenv('TEST_PASSWORD', 'Test@1234')
    ENVIRONMENT = os.getenv('ENVIRONMENT', 'staging')
    BUILD_VERSION = os.getenv('BUILD_VERSION', 'v2.4.1')
