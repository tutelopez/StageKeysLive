import io
import re

filepath = "composeApp/src/commonMain/kotlin/App.kt"
with io.open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

old_block = """                                            saveConcerts(newList)
                                            updateChannelsAndPatchSnapshot(updatedChannels)
                                        }"""

new_block = """                                            saveConcerts(newList)
                                            updateChannelsAndPatchSnapshot(updatedChannels)
                                            showChannelSettingsDialog = chState.copy(colorHex = hexColor)
                                        }"""

content = content.replace(old_block, new_block)

with io.open(filepath, "w", encoding="utf-8") as f:
    f.write(content)

