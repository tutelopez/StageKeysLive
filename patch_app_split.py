import os

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_logic = """                                          SplitKeyboardSettingsScreen(
                                              concert = concert,
                                              onUpdateRange = { channelId, start, end ->
                                                  val updatedChannels = concert.channels.map {
                                                      if (it.id == channelId) it.copy(keyRangeStart = start, keyRangeEnd = end) else it
                                                  }
                                                  val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                                  val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                                                  saveConcertsList(newList)
                                                  activeConcert = updatedConcert
                                              }
                                          )"""

new_logic = """                                          SplitKeyboardSettingsScreen(
                                              concert = concert,
                                              onUpdateRange = { channelId, start, end ->
                                                  val updatedChannels = concert.channels.map {
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
                                                  activeConcert = updatedConcert
                                              }
                                          )"""

content = content.replace(old_logic, new_logic)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
