package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
expect fun PackageExporter(
    concertToExport: Concert?,
    patchToExport: PatchState?,
    onExportComplete: () -> Unit
)
