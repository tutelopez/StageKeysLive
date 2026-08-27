import os

filepath = "composeApp/build.gradle.kts"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace('implementation("br.com.devsrsouza.compose.icons.jetbrains:tabler-icons:1.1.0")', 'implementation("br.com.devsrsouza.compose.icons:tabler-icons:1.1.0")')

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
