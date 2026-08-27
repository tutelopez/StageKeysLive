import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Let's find anywhere colorHex is defined
for m in re.finditer(r'.{0,50}colorHex.{0,50}', content):
    print(m.group(0).strip().replace('\n', ' '))

