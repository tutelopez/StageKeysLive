package com.tutelopezmusic.stagekeyslive

import androidx.compose.runtime.Composable

@Composable
expect fun Sf2FilePicker(show: Boolean, onFileSelected: (path: String?, displayName: String?) -> Unit)
