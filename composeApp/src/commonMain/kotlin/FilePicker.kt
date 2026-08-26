package com.midi.mainstage

import androidx.compose.runtime.Composable

@Composable
expect fun Sf2FilePicker(show: Boolean, onFileSelected: (String?) -> Unit)
