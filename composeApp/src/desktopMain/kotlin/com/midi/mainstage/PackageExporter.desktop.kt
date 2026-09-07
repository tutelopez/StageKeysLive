package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
actual fun PackageExporter(
    concertToExport: Concert?,
    patchToExport: PatchState?,
    onExportComplete: () -> Unit
) {
    // Desktop stub
}
