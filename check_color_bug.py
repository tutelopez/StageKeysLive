import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Let's check how parseColorHex is defined.
match = re.search(r'fun parseColorHex.*?}', content, re.DOTALL)
if match:
    # grab the next few lines too since regex might have stopped at the first brace
    idx = content.find("fun parseColorHex")
    print(content[idx:idx+500])

