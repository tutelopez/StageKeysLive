import io

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_block = """                        onBack = {
                            screenState = ScreenState.DASHBOARD
                        }"""

new_block = """                        onBack = {
                            synth.allNotesOff()
                            screenState = ScreenState.DASHBOARD
                        }"""

content = content.replace(old_block, new_block)

with io.open(filepath, "w", encoding="utf-8") as f:
    f.write(content)

