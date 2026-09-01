package com.midi.mainstage

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
actual fun MidiFileExporter(
    eventsToExport: List<RecordingEvent>?,
    onExportComplete: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(eventsToExport) {
        if (eventsToExport == null || eventsToExport.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            try {
                val midiBytes = escribeSMF(eventsToExport)
                val timeStamp = System.currentTimeMillis()
                val midiFile = File(context.cacheDir, "grabacion_${timeStamp}.mid")
                FileOutputStream(midiFile).use { fos ->
                    fos.write(midiBytes)
                }

                val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", midiFile)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/midi"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "Exportar grabación MIDI").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(context, "Error al exportar MIDI: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        onExportComplete()
    }
}
