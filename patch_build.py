import os

filepath = "composeApp/build.gradle.kts"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_common = """        getByName("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
            }
        }"""
new_common = """        getByName("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(compose.components.uiToolingPreview)
                implementation("br.com.devsrsouza.compose.icons.jetbrains:tabler-icons:1.1.0")
            }
        }"""
content = content.replace(old_common, new_common)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
