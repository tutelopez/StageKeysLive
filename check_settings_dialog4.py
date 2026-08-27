import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

idx = content.find("SettingsTab.MIDI_MAP")
while idx != -1:
    print("---")
    print(content[max(0, idx-200):idx+500])
    idx = content.find("SettingsTab.MIDI_MAP", idx + 10)
