import io

# ConcertView line 610: Unresolved reference 'it'
# This is in KeyboardPanel where we call PitchBendWheel with lambda 
# The issue is in the KeyboardPanel composable where the coroutineScope is received as param
# Let me check lines 600-625 in ConcertView

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines[595:630], start=596):
    print(f"{i}: {line}", end="")
