import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Check how App() composable starts
idx = content.find("fun App(")
print(content[idx:idx+400])
