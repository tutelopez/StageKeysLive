import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

colors_pattern = r"// Theme Colors.*?val TextDark = Color\(0xFF7F7F8C\)"
content = re.sub(colors_pattern, "// Theme Colors now defined in Theme.kt", content, flags=re.DOTALL)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
