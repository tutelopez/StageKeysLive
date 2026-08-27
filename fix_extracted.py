import os
import re
import io

def fix_file(filename, fixes):
    path = f"composeApp/src/commonMain/kotlin/{filename}"
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # Add imports
    imports = "\nimport androidx.compose.ui.input.pointer.*\nimport androidx.compose.foundation.gestures.*\nimport androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.Icons\nimport androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.geometry.Offset\nimport androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.LazyColumn\n"
    if "import androidx.compose.ui.input.pointer.*" not in content:
        content = content.replace("import androidx.compose.ui.Alignment\n", f"import androidx.compose.ui.Alignment\n{imports}")

    for old, new in fixes:
        content = content.replace(old, new)
        
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)

# Fixes for each file
fix_file("Dashboard.kt", [
    ("@Composable\nfun DashboardScreen(", "import androidx.compose.foundation.lazy.items\nimport androidx.compose.foundation.lazy.LazyColumn\n@Composable\nfun DashboardScreen(")
])

fix_file("LevelMeter.kt", [("NeonGreen", "AccentNeonGreen")])
fix_file("MasterOutput.kt", [("MainstageBlue", "AccentSky")])
fix_file("MetronomeChannel.kt", [("NeonGreen", "AccentNeonGreen")])
fix_file("Widgets.kt", [("NeonGreen", "AccentNeonGreen")])

