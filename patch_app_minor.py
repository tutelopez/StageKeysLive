import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_back = """                    onBackClick = { currentScreen = ScreenState.DASHBOARD },"""
new_back = """                    onBackClick = { 
                        stopConcert()
                        activeConcert = null
                        currentScreen = ScreenState.DASHBOARD 
                    },"""
content = content.replace(old_back, new_back)

old_tabs = """                            val tabs = if (settingsOpenedFromConcert) {
                                listOf(
                                    SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            } else {
                                listOf(
                                    SettingsTab.MIDI_MAP to "Mapear MIDI",
                                    SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            }"""
new_tabs = """                            val tabs = if (settingsOpenedFromConcert) {
                                listOf(
                                    SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            } else {
                                listOf(
                                    SettingsTab.MIDI_MAP to "Mapear MIDI",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            }"""
content = content.replace(old_tabs, new_tabs)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
