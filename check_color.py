import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Look for parseColorHex
match = re.search(r'fun\s+parseColorHex.*?}', content, re.DOTALL)
if match:
    print(match.group(0))
else:
    # Find occurrences of parseColorHex
    print("parseColorHex not found as a function definition.")
    occurrences = re.findall(r'.{0,50}parseColorHex.{0,50}', content)
    for o in occurrences:
        print(o.strip())

