package com.tutelopezmusic.stagekeyslive

import androidx.compose.runtime.Composable

@Composable
actual fun MidiFileExporter(
    eventsToExport: List<RecordingEvent>?,
    onExportComplete: () -> Unit
) {
    // Desktop stub
}
