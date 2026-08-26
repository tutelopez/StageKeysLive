package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
actual fun Sf2FilePicker(show: Boolean, onFileSelected: (String?) -> Unit) {
    if (show) {
        onFileSelected(null)
    }
}
