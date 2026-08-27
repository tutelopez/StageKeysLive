import io
import re

filepath = "composeApp/src/commonMain/kotlin/Dashboard.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("Column(")
if idx != -1:
    print(content[idx:idx+1500])

