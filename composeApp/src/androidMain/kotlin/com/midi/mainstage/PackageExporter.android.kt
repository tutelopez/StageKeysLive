package com.midi.mainstage

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
actual fun PackageExporter(
    concertToExport: Concert?,
    patchToExport: PatchState?,
    onExportComplete: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(concertToExport, patchToExport) {
        if (concertToExport == null && patchToExport == null) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val isConcert = concertToExport != null
                val fileName = if (isConcert) ".skconcert" else ".skpatch"
                
                // Use cache dir for temporary zip creation
                val zipFile = File(context.cacheDir, fileName)
                
                ZipOutputStream(FileOutputStream(zipFile)).use { zout ->
                    // 1. Write Manifest
                    val manifestJson = JSONObject()
                    manifestJson.put("type", if (isConcert) "concert" else "patch")
                    manifestJson.put("version", "1.0")
                    
                    zout.putNextEntry(ZipEntry("manifest.json"))
                    zout.write(manifestJson.toString().toByteArray())
                    zout.closeEntry()

                    // 2. Write Data JSON & collect soundfonts
                    val sf2Paths = mutableSetOf<String>()
                    
                    if (isConcert) {
                        val concert = concertToExport!!
                        val updatedChannels = concert.channels.map { ch ->
                            if (ch.sf2Path != null) {
                                val sf2Name = File(ch.sf2Path).name
                                sf2Paths.add(ch.sf2Path)
                                ch.copy(sf2Path = "soundfonts/$sf2Name")
                            } else {
                                ch
                            }
                        }
                        val updatedPatches = concert.patches.map { p ->
                            val updatedSnapshots = p.channelsSnapshot.map { snap ->
                                if (snap.sf2Path != null) {
                                    val sf2Name = File(snap.sf2Path).name
                                    sf2Paths.add(snap.sf2Path)
                                    snap.copy(sf2Path = "soundfonts/$sf2Name")
                                } else snap
                            }
                            p.copy(channelsSnapshot = updatedSnapshots)
                        }
                        
                        val jsonConcert = concert.copy(channels = updatedChannels, patches = updatedPatches)
                        val concertJsonStr = ConcertSerializer.serialize(listOf(jsonConcert))
                        zout.putNextEntry(ZipEntry("concert.json"))
                        zout.write(concertJsonStr.toByteArray())
                        zout.closeEntry()
                    } else {
                        val patch = patchToExport!!
                        
                        val patchObj = JSONObject()
                        patchObj.put("id", patch.id)
                        patchObj.put("name", patch.name)
                        patchObj.put("category", patch.category)
                        patchObj.put("programNumber", patch.programNumber)
                        patchObj.put("description", patch.description)
                        
                        val snapsArray = JSONArray()
                        patch.channelsSnapshot.forEach { snap ->
                            val snapObj = JSONObject()
                            snapObj.put("channelId", snap.channelId)
                            snapObj.put("sf2Name", snap.sf2Name)
                            if (snap.sf2Path != null) {
                                val sf2Name = File(snap.sf2Path).name
                                sf2Paths.add(snap.sf2Path)
                                snapObj.put("sf2Path", "soundfonts/$sf2Name")
                            }
                            snapObj.put("volume", snap.volume.toDouble())
                            snapObj.put("isMuted", snap.isMuted)
                            snapObj.put("isSoloed", snap.isSoloed)
                            snapObj.put("keyRangeStart", snap.keyRangeStart)
                            snapObj.put("keyRangeEnd", snap.keyRangeEnd)
                            snapObj.put("colorHex", snap.colorHex)
                            snapsArray.put(snapObj)
                        }
                        patchObj.put("channelsSnapshot", snapsArray)
                        
                        zout.putNextEntry(ZipEntry("patch.json"))
                        zout.write(patchObj.toString().toByteArray())
                        zout.closeEntry()
                    }

                    // 3. Write SoundFonts
                    sf2Paths.forEach { absPath ->
                        val sf2File = File(absPath)
                        if (sf2File.exists()) {
                            zout.putNextEntry(ZipEntry("soundfonts/"))
                            FileInputStream(sf2File).use { input ->
                                input.copyTo(zout)
                            }
                            zout.closeEntry()
                        }
                    }
                }

                // Share via Intent
                val uri: Uri = FileProvider.getUriForFile(context, ".fileprovider", zipFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                context.startActivity(Intent.createChooser(shareIntent, "Exportar paquete"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        onExportComplete()
    }
}
