import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find('listOf("#00D2FF"')
if idx != -1:
    print(content[idx:idx+400])

idx2 = content.find("Color.White")
if idx2 != -1:
    print(content[idx2:idx2+400])
