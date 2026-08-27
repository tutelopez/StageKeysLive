import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("fun ConcertViewScreen")
if idx != -1:
    print("ConcertViewScreen found in App.kt!")
else:
    print("Not found in App.kt")

