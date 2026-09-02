package com.midi.mainstage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AndroidAutoBackupController(
    private val context: Context
) : AutoBackupController {

    private val prefs = context.getSharedPreferences("AutoBackupPrefs", Context.MODE_PRIVATE)
    private var launcherCallback: (() -> Unit)? = null

    fun setLauncher(callback: (() -> Unit)?) {
        this.launcherCallback = callback
    }

    var currentState by mutableStateOf(readCurrentState())
        private set

    override val state: AutoBackupState
        get() = currentState

    fun refreshState() {
        currentState = readCurrentState()
    }

    private fun readCurrentState(): AutoBackupState {
        val uriStr = prefs.getString("backup_folder_uri", null)
        val name = prefs.getString("backup_folder_name", null)
        val lastTime = prefs.getLong("last_backup_time", -1L).takeIf { it > 0 }
        val isConfigured = !uriStr.isNullOrBlank()
        return AutoBackupState(
            folderUri = uriStr,
            folderName = name,
            lastBackupTimestamp = lastTime,
            isConfigured = isConfigured
        )
    }

    override fun requestSelectFolder() {
        launcherCallback?.invoke()
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri == null) return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: uri.lastPathSegment ?: "Carpeta SAF"

            prefs.edit()
                .putString("backup_folder_uri", uri.toString())
                .putString("backup_folder_name", folderName)
                .apply()

            refreshState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun clearFolder() {
        try {
            val uriStr = prefs.getString("backup_folder_uri", null)
            if (!uriStr.isNullOrBlank()) {
                val uri = Uri.parse(uriStr)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.edit().clear().apply()
        refreshState()
    }

    override suspend fun backupToFolder(concerts: List<Concert>): Result<Unit> = withContext(Dispatchers.IO) {
        val uriStr = prefs.getString("backup_folder_uri", null)
        if (uriStr.isNullOrBlank()) {
            return@withContext Result.success(Unit) // Silencioso si no está configurado
        }

        try {
            val treeUri = Uri.parse(uriStr)
            val folder = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(SecurityException("No se pudo acceder a la carpeta de respaldo."))

            if (!folder.exists() || !folder.canWrite()) {
                return@withContext Result.failure(SecurityException("Permisos de escritura no válidos o revocados en la carpeta de respaldo."))
            }

            // 1. Recolectar Soundfonts y actualizar rutas relativas
            val sf2Paths = mutableSetOf<String>()
            val updatedConcerts = concerts.map { concert ->
                val updatedChannels = concert.channels.map { ch ->
                    if (ch.sf2Path != null) {
                        val sf2File = File(ch.sf2Path)
                        if (sf2File.exists()) {
                            sf2Paths.add(ch.sf2Path)
                            ch.copy(sf2Path = "soundfonts/${sf2File.name}")
                        } else ch
                    } else ch
                }
                val updatedPatches = concert.patches.map { p ->
                    val updatedSnapshots = p.channelsSnapshot.map { snap ->
                        if (snap.sf2Path != null) {
                            val sf2File = File(snap.sf2Path)
                            if (sf2File.exists()) {
                                sf2Paths.add(snap.sf2Path)
                                snap.copy(sf2Path = "soundfonts/${sf2File.name}")
                            } else snap
                        } else snap
                    }
                    p.copy(channelsSnapshot = updatedSnapshots)
                }
                concert.copy(channels = updatedChannels, patches = updatedPatches)
            }

            // 2. Generar el archivo ZIP temporal en caché
            val tempZip = File(context.cacheDir, "temp_backup_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(tempZip)).use { zout ->
                // Manifest
                val manifest = JSONObject().apply {
                    put("type", "full_backup")
                    put("version", "1.0")
                    put("concertsCount", updatedConcerts.size)
                    put("timestamp", System.currentTimeMillis())
                }
                zout.putNextEntry(ZipEntry("manifest.json"))
                zout.write(manifest.toString().toByteArray())
                zout.closeEntry()

                // Concerts JSON
                val concertsJsonStr = ConcertSerializer.serialize(updatedConcerts)
                zout.putNextEntry(ZipEntry("concerts.json"))
                zout.write(concertsJsonStr.toByteArray())
                zout.closeEntry()

                // SoundFonts
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

            // 3. Borrar respaldos anteriores para no saturar Google Drive / Almacenamiento
            try {
                folder.listFiles().forEach { f ->
                    if (f.name?.startsWith("StageKeysBackup_") == true && f.name?.endsWith(".zip") == true) {
                        f.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore failure to delete old files
            }

            // 4. Crear el archivo nuevo en DocumentFile
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dateStr = dateFormat.format(Date())
            val targetFileName = "StageKeysBackup_${dateStr}.zip"

            val targetDoc = folder.createFile("application/zip", targetFileName)
                ?: return@withContext Result.failure(IOException("No se pudo crear el archivo $targetFileName en la carpeta seleccionada."))

            context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                FileInputStream(tempZip).use { input ->
                    input.copyTo(out)
                }
            } ?: return@withContext Result.failure(IOException("No se pudo escribir en el archivo de respaldo."))

            tempZip.delete()

            // 5. Guardar timestamp del último respaldo
            val now = System.currentTimeMillis()
            prefs.edit().putLong("last_backup_time", now).apply()
            refreshState()

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

@Composable
actual fun rememberAutoBackupController(): AutoBackupController {
    val context = LocalContext.current
    val controller = remember { AndroidAutoBackupController(context) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        controller.onFolderSelected(uri)
    }

    DisposableEffect(launcher) {
        controller.setLauncher {
            launcher.launch(null)
        }
        onDispose {
            controller.setLauncher(null)
        }
    }

    return controller
}
