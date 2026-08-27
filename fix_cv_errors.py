import io

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Fix: line 612 - the lambda for onValueChange uses 'it' but should use explicit param
content = content.replace(
    "                        onValueChange = {\n                            coroutineScope.launch { pitchBend.snapTo(it) }\n                        },",
    "                        onValueChange = { newVal ->\n                            coroutineScope.launch { pitchBend.snapTo(newVal) }\n                        },"
)

# Also fix the invalid color literal 0xFF6060808 (too many digits)
content = content.replace("Color(0xFF6060808)", "Color(0xFF606080)")

with io.open("composeApp/src/commonMain/kotlin/ConcertView.kt", "w", encoding="utf-8") as f:
    f.write(content)

print("Fixed ConcertView")

# Also check App.kt for duplicate OctaveButton private functions  
with io.open("composeApp/src/commonMain/kotlin/App.kt", "r", encoding="utf-8") as f:
    app = f.read()

count_pb = app.count("fun PitchBendWheel")
count_mod = app.count("fun ModulationWheel")
print(f"App.kt - PitchBendWheel: {count_pb}, ModulationWheel: {count_mod}")
