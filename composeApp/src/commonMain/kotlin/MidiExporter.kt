package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
expect fun MidiFileExporter(
    eventsToExport: List<RecordingEvent>?,
    onExportComplete: () -> Unit
)
