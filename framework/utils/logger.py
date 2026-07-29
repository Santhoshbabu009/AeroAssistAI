import logging
import os
from datetime import datetime

def setup_logger(name, log_file=None):
    if log_file is None:
        log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'logs')
        os.makedirs(log_dir, exist_ok=True)
        date_str = datetime.now().strftime('%Y%m%d')
        log_file = os.path.join(log_dir, f'selenium_test_{date_str}.log')

    logger = logging.getLogger(name)
    
    log_level_str = os.getenv('LOG_LEVEL', 'INFO').upper()
    log_level = getattr(logging, log_level_str, logging.INFO)
    logger.setLevel(log_level)

    if not logger.handlers:
        formatter = logging.Formatter('%(asctime)s | %(levelname)s | %(name)s | %(message)s')

        file_handler = logging.FileHandler(log_file)
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)

        stream_handler = logging.StreamHandler()
        stream_handler.setFormatter(formatter)
        logger.addHandler(stream_handler)

    return logger
