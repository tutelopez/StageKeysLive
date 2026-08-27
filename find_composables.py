import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Find all lines with @Composable and the following fun name
composables = re.findall(r'@Composable\s*(?:@OptIn[^\n]*\n)*\s*fun\s+([A-Za-z0-9_]+)\(', content)
for c in composables:
    print(f"- {c}")

