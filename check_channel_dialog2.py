import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

for m in re.finditer(r'showChannelSettingsDialog \?\..{0,2500}', content, flags=re.DOTALL):
    print(m.group(0))
    break
