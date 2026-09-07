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
                if (isConcert) {
                    val concert = concertToExport!!
                    val fileName = "${concert.name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().ifEmpty { "Concert" }}.skconcert"
                    val zipFile = File(context.cacheDir, fileName)
                    
                    ZipOutputStream(FileOutputStream(zipFile)).use { zout ->
                        // 1. Write Manifest
                        val manifestJson = JSONObject()
                        manifestJson.put("type", "concert")
                        manifestJson.put("version", "1.0")
                        
                        zout.putNextEntry(ZipEntry("manifest.json"))
                        zout.write(manifestJson.toString().toByteArray())
                        zout.closeEntry()

                        // 2. Write Data JSON & collect soundfonts
                        val sf2Paths = mutableSetOf<String>()
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

                        // 3. Write SoundFonts
                        sf2Paths.forEach { absPath ->
                            val sf2File = File(absPath)
                            if (sf2File.exists()) {
                                zout.putNextEntry(ZipEntry("soundfonts/${sf2File.name}"))
                                FileInputStream(sf2File).use { input ->
                                    input.copyTo(zout)
                                }
                                zout.closeEntry()
                            }
                        }
                    }

                    // Share via Intent
                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Concierto StageKeysLive: ${concert.name}")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Exportar concierto"))
                } else {
                    val patch = patchToExport!!
                    val cleanName = patch.name.replace(Regex("[^a-zA-Z0-9_\\-\\s]"), "").trim().ifEmpty { "Patch" }
                    val jsonFile = File(context.cacheDir, "StageKeys_${cleanName}.skpatch")
                    
                    val patchJsonStr = SinglePatchSerializer.serialize(patch)
                    jsonFile.writeText(patchJsonStr, Charsets.UTF_8)

                    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", jsonFile)
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Patch StageKeysLive: ${patch.name}")
                        putExtra(Intent.EXTRA_TEXT, "Patch '${patch.name}' para StageKeysLive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Compartir patch: ${patch.name}"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Error al exportar: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        
        onExportComplete()
    }
}
