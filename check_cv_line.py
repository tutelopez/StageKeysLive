import io

# ConcertView line 474 error - Expression weight of type Float cannot be invoked as a function
# Check what is around that area
with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

# Print lines around 470-480
for i, line in enumerate(lines[465:490], start=466):
    print(f"{i}: {line}", end="")
