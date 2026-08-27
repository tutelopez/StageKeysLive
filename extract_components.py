import io

def get_block(text, start_idx):
    idx = start_idx
    while idx < len(text) and text[idx] != '{':
        idx += 1
    if idx == len(text):
        return None, None
    
    braces = 1
    idx += 1
    while idx < len(text) and braces > 0:
        if text[idx] == '{':
            braces += 1
        elif text[idx] == '}':
            braces -= 1
        idx += 1
    return text[start_idx:idx], idx

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

def extract_and_remove(func_name, filename):
    global content
    idx = content.find(f"fun {func_name}(")
    if idx == -1: return
    composable_idx = content.rfind("@Composable", 0, idx)
    block, end_idx = get_block(content, composable_idx)
    if not block: return
    
    # Save to file
    with io.open(f"composeApp/src/commonMain/kotlin/{filename}", "w", encoding="utf-8") as out:
        out.write("package com.midi.mainstage\n\n")
        out.write("import androidx.compose.foundation.*\nimport androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.shape.*\nimport androidx.compose.material3.*\nimport androidx.compose.runtime.*\nimport androidx.compose.ui.*\nimport androidx.compose.ui.graphics.*\nimport androidx.compose.ui.text.font.*\nimport androidx.compose.ui.unit.*\nimport compose.icons.TablerIcons\nimport compose.icons.tablericons.*\nimport androidx.compose.ui.Alignment\n\n")
        out.write(block)
    
    content = content.replace(block, "")

extract_and_remove("ConcertViewScreen", "ConcertView.kt")
extract_and_remove("ChannelStripItem", "ChannelStrip.kt")
extract_and_remove("VolumeFader", "Widgets.kt") # We will manually append others to Widgets.kt
extract_and_remove("LevelMeter", "LevelMeter.kt")
extract_and_remove("AddChannelButton", "AddChannel.kt")
extract_and_remove("EmptyChannelPlaceholder", "EmptyChannel.kt")
extract_and_remove("MasterOutputChannelItem", "MasterOutput.kt")
extract_and_remove("MetronomeChannelItem", "MetronomeChannel.kt")
extract_and_remove("DashboardScreen", "Dashboard.kt")

with io.open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
