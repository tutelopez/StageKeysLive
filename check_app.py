import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("fun App(")
composable_idx = content.rfind("@Composable", 0, idx)
print(content[composable_idx:composable_idx+1000])

