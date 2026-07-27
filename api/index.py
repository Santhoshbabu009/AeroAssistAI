import sys
import os

# Include project root directory in sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from backend.app import app

# Export WSGI application entrypoint for Vercel Serverless Functions
handler = app
