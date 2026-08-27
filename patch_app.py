import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix Point 4: Circular navigation and buttons
# Add nextPatch and previousPatch lambdas before the when(target) in onMappedCc

old_on_mapped_cc = """            onMappedCc = { target, floatValue ->
                triggerMidiFlash()
                when (target) {"""
                
new_on_mapped_cc = """            onMappedCc = { target, floatValue ->
                triggerMidiFlash()
                
                val handleNextPatch = {
                    val concert = activeConcert
                    if (concert != null && concert.patches.isNotEmpty()) {
                        val next = (selectedPatchIndex + 1) % concert.patches.size
                        applyPatch(next)
                    }
                }
                
                val handlePreviousPatch = {
                    val concert = activeConcert
                    if (concert != null && concert.patches.isNotEmpty()) {
                        val prev = (selectedPatchIndex - 1 + concert.patches.size) % concert.patches.size
                        applyPatch(prev)
                    }
                }
                
                when (target) {"""

content = content.replace(old_on_mapped_cc, new_on_mapped_cc)

old_next_patch = """                    is MidiTarget.NextPatch -> {
                        if (floatValue > 0f) {
                            val concert = activeConcert
                            if (concert != null) {
                                val next = (selectedPatchIndex + 1).coerceAtMost(concert.patches.size - 1)
                                if (next != selectedPatchIndex) applyPatch(next)
                            }
                        }
                    }"""

new_next_patch = """                    is MidiTarget.NextPatch -> {
                        if (floatValue > 0f) {
                            handleNextPatch()
                        }
                    }"""

content = content.replace(old_next_patch, new_next_patch)

old_prev_patch = """                    is MidiTarget.PreviousPatch -> {
                        if (floatValue > 0f) {
                            val concert = activeConcert
                            if (concert != null) {
                                val prev = (selectedPatchIndex - 1).coerceAtLeast(0)
                                if (prev != selectedPatchIndex) applyPatch(prev)
                            }
                        }
                    }"""

new_prev_patch = """                    is MidiTarget.PreviousPatch -> {
                        if (floatValue > 0f) {
                            handlePreviousPatch()
                        }
                    }"""

content = content.replace(old_prev_patch, new_prev_patch)

# Add the UI buttons
old_ui_buttons = """                    IconButton(
                        onClick = onImportPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("↓", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }"""

new_ui_buttons = """                    IconButton(
                        onClick = {
                            if (concert.patches.isNotEmpty()) {
                                val prev = (selectedPatchIndex - 1 + concert.patches.size) % concert.patches.size
                                onSelectPatch(prev)
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous patch", tint = TextDark, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    IconButton(
                        onClick = {
                            if (concert.patches.isNotEmpty()) {
                                val next = (selectedPatchIndex + 1) % concert.patches.size
                                onSelectPatch(next)
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next patch", tint = TextDark, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onImportPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Text("↓", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }"""

content = content.replace(old_ui_buttons, new_ui_buttons)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
