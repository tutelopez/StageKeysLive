package com.midi.mainstage

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun UserAvatar(
    photoUrl: String?,
    displayName: String?,
    modifier: Modifier = Modifier
)
