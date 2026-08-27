import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Check what wraps everything, look for MaterialTheme or StageKeysTheme
idx = content.find("MaterialTheme(")
print("MaterialTheme at:", idx)
idx2 = content.find("StageKeysTheme {")
print("StageKeysTheme at:", idx2)

# Check screenState usage
idx3 = content.find("screenState")
if idx3 == -1:
    idx3 = content.find("currentScreen")
print(content[idx3:idx3+200])
