import io, os

# Check if AddChannel.kt, EmptyChannel.kt, MasterOutput.kt, MetronomeChannel.kt are still needed
# They are now all in Widgets.kt, so stub them out
for fname in ["AddChannel.kt", "EmptyChannel.kt", "MasterOutput.kt", "MetronomeChannel.kt"]:
    path = f"composeApp/src/commonMain/kotlin/{fname}"
    if os.path.exists(path):
        with io.open("composeApp/src/commonMain/kotlin/Widgets.kt", "r", encoding="utf-8") as f:
            widgets = f.read()
        funcName = fname.replace(".kt","")
        if f"fun {funcName}" in widgets:
            # Stub the old file
            with io.open(path, "w", encoding="utf-8") as f:
                f.write(f"package com.midi.mainstage\n// {funcName} moved to Widgets.kt\n")
            print(f"Stubbed {fname}")
        else:
            print(f"WARNING: {funcName} NOT found in Widgets.kt")
