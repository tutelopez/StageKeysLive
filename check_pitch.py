import io

# Also fix the PitchBendWheel lambda issue (line 610: Unresolved reference 'it')
# In Widgets.kt PitchBendWheel uses onDrag { change, _ -> ... } but the variable name was 'it'
with io.open("composeApp/src/commonMain/kotlin/Widgets.kt", "r", encoding="utf-8") as f:
    content = f.read()

# Check the PitchBendWheel lambda
idx = content.find("fun PitchBendWheel")
print(content[idx:idx+800])
