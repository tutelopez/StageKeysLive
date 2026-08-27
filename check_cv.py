import io

filepath = "composeApp/src/commonMain/kotlin/ConcertView.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    print(f.read()[:2000])

