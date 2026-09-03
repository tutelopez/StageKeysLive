package com.midi.mainstage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AndroidSf2ExplorerController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : Sf2ExplorerController {

    private val prefs = context.getSharedPreferences("Sf2ExplorerPrefs", Context.MODE_PRIVATE)
    private var launcherCallback: (() -> Unit)? = null

    fun setLauncher(callback: (() -> Unit)?) {
        this.launcherCallback = callback
    }

    var currentState by mutableStateOf(readInitialState())
        private set

    override val state: Sf2ExplorerState
        get() = currentState

    init {
        if (currentState.isFolderConfigured) {
            refreshFiles()
        }
    }

    private fun readInitialState(): Sf2ExplorerState {
        val uriStr = prefs.getString("sf2_folder_uri", null)
        val name = prefs.getString("sf2_folder_name", null)
        val isConfigured = !uriStr.isNullOrBlank()
        return Sf2ExplorerState(
            folderUri = uriStr,
            folderName = name,
            isFolderConfigured = isConfigured,
            files = emptyList(),
            isLoading = false,
            previewingUri = null
        )
    }

    override fun requestSelectFolder() {
        launcherCallback?.invoke()
    }

    fun onFolderSelected(uri: Uri?) {
        if (uri == null) return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)

            val docFile = DocumentFile.fromTreeUri(context, uri)
            val folderName = docFile?.name ?: uri.lastPathSegment ?: "Carpeta SF2"

            prefs.edit()
                .putString("sf2_folder_uri", uri.toString())
                .putString("sf2_folder_name", folderName)
                .apply()

            currentState = currentState.copy(
                folderUri = uri.toString(),
                folderName = folderName,
                isFolderConfigured = true
            )

            refreshFiles()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun clearFolder() {
        try {
            val uriStr = prefs.getString("sf2_folder_uri", null)
            if (!uriStr.isNullOrBlank()) {
                val uri = Uri.parse(uriStr)
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(uri, flags)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.edit().clear().apply()
        currentState = Sf2ExplorerState()
    }

    override fun refreshFiles() {
        val uriStr = currentState.folderUri ?: return
        currentState = currentState.copy(isLoading = true)

        coroutineScope.launch(Dispatchers.IO) {
            val fileList = mutableListOf<Sf2FileEntry>()
            try {
                val treeUri = Uri.parse(uriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.exists()) {
                    scanDirectory(rootDoc, "", fileList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                currentState = currentState.copy(
                    files = fileList.sortedWith(compareBy({ it.relativePath }, { it.displayName.lowercase() })),
                    isLoading = false
                )
            }
        }
    }

    private fun scanDirectory(dir: DocumentFile, currentRelativePath: String, result: MutableList<Sf2FileEntry>) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                val subPath = if (currentRelativePath.isEmpty()) child.name ?: "" else "$currentRelativePath/${child.name}"
                scanDirectory(child, subPath, result)
            } else if (child.isFile && child.name?.endsWith(".sf2", ignoreCase = true) == true) {
                result.add(
                    Sf2FileEntry(
                        uri = child.uri.toString(),
                        displayName = child.name ?: "soundfont.sf2",
                        sizeBytes = child.length(),
                        relativePath = currentRelativePath
                    )
                )
            }
        }
    }

    override suspend fun previewFile(entry: Sf2FileEntry, synth: PlatformAudioSynth) {
        if (currentState.previewingUri == entry.uri) {
            stopPreview(synth)
            return
        }

        currentState = currentState.copy(previewingUri = entry.uri)

        withContext(Dispatchers.IO) {
            try {
                // Ensure a local cached copy for FluidSynth
                val cacheFileName = "preview_${entry.displayName.hashCode()}_${entry.sizeBytes}.sf2"
                val cacheFile = File(context.cacheDir, cacheFileName)

                if (!cacheFile.exists() || cacheFile.length() != entry.sizeBytes) {
                    val uri = Uri.parse(entry.uri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (cacheFile.exists() && cacheFile.length() > 0) {
                    synth.previewSoundFont(cacheFile.absolutePath, 60, 100, 2000)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun stopPreview(synth: PlatformAudioSynth) {
        synth.stopPreview()
        currentState = currentState.copy(previewingUri = null)
    }

    override suspend fun importFileForChannel(entry: Sf2FileEntry): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(entry.uri)
            val fileName = "sf2_${System.currentTimeMillis()}.sf2"
            val destFile = File(context.filesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (destFile.exists() && destFile.length() > 0) {
                Pair(destFile.absolutePath, entry.displayName)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@Composable
actual fun rememberSf2ExplorerController(): Sf2ExplorerController {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val controller = remember { AndroidSf2ExplorerController(context, coroutineScope) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        controller.onFolderSelected(uri)
    }

    DisposableEffect(controller) {
        controller.setLauncher {
            launcher.launch(null)
        }
        onDispose {
            controller.setLauncher(null)
        }
    }

    return controller
}
