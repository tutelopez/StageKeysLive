import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_logic = """                        if (active != null && newPatchName.isNotBlank()) {
                            val program = newPatchProgram.toIntOrNull() ?: 0
                            val updatedPatches = if (patchToEdit != null) {
                                active.patches.map { if (it.id == patchToEdit!!.id) it.copy(name = newPatchName, category = newPatchCategory, programNumber = program, description = newPatchDescription) else it }
                            } else {
                                val newPatch = PatchState(newPatchName, newPatchCategory, program, newPatchDescription)
                                active.patches + newPatch
                            }"""

new_logic = """                        if (active != null && newPatchName.isNotBlank()) {
                            val program = newPatchProgram.toIntOrNull() ?: 0
                            val updatedPatches = if (patchToEdit != null) {
                                active.patches.map { if (it.id == patchToEdit!!.id) it.copy(name = newPatchName, category = newPatchCategory, programNumber = program, description = newPatchDescription) else it }
                            } else {
                                val currentSnapshot = active.channels.map { ch ->
                                    PatchChannelSnapshot(
                                        channelId = ch.id,
                                        sf2Name = ch.sf2Name,
                                        sf2Path = ch.sf2Path,
                                        volume = ch.volume,
                                        isMuted = ch.isMuted,
                                        isSoloed = ch.isSoloed,
                                        keyRangeStart = ch.keyRangeStart,
                                        keyRangeEnd = ch.keyRangeEnd,
                                        colorHex = ch.colorHex
                                    )
                                }
                                val newPatch = PatchState(newPatchName, newPatchCategory, program, newPatchDescription, channelsSnapshot = currentSnapshot)
                                active.patches + newPatch
                            }"""

content = content.replace(old_logic, new_logic)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
