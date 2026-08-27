import io
import os

def fix_file(filename, fixes):
    path = f"composeApp/src/commonMain/kotlin/{filename}"
    if not os.path.exists(path): return
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    for old, new in fixes:
        content = content.replace(old, new)
        
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)

fix_file("ConcertView.kt", [("AccentAccentNeonGreen", "AccentNeonGreen")])
fix_file("AddChannel.kt", [("NeonGreen", "AccentNeonGreen")])
fix_file("App.kt", [
    ("import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.animation.core.Animatable\n", ""),
    ("import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.animation.core.Animatable\n", "import androidx.compose.ui.draw.clip\n")
])

def add_icons(filename):
    path = f"composeApp/src/commonMain/kotlin/{filename}"
    if not os.path.exists(path): return
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    if "import androidx.compose.material.icons" not in content:
        content = content.replace("import androidx", "import androidx.compose.material.icons.filled.*\nimport androidx.compose.material.icons.Icons\nimport androidx", 1)
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(content)

add_icons("AddChannel.kt")
add_icons("ChannelStrip.kt")
