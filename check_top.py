import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("enum class ScreenState")
end_idx = content.find("@Composable\nfun App(")

print(content[idx:end_idx])

