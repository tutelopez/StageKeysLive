package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
actual fun MidiFileExporter(
    eventsToExport: List<RecordingEvent>?,
    onExportComplete: () -> Unit
) {
    // Desktop stub
}
