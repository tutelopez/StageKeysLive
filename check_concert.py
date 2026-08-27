import re
import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("fun ConcertViewScreen(")
print(content[idx:idx+1500])
