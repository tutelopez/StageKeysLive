import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# find listOf("#00D2FF"
idx = content.find('listOf("#00D2FF"')
if idx != -1:
    print(content[idx:idx+200])

