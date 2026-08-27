import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Add imports for Tabler Icons and Theme
imports = """import compose.icons.TablerIcons
import compose.icons.tablericons.*
import com.midi.mainstage.*"""

content = content.replace("import androidx.compose.material.icons.filled.*", "import androidx.compose.material.icons.filled.*\n" + imports)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
