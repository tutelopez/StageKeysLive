import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Find where StageKeysTheme is applied
idx = content.find("StageKeysTheme")
print(content[idx-200:idx+300])
