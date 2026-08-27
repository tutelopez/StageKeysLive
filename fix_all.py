import os
import io

def fix_file(filename, fixes):
    path = f"composeApp/src/commonMain/kotlin/{filename}"
    if not os.path.exists(path): return
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    for old, new in fixes:
        content = content.replace(old, new)
        
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(content)

def add_imports(filename, extra_imports):
    path = f"composeApp/src/commonMain/kotlin/{filename}"
    if not os.path.exists(path): return
    with io.open(path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # inject imports after package declaration
    if "import androidx" in content:
        content = content.replace("import androidx", extra_imports + "\nimport androidx", 1)
        with io.open(path, "w", encoding="utf-8") as f:
            f.write(content)

color_fixes = [
    ("MainstageBlue", "AccentSky"),
    ("NeonGreen", "AccentNeonGreen"),
    ("BrightOrange", "AccentWarmYellow")
]

fix_file("App.kt", color_fixes)
add_imports("App.kt", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.draw.clip\nimport androidx.compose.animation.core.Animatable\n")

fix_file("ChannelStrip.kt", color_fixes)
add_imports("ChannelStrip.kt", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.draw.clip\n")

fix_file("ConcertView.kt", color_fixes)
add_imports("ConcertView.kt", "import androidx.compose.animation.core.Animatable\nimport androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.draw.clip\n")

