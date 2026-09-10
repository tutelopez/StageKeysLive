package com.tutelopezmusic.stagekeyslive

import androidx.compose.runtime.Composable

@Composable
expect fun MidiFileExporter(
    eventsToExport: List<RecordingEvent>?,
    onExportComplete: () -> Unit
)
