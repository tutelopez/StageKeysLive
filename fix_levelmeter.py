import io

# LevelMeter.kt - now moved into Widgets.kt, but file still exists so must be removed or emptied
# We check if LevelMeter is defined in Widgets.kt to avoid duplication
with io.open("composeApp/src/commonMain/kotlin/Widgets.kt", "r", encoding="utf-8") as f:
    widgets = f.read()

if "fun LevelMeter" in widgets:
    print("LevelMeter already in Widgets.kt")
    # Replace LevelMeter.kt with just package declaration (it is a stub now)
    with io.open("composeApp/src/commonMain/kotlin/LevelMeter.kt", "w", encoding="utf-8") as f:
        f.write("package com.midi.mainstage\n// LevelMeter moved to Widgets.kt\n")
    print("LevelMeter.kt stubbed out")
