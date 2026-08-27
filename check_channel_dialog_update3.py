import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("val active = activeConcert")
idx = content.find("val active = activeConcert", idx + 10)
if idx != -1:
    print(content[idx:idx+800])
