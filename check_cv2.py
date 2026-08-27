import io
import re

filepath = "composeApp/src/commonMain/kotlin/ConcertView.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Let's see the main scaffold / surface layout
idx = content.find("Surface(")
if idx == -1:
    idx = content.find("Column(")
if idx != -1:
    print(content[idx:idx+1500])

