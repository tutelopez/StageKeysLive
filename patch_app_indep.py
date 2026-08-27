import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Insert updateChannelsAndPatchSnapshot helper after applyPatch definition
insert_point = """    val applyPatch = { patchIndex: Int ->"""
helper = """    val updateChannelsAndPatchSnapshot = { newChannels: List<ChannelStripState> ->
        val concert = activeConcert
        if (concert != null) {
            val updatedPatches = if (selectedPatchIndex in concert.patches.indices) {
                concert.patches.mapIndexed { idx, patch ->
                    if (idx == selectedPatchIndex) {
                        val newSnapshot = newChannels.map { ch ->
                            PatchChannelSnapshot(
                                channelId = ch.id, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                                volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                                keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd,
                                colorHex = ch.colorHex
                            )
                        }
                        patch.copy(channelsSnapshot = newSnapshot)
                    } else patch
                }
            } else concert.patches

            val updatedConcert = concert.copy(channels = newChannels, patches = updatedPatches, lastModified = System.currentTimeMillis())
            val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
            saveConcertsList(newList)
            activeConcert = updatedConcert
        }
    }

    val applyPatch = { patchIndex: Int ->"""
content = content.replace(insert_point, helper)

# 2. Update applyPatch to rebuild channels from snapshot completely, 
# and if snapshot is empty, just keep concert.channels (for old patches).
old_apply = """            val patch = concert.patches[patchIndex]
            if (patch.channelsSnapshot.isNotEmpty()) {
                val updatedChannels = concert.channels.map { ch ->
                    val snap = patch.channelsSnapshot.find { it.channelId == ch.id }
                    if (snap != null) {
                        ch.copy(
                            sf2Name = snap.sf2Name,
                            sf2Path = snap.sf2Path,
                            volume = snap.volume,
                            isMuted = snap.isMuted,
                            isSoloed = snap.isSoloed,
                            keyRangeStart = snap.keyRangeStart,
                            keyRangeEnd = snap.keyRangeEnd,
                            colorHex = snap.colorHex
                        )
                    } else {
                        ch
                    }
                }"""

new_apply = """            val patch = concert.patches[patchIndex]
            if (patch.channelsSnapshot.isNotEmpty()) {
                val updatedChannels = patch.channelsSnapshot.map { snap ->
                    ChannelStripState(
                        id = snap.channelId,
                        sf2Name = snap.sf2Name,
                        sf2Path = snap.sf2Path,
                        volume = snap.volume,
                        isMuted = snap.isMuted,
                        isSoloed = snap.isSoloed,
                        keyRangeStart = snap.keyRangeStart,
                        keyRangeEnd = snap.keyRangeEnd,
                        colorHex = snap.colorHex
                    )
                }"""
content = content.replace(old_apply, new_apply)

# 3. Replace all the manual concert updates with the helper
# Target 1: MidiTarget.ChannelVolume
old_midi_vol = """                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(volume = floatValue)
                                activeConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())"""
new_midi_vol = """                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(volume = floatValue)
                                updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_midi_vol, new_midi_vol)

# Target 2: MidiTarget.ChannelMute
old_midi_mute = """                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(isMuted = toggle)
                                activeConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())"""
new_midi_mute = """                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(isMuted = toggle)
                                updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_midi_mute, new_midi_mute)

# Target 3: onVolumeChange
old_on_vol = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(volume = vol) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })"""
new_on_vol = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(volume = vol) else it
                        }
                        updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_on_vol, new_on_vol)

# Target 4: onMuteToggle
old_on_mute = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isMuted = !it.isMuted) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })"""
new_on_mute = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isMuted = !it.isMuted) else it
                        }
                        updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_on_mute, new_on_mute)

# Target 5: onSoloToggle
old_on_solo = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isSoloed = !it.isSoloed) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })"""
new_on_solo = """                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isSoloed = !it.isSoloed) else it
                        }
                        updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_on_solo, new_on_solo)

# Target 6: onAddChannelClick
old_on_add = """                            val updatedChannels = concert.channels + newChannel
                            val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                            activeConcert = updatedConcert
                            saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })"""
new_on_add = """                            val updatedChannels = concert.channels + newChannel
                            updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_on_add, new_on_add)

# Target 7: Sf2FilePicker
old_sf2 = """                            val updatedChannels = active.channels.map {
                                if (it.id == channel.id) it.copy(sf2Name = sf2Name, sf2Path = path) else it
                            }
                            val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                            val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                            saveConcertsList(newList)
                            activeConcert = updatedConcert
                            showChannelSettingsDialog = updatedConcert.channels.find { it.id == channel.id }"""
new_sf2 = """                            val updatedChannels = active.channels.map {
                                if (it.id == channel.id) it.copy(sf2Name = sf2Name, sf2Path = path) else it
                            }
                            updateChannelsAndPatchSnapshot(updatedChannels)
                            showChannelSettingsDialog = activeConcert?.channels?.find { it.id == channel.id }"""
content = content.replace(old_sf2, new_sf2)

# Target 8: Dialog - Rename Channel sf2Name
old_rename = """                                            val updatedChannels = active.channels.map {
                                                if (it.id == chState.id) it.copy(sf2Name = newName) else it
                                            }
                                            val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                            val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                                            saveConcertsList(newList)
                                            activeConcert = updatedConcert
                                            showChannelSettingsDialog = updatedConcert.channels.find { it.id == chState.id }"""
new_rename = """                                            val updatedChannels = active.channels.map {
                                                if (it.id == chState.id) it.copy(sf2Name = newName) else it
                                            }
                                            updateChannelsAndPatchSnapshot(updatedChannels)
                                            showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }"""
content = content.replace(old_rename, new_rename)

# Target 9: Dialog - Delete Channel
old_delete = """                                val updatedChannels = active.channels.filter { it.id != chState.id }
                                val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                                saveConcertsList(newList)
                                activeConcert = updatedConcert
                                showChannelSettingsDialog = null"""
new_delete = """                                val updatedChannels = active.channels.filter { it.id != chState.id }
                                updateChannelsAndPatchSnapshot(updatedChannels)
                                showChannelSettingsDialog = null"""
content = content.replace(old_delete, new_delete)

# Target 10: SettingsTab.SPLIT_ZONES
# Since I made it update the snapshot explicitly here earlier, I can simplify it back to just using the helper!
old_split = """                                                val updatedChannels = concert.channels.map {
                                                    if (it.id == channelId) it.copy(keyRangeStart = start, keyRangeEnd = end) else it
                                                }

                                                val updatedPatches = if (selectedPatchIndex in concert.patches.indices) {
                                                    concert.patches.mapIndexed { idx, patch ->
                                                        if (idx == selectedPatchIndex) {
                                                            val existingSnapshot = patch.channelsSnapshot
                                                            val newSnapshot = if (existingSnapshot.isEmpty()) {
                                                                updatedChannels.map { ch ->
                                                                    PatchChannelSnapshot(
                                                                        channelId = ch.id, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                                                                        volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                                                                        keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd,
                                                                        colorHex = ch.colorHex
                                                                    )
                                                                }
                                                            } else {
                                                                val existingChannelIds = existingSnapshot.map { it.channelId }.toSet()
                                                                val mergedSnapshot = existingSnapshot.toMutableList()
                                                                
                                                                updatedChannels.forEach { ch ->
                                                                    if (ch.id !in existingChannelIds) {
                                                                        mergedSnapshot.add(
                                                                            PatchChannelSnapshot(
                                                                                channelId = ch.id, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                                                                                volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                                                                                keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd,
                                                                                colorHex = ch.colorHex
                                                                            )
                                                                        )
                                                                    }
                                                                }
                                                                
                                                                mergedSnapshot.map { snap ->
                                                                    if (snap.channelId == channelId) snap.copy(keyRangeStart = start, keyRangeEnd = end) else snap
                                                                }
                                                            }
                                                            patch.copy(channelsSnapshot = newSnapshot)
                                                        } else {
                                                            patch
                                                        }
                                                    }
                                                } else {
                                                    concert.patches
                                                }

                                                val updatedConcert = concert.copy(channels = updatedChannels, patches = updatedPatches, lastModified = System.currentTimeMillis())
                                                val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                                                saveConcertsList(newList)
                                                activeConcert = updatedConcert"""

new_split = """                                                val updatedChannels = concert.channels.map {
                                                    if (it.id == channelId) it.copy(keyRangeStart = start, keyRangeEnd = end) else it
                                                }
                                                updateChannelsAndPatchSnapshot(updatedChannels)"""
content = content.replace(old_split, new_split)

# Target 11: showAddPatchDialog - Make new patch have a default snapshot instead of copying previous
old_add_patch = """                            val updatedPatches = if (patchToEdit != null) {
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

new_add_patch = """                            val updatedPatches = if (patchToEdit != null) {
                                active.patches.map { if (it.id == patchToEdit!!.id) it.copy(name = newPatchName, category = newPatchCategory, programNumber = program, description = newPatchDescription) else it }
                            } else {
                                val defaultSnapshot = listOf(
                                    PatchChannelSnapshot(
                                        channelId = 1,
                                        sf2Name = "Sin Asignar",
                                        sf2Path = null,
                                        volume = 0.8f,
                                        isMuted = false,
                                        isSoloed = false,
                                        keyRangeStart = 0,
                                        keyRangeEnd = 127,
                                        colorHex = "#00D2FF"
                                    )
                                )
                                val newPatch = PatchState(newPatchName, newPatchCategory, program, newPatchDescription, channelsSnapshot = defaultSnapshot)
                                active.patches + newPatch
                            }"""
content = content.replace(old_add_patch, new_add_patch)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
