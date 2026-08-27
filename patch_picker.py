import os

filepath = "composeApp/src/androidMain/kotlin/com/midi/mainstage/SkPackagePicker.android.kt"
with open(filepath, "r", encoding="utf-8") as f:
    content = f.read()

# Fix Point 3 (Unique IDs for imported concerts/patches)
old_concert_id = """val finalConcert = concert.copy(
                            id = "concert_",
                            channels = updatedChannels,"""
                            
new_concert_id = """val finalConcert = concert.copy(
                            id = "concert_${System.currentTimeMillis()}_${(0..9999).random()}",
                            channels = updatedChannels,"""
                            
content = content.replace(old_concert_id, new_concert_id)

old_patch_id = """val finalPatch = PatchState(
                        id = "patch_",
                        name = pObj.getString("name"),"""
                        
new_patch_id = """val finalPatch = PatchState(
                        id = "patch_${System.currentTimeMillis()}_${(0..9999).random()}",
                        name = pObj.getString("name"),"""

content = content.replace(old_patch_id, new_patch_id)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
