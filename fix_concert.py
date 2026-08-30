import re
import os

path = 'composeApp/src/commonMain/kotlin/ConcertView.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import
if 'import androidx.compose.runtime.saveable.rememberSaveable' not in content:
    content = content.replace('import androidx.compose.runtime.*', 'import androidx.compose.runtime.*\nimport androidx.compose.runtime.saveable.rememberSaveable')

# Replace the block
old_block = """    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DarkBackground, DarkPanel)
                )
            )
    ) {
    // Tablets (>=600dp) start expanded; phones start collapsed to maximize mixer space
    var isKeyboardVisible by remember { mutableStateOf(maxWidth >= 600.dp) }"""

new_block = """    // Tablets (>=600dp) start expanded; phones start collapsed to maximize mixer space
    var isKeyboardVisible by rememberSaveable { mutableStateOf(true) }

    // Use BoxWithConstraints to detect screen width (KMP-safe, no LocalConfiguration needed)
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DarkBackground, DarkPanel)
                )
            )
    ) {
        LaunchedEffect(Unit) {
            isKeyboardVisible = maxWidth >= 600.dp
        }"""

if old_block in content:
    content = content.replace(old_block, new_block)
else:
    print("Could not find the target block to replace.")

with open(path, 'w', encoding='utf-8', newline='\n') as f:
    f.write(content)
