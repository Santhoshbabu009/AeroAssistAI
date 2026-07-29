import pandas as pd
import os
from datetime import datetime

class AppiumExcelReporter:
    def __init__(self, filename="appium_test_report.xlsx"):
        self.filepath = os.path.join("framework", "reports", "excel", filename)
        self.results = []
        
    def add_result(self, test_name, status, device, version, duration, error_msg=""):
        self.results.append({
            "Test Name": test_name,
            "Status": status,
            "Device": device,
            "Android Version": version,
            "Duration (s)": duration,
            "Error": error_msg,
            "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        })
        
    def generate_report(self):
        df = pd.DataFrame(self.results)
        df.to_excel(self.filepath, index=False)
        print(f"Appium report generated: {self.filepath}")
