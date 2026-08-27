import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add settingsOpenedFromConcert variable
old_state = """    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(SettingsTab.MIDI_MAP) }"""
    
new_state = """    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsOpenedFromConcert by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(SettingsTab.MIDI_MAP) }"""
    
content = content.replace(old_state, new_state)

# 2. Update onSettingsClick for Dashboard
old_dash_settings = """                onSettingsClick = { showSettingsDialog = true }"""
new_dash_settings = """                onSettingsClick = { 
                    settingsOpenedFromConcert = false
                    activeSettingsTab = SettingsTab.MIDI_MAP
                    showSettingsDialog = true 
                }"""

# Using count=1 because there are two occurrences
content = content.replace(old_dash_settings, new_dash_settings, 1)

# 3. Update onSettingsClick for Concert
old_concert_settings = """                    onSettingsClick = { showSettingsDialog = true },"""
new_concert_settings = """                    onSettingsClick = { 
                        settingsOpenedFromConcert = true
                        activeSettingsTab = SettingsTab.SPLIT_ZONES
                        showSettingsDialog = true 
                    },"""

content = content.replace(old_concert_settings, new_concert_settings)

# 4. Filter settings tabs based on settingsOpenedFromConcert
old_tabs = """                            val tabs = listOf(
                                SettingsTab.MIDI_MAP to "Mapear MIDI",
                                SettingsTab.SPLIT_ZONES to "Keyboard Zones",
                                SettingsTab.AUDIO to "Interfaces de Audio"
                            )
                            tabs.forEach { (tab, label) ->"""
                            
new_tabs = """                            val tabs = if (settingsOpenedFromConcert) {
                                listOf(SettingsTab.SPLIT_ZONES to "Keyboard Zones", SettingsTab.AUDIO to "Interfaces de Audio")
                            } else {
                                listOf(SettingsTab.MIDI_MAP to "Mapear MIDI", SettingsTab.SPLIT_ZONES to "Keyboard Zones", SettingsTab.AUDIO to "Interfaces de Audio")
                            }
                            tabs.forEach { (tab, label) ->"""

content = content.replace(old_tabs, new_tabs)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
