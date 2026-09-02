package com.midi.mainstage

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun Sf2FilePicker(show: Boolean, onFileSelected: (path: String?, displayName: String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onFileSelected(null, null)
            return@rememberLauncherForActivityResult
        }
        
        try {
            val contentResolver = context.contentResolver

            // Query original display name using OpenableColumns.DISPLAY_NAME
            var realDisplayName: String? = null
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    realDisplayName = cursor.getString(nameIndex)
                }
            }
            val displayName = realDisplayName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "custom.sf2"

            // Keep generating unique internal file name on disk to avoid collisions
            val fileName = "sf2_${System.currentTimeMillis()}.sf2"
            val destFile = File(context.filesDir, fileName)
            
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            onFileSelected(destFile.absolutePath, displayName)
        } catch (e: Exception) {
            e.printStackTrace()
            onFileSelected(null, null)
        }
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(arrayOf("*/*"))
        }
    }
}
