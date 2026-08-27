import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("val hexColor = ")
if idx != -1:
    print(content[idx-100:idx+400])

