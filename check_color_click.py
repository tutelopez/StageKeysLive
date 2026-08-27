import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

for m in re.finditer(r'\.clickable \{.*?val active = activeConcert.*?\}', content, flags=re.DOTALL):
    print(m.group(0))

