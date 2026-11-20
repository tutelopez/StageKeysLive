package com.tutelopezmusic.stagekeyslive

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Delegación de compatibilidad hacia ProChannelStrip (Fase 3).
 */
@Composable
fun ChannelStripItem(
    state: ChannelStripState,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onGearClick: () -> Unit,
    onReverbChange: (Float) -> Unit = {},
    onChorusChange: (Float) -> Unit = {},
    onCutoffChange: (Float) -> Unit = {},
    isReverbMapped: Boolean = false,
    isChorusMapped: Boolean = false,
    isCutoffMapped: Boolean = false
) {
    ProChannelStrip(
        channel = state,
        levelProvider = { level },
        onVolumeChange = onVolumeChange,
        onMuteToggle = onMuteToggle,
        onSoloToggle = onSoloToggle,
        onGearClick = onGearClick,
        onFxClick = onGearClick,
        onPanChange = {},
        modifier = Modifier.width(104.dp),
        isMidiMapped = isReverbMapped || isChorusMapped || isCutoffMapped
    )
}
