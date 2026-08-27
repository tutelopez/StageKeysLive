import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix parenthesis at line 1536
old_text = """                        fontWeight = FontWeight.Bold
                           IconButton("""
new_text = """                        fontWeight = FontWeight.Bold
                    )
                    IconButton("""

content = content.replace(old_text, new_text)

# Fix imports
if "import androidx.compose.material.icons.filled.KeyboardArrowUp" not in content:
    content = content.replace(
        "import androidx.compose.material.icons.filled.Add",
        "import androidx.compose.material.icons.filled.Add\nimport androidx.compose.material.icons.filled.KeyboardArrowUp\nimport androidx.compose.material.icons.filled.KeyboardArrowDown"
    )

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
