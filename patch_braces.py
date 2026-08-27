import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_text = """                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }                 }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {"""

new_text = """                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {"""

content = content.replace(old_text, new_text)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
