package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
actual fun Sf2FilePicker(show: Boolean, onFileSelected: (path: String?, displayName: String?) -> Unit) {
    if (show) {
        onFileSelected(null, null)
    }
}
