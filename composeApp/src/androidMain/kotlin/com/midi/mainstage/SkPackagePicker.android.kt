package com.midi.mainstage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Composable
actual fun SkPackagePicker(show: Boolean, onPackageSelected: (Concert?, PatchState?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onPackageSelected(null, null)
            return@rememberLauncherForActivityResult
        }

        Thread {
            try {
                val contentResolver = context.contentResolver
                val baseFilesDir = context.filesDir
                val soundfontsDir = File(baseFilesDir, "soundfonts")
                if (!soundfontsDir.exists()) soundfontsDir.mkdirs()

                var type = ""
                var concertJsonStr: String? = null
                var patchJsonStr: String? = null

                contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name == "manifest.json") {
                                val manifestStr = zis.bufferedReader().readText()
                                val manifestObj = JSONObject(manifestStr)
                                type = manifestObj.optString("type", "")
                            } else if (entry.name == "concert.json") {
                                concertJsonStr = zis.bufferedReader().readText()
                            } else if (entry.name == "patch.json") {
                                patchJsonStr = zis.bufferedReader().readText()
                            } else if (entry.name.startsWith("soundfonts/") && !entry.isDirectory) {
                                val sf2Name = entry.name.substringAfter("soundfonts/")
                                val destFile = File(soundfontsDir, sf2Name)
                                if (!destFile.exists()) {
                                    FileOutputStream(destFile).use { out ->
                                        zis.copyTo(out)
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                if (type == "concert" && concertJsonStr != null) {
                    val list = ConcertSerializer.deserialize(concertJsonStr!!)
                    if (list.isNotEmpty()) {
                        val concert = list.first()
                        val updatedChannels = concert.channels.map { ch ->
                            if (ch.sf2Path != null && ch.sf2Path.startsWith("soundfonts/")) {
                                val sf2Name = ch.sf2Path.substringAfter("soundfonts/")
                                val absPath = File(soundfontsDir, sf2Name).absolutePath
                                ch.copy(sf2Path = absPath)
                            } else ch
                        }
                        val updatedPatches = concert.patches.map { p ->
                            val updatedSnapshots = p.channelsSnapshot.map { snap ->
                                if (snap.sf2Path != null && snap.sf2Path.startsWith("soundfonts/")) {
                                    val sf2Name = snap.sf2Path.substringAfter("soundfonts/")
                                    val absPath = File(soundfontsDir, sf2Name).absolutePath
                                    snap.copy(sf2Path = absPath)
                                } else snap
                            }
                            p.copy(channelsSnapshot = updatedSnapshots)
                        }
                        
                        val finalConcert = concert.copy(
                            id = "concert_${System.currentTimeMillis()}_${(0..9999).random()}",
                            channels = updatedChannels, 
                            patches = updatedPatches
                        )
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onPackageSelected(finalConcert, null)
                        }
                        return@Thread
                    }
                } else if (type == "patch" && patchJsonStr != null) {
                    val pObj = JSONObject(patchJsonStr!!)
                    
                    val snapsArray = pObj.optJSONArray("channelsSnapshot")
                    val parsedSnaps = mutableListOf<PatchChannelSnapshot>()
                    
                    if (snapsArray != null) {
                        for (i in 0 until snapsArray.length()) {
                            val sObj = snapsArray.getJSONObject(i)
                            val sfPathRaw = if (sObj.has("sf2Path") && !sObj.isNull("sf2Path")) sObj.getString("sf2Path") else null
                            var absPath = sfPathRaw
                            
                            if (sfPathRaw != null && sfPathRaw.startsWith("soundfonts/")) {
                                val sf2Name = sfPathRaw.substringAfter("soundfonts/")
                                absPath = File(soundfontsDir, sf2Name).absolutePath
                            }
                            
                            parsedSnaps.add(
                                PatchChannelSnapshot(
                                    channelId = sObj.getInt("channelId"),
                                    sf2Name = sObj.getString("sf2Name"),
                                    sf2Path = absPath,
                                    volume = sObj.getDouble("volume").toFloat(),
                                    isMuted = sObj.getBoolean("isMuted"),
                                    isSoloed = sObj.getBoolean("isSoloed"),
                                    keyRangeStart = sObj.getInt("keyRangeStart"),
                                    keyRangeEnd = sObj.getInt("keyRangeEnd"),
                                    colorHex = sObj.getString("colorHex")
                                )
                            )
                        }
                    }
                    
                    val finalPatch = PatchState(
                        id = "patch_${System.currentTimeMillis()}_${(0..9999).random()}",
                        name = pObj.getString("name"),
                        category = pObj.getString("category"),
                        programNumber = pObj.getInt("programNumber"),
                        description = pObj.getString("description"),
                        channelsSnapshot = parsedSnaps
                    )
                    
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onPackageSelected(null, finalPatch)
                    }
                    return@Thread
                }
                
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onPackageSelected(null, null)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onPackageSelected(null, null)
                }
            }
        }.start()
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        }
    }
}
