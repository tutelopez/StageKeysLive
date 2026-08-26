package com.midi.mainstage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun Sf2FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onFileSelected(null)
            return@rememberLauncherForActivityResult
        }
        
        try {
            val contentResolver = context.contentResolver
            val fileName = "sf2_${System.currentTimeMillis()}.sf2"
            val destFile = File(context.filesDir, fileName)
            
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            onFileSelected(destFile.absolutePath)
        } catch (e: Exception) {
            e.printStackTrace()
            onFileSelected(null)
        }
    }

    LaunchedEffect(show) {
        if (show) {
            launcher.launch(arrayOf("*/*"))
        }
    }
}
