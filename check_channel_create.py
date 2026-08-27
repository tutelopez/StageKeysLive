import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Let's see how new channels get their default color
match = re.search(r'ChannelState\(.*?colorHex.*?=.*?\"(.*?)\".*?\)', content, re.DOTALL)
if match:
    print("Found ChannelState constructor:")
    print(match.group(0))

