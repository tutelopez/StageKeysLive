import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("var showChannelSettingsDialog")
if idx != -1:
    print("Found it!")

for m in re.finditer(r'var showChannelSettingsDialog.{0,1500}', content, flags=re.DOTALL):
    print(m.group(0))
    break
