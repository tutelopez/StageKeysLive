import io
import re

filepath = "composeApp/src/commonMain/kotlin/AudioEngine.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

print(content[:1500])

