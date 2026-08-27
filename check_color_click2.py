import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("it.copy(colorHex = hexColor)")
if idx != -1:
    print(content[idx:idx+400])

