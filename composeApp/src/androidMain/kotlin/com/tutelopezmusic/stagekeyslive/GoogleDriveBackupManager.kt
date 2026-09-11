package com.tutelopezmusic.stagekeyslive

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val TAG = "GoogleDriveBackup"
private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
private const val FOLDER_NAME = "StageKeysLive Backups"
private const val MAX_BACKUPS_RETAINED = 10

class GoogleDriveBackupManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("GoogleDriveBackupPrefs", Context.MODE_PRIVATE)
    var onAuthRecoveryNeeded: ((android.content.Intent) -> Unit)? = null

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val accountPrefs = context.getSharedPreferences("google_account_prefs", Context.MODE_PRIVATE)
        val email = accountPrefs.getString("user_email", null)
            ?: com.google.android.gms.auth.api.signin.GoogleSignIn.getLastSignedInAccount(context)?.email
            ?: FirebaseAuth.getInstance().currentUser?.email
            ?: return@withContext null

        try {
            Log.i(TAG, "Requesting Drive OAuth token for $email")
            val token = GoogleAuthUtil.getToken(context, email, DRIVE_SCOPE)
            Log.i(TAG, "Drive OAuth token obtained successfully")
            token
        } catch (e: UserRecoverableAuthException) {
            Log.w(TAG, "User recoverable auth exception for Drive: ${e.message}")
            val intent = e.intent
            if (intent != null) {
                onAuthRecoveryNeeded?.invoke(intent) ?: run {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            throw IllegalStateException("Por favor autoriza el permiso de Google Drive en la pantalla de tu dispositivo y vuelve a intentar.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to obtain Drive OAuth token: ${e.message}", e)
            CrashReporter.recordException(e, "GoogleDriveGetToken")
            val msg = when {
                e.message?.contains("UnregisteredOnApiConsole", ignoreCase = true) == true ->
                    "Google Drive API no está habilitada o la huella SHA-1 de la app no está registrada en Google Cloud / Firebase Console."
                else -> "Error de autenticación con Google Drive: ${e.message}"
            }
            throw IllegalStateException(msg)
        }
    }

    fun invalidateToken(token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear Drive token: ${e.message}")
        }
    }

    suspend fun getOrCreateFolderId(token: String): String = withContext(Dispatchers.IO) {
        val cachedId = prefs.getString("drive_folder_id", null)
        if (!cachedId.isNullOrBlank()) {
            // Verify if cached folder exists and is valid
            try {
                val checkUrl = "https://www.googleapis.com/drive/v3/files/$cachedId?fields=id,trashed"
                val conn = (URL(checkUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(resp)
                    if (!json.optBoolean("trashed", false)) {
                        return@withContext cachedId
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cached folder check failed: ${e.message}")
            }
            prefs.edit().remove("drive_folder_id").apply()
        }

        // Search for existing folder
        try {
            val query = java.net.URLEncoder.encode("name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false", "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)"
            val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    val folderId = files.getJSONObject(0).getString("id")
                    prefs.edit().putString("drive_folder_id", folderId).apply()
                    return@withContext folderId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search folder error: ${e.message}")
        }

        // Create folder if not found
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val createConn = (URL(createUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
            connectTimeout = 15000
            readTimeout = 15000
        }

        val metadata = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }

        OutputStreamWriter(createConn.outputStream, "UTF-8").use { it.write(metadata.toString()) }

        if (createConn.responseCode in 200..299) {
            val res = createConn.inputStream.bufferedReader().use { it.readText() }
            val folderId = JSONObject(res).getString("id")
            prefs.edit().putString("drive_folder_id", folderId).apply()
            return@withContext folderId
        } else {
            val err = createConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.e(TAG, "Failed to create Drive folder: HTTP ${createConn.responseCode} - $err")
            throw IOException("Fallo al crear carpeta en Google Drive: HTTP ${createConn.responseCode}")
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    suspend fun uploadBackup(concerts: List<Concert>): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.i(TAG, "uploadBackup omitido: sin conectividad de red")
            return@withContext Result.failure(
                IllegalStateException("Sin conexión a internet. El respaldo se reintentará automáticamente.")
            )
        }
        try {
            val token = getAccessToken() ?: return@withContext Result.failure(IllegalStateException("No autenticado en Google"))
            val folderId = getOrCreateFolderId(token)

            // 1. Recolectar SoundFonts
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

            // 2. Crear ZIP temporal
            val timestamp = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
            val dateStr = dateFormat.format(Date(timestamp))
            val backupFileName = "StageKeysLive_Backup_${dateStr}.zip"
            val tempZip = File(context.cacheDir, "temp_drive_backup_${timestamp}.zip")

            ZipOutputStream(FileOutputStream(tempZip)).use { zout ->
                val manifest = JSONObject().apply {
                    put("type", "google_drive_backup")
                    put("version", "1.0")
                    put("concertsCount", updatedConcerts.size)
                    put("timestamp", timestamp)
                }
                zout.putNextEntry(ZipEntry("manifest.json"))
                zout.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zout.closeEntry()

                val concertsJsonStr = ConcertSerializer.serialize(updatedConcerts)
                zout.putNextEntry(ZipEntry("concerts.json"))
                zout.write(concertsJsonStr.toByteArray(Charsets.UTF_8))
                zout.closeEntry()

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

            // 3. Subir Multipart a Google Drive
            val boundary = "==StageKeysDriveUpload==" + System.currentTimeMillis()
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            val conn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                connectTimeout = 30000
                readTimeout = 30000
            }

            val metaJson = JSONObject().apply {
                put("name", backupFileName)
                put("parents", JSONArray().put(folderId))
            }

            conn.outputStream.use { out ->
                // Metadata Part
                out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                out.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
                out.write(metaJson.toString().toByteArray(Charsets.UTF_8))
                out.write("\r\n".toByteArray(Charsets.UTF_8))

                // Media Part
                out.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                out.write("Content-Type: application/zip\r\n\r\n".toByteArray(Charsets.UTF_8))
                FileInputStream(tempZip).use { input ->
                    input.copyTo(out)
                }
                out.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val responseCode = conn.responseCode
            tempZip.delete()

            if (responseCode == 401) {
                invalidateToken(token)
                return@withContext Result.failure(IllegalStateException("Token expirado. Por favor intenta respaldar nuevamente."))
            }

            if (responseCode in 200..299) {
                prefs.edit().putLong("last_cloud_backup_time", timestamp).apply()
                Log.i(TAG, "Drive backup uploaded successfully: $backupFileName")

                // 4. Rotación: Limitar a MAX_BACKUPS_RETAINED
                rotateBackups(token, folderId)

                Result.success(Unit)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Failed upload to Drive: HTTP $responseCode - $errorBody")
                Result.failure(IOException("Fallo al subir a Google Drive: HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Drive backup", e)
            CrashReporter.recordException(e, "GoogleDriveBackupUpload")
            Result.failure(e)
        }
    }

    private suspend fun rotateBackups(token: String, folderId: String) = withContext(Dispatchers.IO) {
        try {
            val query = java.net.URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name,createdTime)&orderBy=createdTime+desc"
            val conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                val files = JSONObject(res).optJSONArray("files")
                if (files != null && files.length() > MAX_BACKUPS_RETAINED) {
                    for (i in MAX_BACKUPS_RETAINED until files.length()) {
                        val fileId = files.getJSONObject(i).getString("id")
                        deleteFile(token, fileId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backup rotation warning: ${e.message}")
        }
    }

    private fun deleteFile(token: String, fileId: String) {
        try {
            val deleteUrl = "https://www.googleapis.com/drive/v3/files/$fileId"
            val conn = (URL(deleteUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 10000
            }
            conn.responseCode
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete old backup $fileId", e)
        }
    }

    suspend fun listBackups(): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.i(TAG, "listBackups omitido: sin conectividad")
            return@withContext Result.failure(
                IllegalStateException("Sin conexión a internet. Conéctate a internet para ver los respaldos en Google Drive.")
            )
        }
        try {
            val token = getAccessToken() ?: return@withContext Result.failure(IllegalStateException("No autenticado"))
            val folderId = getOrCreateFolderId(token)

            // Try query by folder parent, fallback to name query if empty
            var query = java.net.URLEncoder.encode("'$folderId' in parents and trashed = false", "UTF-8")
            var listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name,createdTime,size)&orderBy=createdTime+desc"
            var conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 15000
                readTimeout = 15000
            }

            var files: JSONArray? = null
            if (conn.responseCode == 200) {
                val res = conn.inputStream.bufferedReader().use { it.readText() }
                files = JSONObject(res).optJSONArray("files")
            }

            if (files == null || files.length() == 0) {
                // Secondary search for any StageKeysLive backup in the user's Drive app scope
                query = java.net.URLEncoder.encode("name contains 'StageKeysLive_Backup_' and trashed = false", "UTF-8")
                listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name,createdTime,size)&orderBy=createdTime+desc"
                val conn2 = (URL(listUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 15000
                    readTimeout = 15000
                }
                if (conn2.responseCode == 200) {
                    val res = conn2.inputStream.bufferedReader().use { it.readText() }
                    files = JSONObject(res).optJSONArray("files")
                }
            }

            val validFiles = files ?: JSONArray()
            val list = mutableListOf<DriveBackupItem>()

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val humanFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            for (i in 0 until validFiles.length()) {
                val f = validFiles.getJSONObject(i)
                val id = f.getString("id")
                val name = f.getString("name")
                val createdIso = f.optString("createdTime", "")
                val size = f.optLong("size", 0L)

                val timestamp = try {
                    isoFormat.parse(createdIso)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val dateStr = humanFormat.format(Date(timestamp))
                val sizeStr = if (size > 1024 * 1024) {
                    String.format(Locale.US, "%.1f MB", size.toDouble() / (1024 * 1024))
                } else {
                    String.format(Locale.US, "%.1f KB", size.toDouble() / 1024)
                }

                list.add(
                    DriveBackupItem(
                        id = id,
                        name = name,
                        createdTimeFormatted = dateStr,
                        timestamp = timestamp,
                        sizeFormatted = sizeStr
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing backups: ${e.message}", e)
            CrashReporter.recordException(e, "GoogleDriveListBackups")
            Result.failure(e)
        }
    }

    suspend fun downloadAndRestoreBackup(backupId: String): Result<List<Concert>> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            Log.i(TAG, "downloadAndRestoreBackup omitido: sin conectividad")
            return@withContext Result.failure(
                IllegalStateException("Sin conexión a internet. Conéctate a internet para restaurar un respaldo.")
            )
        }
        try {
            val token = getAccessToken() ?: return@withContext Result.failure(IllegalStateException("No autenticado"))
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$backupId?alt=media"
            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 30000
                readTimeout = 30000
            }

            if (conn.responseCode != 200) {
                return@withContext Result.failure(IOException("Fallo al descargar respaldo: HTTP ${conn.responseCode}"))
            }

            val soundfontsDir = File(context.filesDir, "soundfonts").apply { mkdirs() }
            var concertsJsonStr: String? = null

            ZipInputStream(conn.inputStream).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "concerts.json") {
                        concertsJsonStr = zis.bufferedReader().readText()
                    } else if (name.startsWith("soundfonts/")) {
                        val sf2Name = name.removePrefix("soundfonts/")
                        if (sf2Name.isNotEmpty()) {
                            val targetSf2 = File(soundfontsDir, sf2Name)
                            FileOutputStream(targetSf2).use { out ->
                                zis.copyTo(out)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (concertsJsonStr.isNullOrBlank()) {
                return@withContext Result.failure(IllegalStateException("El archivo de respaldo no contiene concerts.json válido."))
            }

            val rawConcerts = ConcertSerializer.deserialize(concertsJsonStr!!)

            // Remap soundfont paths to absolute local paths
            val restoredConcerts = rawConcerts.map { concert ->
                val updatedChannels = concert.channels.map { ch ->
                    if (ch.sf2Path != null && ch.sf2Path.startsWith("soundfonts/")) {
                        val localFile = File(soundfontsDir, ch.sf2Path.removePrefix("soundfonts/"))
                        ch.copy(sf2Path = localFile.absolutePath)
                    } else ch
                }
                val updatedPatches = concert.patches.map { p ->
                    val updatedSnapshots = p.channelsSnapshot.map { snap ->
                        if (snap.sf2Path != null && snap.sf2Path.startsWith("soundfonts/")) {
                            val localFile = File(soundfontsDir, snap.sf2Path.removePrefix("soundfonts/"))
                            snap.copy(sf2Path = localFile.absolutePath)
                        } else snap
                    }
                    p.copy(channelsSnapshot = updatedSnapshots)
                }
                concert.copy(channels = updatedChannels, patches = updatedPatches)
            }

            Result.success(restoredConcerts)
        } catch (e: Exception) {
            CrashReporter.recordException(e, "GoogleDriveRestore")
            Result.failure(e)
        }
    }

    fun getLastCloudBackupTime(): Long? {
        val time = prefs.getLong("last_cloud_backup_time", -1L)
        return if (time > 0) time else null
    }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
