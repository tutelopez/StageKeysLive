import io
import re

filepath = "composeApp/src/commonMain/kotlin/Persistence.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("fun serialize(")
if idx != -1:
    print(content[idx:idx+2500])
