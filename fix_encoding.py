import os
import glob

# Mapping of corrupted ISO-8859-1 strings to correct UTF-8 characters
replacements = {
    "â‚¹": "₹",
    "â€¢": "•",
    "â˜…": "★",
    "âœ”": "✔",
    "â˜•": "☕",
    "ðŸ ”": "🍔",
    "ðŸ›‹ï¸ ": "🛋️",
    "âœˆï¸ ": "✈️",
    "â­ ": "⭐"
}

directory = r"c:\Users\santh\AndroidStudioProjects\AeroAssistAI\app\src\main\java\com\aeroassist\ai"

files_updated = 0
for filepath in glob.glob(os.path.join(directory, "*.java")):
    with open(filepath, 'r', encoding='utf-8') as file:
        content = file.read()
    
    modified = False
    for corrupted, correct in replacements.items():
        if corrupted in content:
            content = content.replace(corrupted, correct)
            modified = True
            
    if modified:
        with open(filepath, 'w', encoding='utf-8') as file:
            file.write(content)
        files_updated += 1
        print(f"Fixed: {os.path.basename(filepath)}")

print(f"Total files updated: {files_updated}")
