import io
import re
import os

def redesign_file(filepath):
    if not os.path.exists(filepath): return
    with io.open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Colors
    color_map = {
        "DarkBackground": "MaterialTheme.colorScheme.background",
        "DarkPanel": "MaterialTheme.colorScheme.surface",
        "LightPanel": "MaterialTheme.colorScheme.surfaceVariant",
        "TextLight": "MaterialTheme.colorScheme.onSurface",
        "TextDark": "MaterialTheme.colorScheme.onSurfaceVariant",
        "AccentSky": "MaterialTheme.colorScheme.primary",
        "AccentNeonGreen": "MaterialTheme.colorScheme.tertiary",
        "AccentWarmYellow": "MaterialTheme.colorScheme.secondary",
        "Color.White": "MaterialTheme.colorScheme.onPrimary",
        "Color.Black": "MaterialTheme.colorScheme.background",
        "Color.Red": "MaterialTheme.colorScheme.error",
        "Color.Transparent": "Color.Transparent"
    }

    for old, new in color_map.items():
        # Match whole words, except when it's part of an import
        # Instead of regex we can just replace as long as they are distinct
        content = content.replace(f"color = {old}", f"color = {new}")
        content = content.replace(f"background({old}", f"background({new}")
        content = content.replace(f"tint = {old}", f"tint = {new}")
        content = content.replace(f"color: Color = {old}", f"color: Color = {new}")
        content = content.replace(f"containerColor = {old}", f"containerColor = {new}")
        content = content.replace(f"Color({old})", f"Color({new})") # if any
        # just replace them blindly except if they are inside import or declaration
        if old != "Color.White" and old != "Color.Black" and old != "Color.Red" and old != "Color.Transparent":
            content = re.sub(rf'\b{old}\b', new, content)

    # 2. Typography
    content = content.replace("fontFamily = FontFamily.Monospace,", "style = MaterialTheme.typography.titleMedium,")
    content = content.replace("fontSize = 10.sp", "style = MaterialTheme.typography.labelSmall")
    content = content.replace("fontSize = 11.sp", "style = MaterialTheme.typography.labelSmall")
    content = content.replace("fontSize = 12.sp", "style = MaterialTheme.typography.bodySmall")
    content = content.replace("fontSize = 14.sp", "style = MaterialTheme.typography.bodyMedium")
    content = content.replace("fontSize = 15.sp", "style = MaterialTheme.typography.titleMedium")
    content = content.replace("fontSize = 16.sp", "style = MaterialTheme.typography.titleLarge")
    content = content.replace("fontSize = 20.sp", "style = MaterialTheme.typography.headlineSmall")
    content = content.replace("fontWeight = FontWeight.Bold,", "")

    # 3. Shapes
    content = content.replace("RoundedCornerShape(6.dp)", "RoundedCornerShape(percent = 50)")
    content = content.replace("RoundedCornerShape(8.dp)", "RoundedCornerShape(percent = 50)")
    content = content.replace("RoundedCornerShape(4.dp)", "RoundedCornerShape(12.dp)")
    
    # 4. Icons
    icon_map = {
        "Icons.Default.ArrowBack": "TablerIcons.ArrowLeft",
        "Icons.Default.Add": "TablerIcons.Plus",
        "Icons.Default.Delete": "TablerIcons.Trash",
        "Icons.Default.Edit": "TablerIcons.Edit",
        "Icons.Default.Settings": "TablerIcons.Settings",
        "Icons.Default.PlayArrow": "TablerIcons.PlayerPlay",
        "Icons.Default.KeyboardArrowUp": "TablerIcons.ChevronUp",
        "Icons.Default.KeyboardArrowDown": "TablerIcons.ChevronDown",
        "Icons.Default.MoreVert": "TablerIcons.DotsVertical",
        "Icons.Default.Close": "TablerIcons.X",
        "Icons.Default.VolumeUp": "TablerIcons.Volume",
        "Icons.Default.VolumeOff": "TablerIcons.Volume3"
    }
    for old, new in icon_map.items():
        content = content.replace(old, new)
        
    with io.open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

files_to_fix = [
    "ConcertView.kt", "ChannelStrip.kt", "Widgets.kt", "LevelMeter.kt", 
    "AddChannel.kt", "EmptyChannel.kt", "MasterOutput.kt", "MetronomeChannel.kt"
]

for file in files_to_fix:
    redesign_file("composeApp/src/commonMain/kotlin/" + file)

print("Redesign applied.")
