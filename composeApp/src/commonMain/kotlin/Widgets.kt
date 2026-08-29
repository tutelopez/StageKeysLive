package com.midi.mainstage

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.compose.ui.Alignment

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// VOLUME FADER (Vertical drag â€” gradient fill + bright handle)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun VolumeFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color = AccentSky,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkPanel)
            .border(1.dp, LightPanel, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val newValue = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        // Track fill (gradient from bottom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0.01f, 1f))
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.5f), accentColor.copy(alpha = 0.1f))
                    )
                )
        )

        // Tick marks
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.height / 8f
            for (i in 1..7) {
                val y = i * step
                val w = if (i % 2 == 0) size.width * 0.55f else size.width * 0.3f
                drawLine(
                    color = OutlineVariant,
                    start = Offset((size.width - w) / 2f, y),
                    end = Offset((size.width + w) / 2f, y),
                    strokeWidth = 1.5f
                )
            }
        }

        // Handle
        val handleHeight = 14.dp
        val usableHeight = maxHeight - handleHeight
        val handleOffset = usableHeight * (1f - value)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(handleHeight)
                .align(Alignment.TopCenter)
                .offset(y = handleOffset)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(LightPanel, accentColor.copy(alpha = 0.8f), LightPanel)
                    )
                )
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// LEVEL METER (VU â€” segmented bars from bottom)
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun LevelMeter(
    level: Float,
    accentColor: Color = AccentNeonGreen,
    modifier: Modifier = Modifier
) {
    val segments = 12
    Canvas(
        modifier = modifier.clip(RoundedCornerShape(4.dp))
    ) {
        val segH = (size.height - (segments - 1) * 2f) / segments
        val activeSeg = (level * segments).toInt().coerceIn(0, segments)

        for (i in 0 until segments) {
            val segIdx = segments - 1 - i  // bottom = 0
            val top = i * (segH + 2f)
            val isActive = segIdx < activeSeg
            val segColor = when {
                segIdx >= segments - 2 -> if (isActive) StatusError else StatusError.copy(alpha = 0.2f)
                segIdx >= segments - 4 -> if (isActive) StatusWarning else StatusWarning.copy(alpha = 0.2f)
                else -> if (isActive) accentColor else DarkPanel
            }
            drawRoundRect(
                color = segColor,
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, segH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f)
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// ADD CHANNEL BUTTON
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun AddChannelButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkPanel)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(AccentSky.copy(alpha = 0.3f), LightPanel)
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AccentSky.copy(alpha = 0.15f))
                    .border(1.dp, AccentSky.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.Plus,
                    contentDescription = "Add channel",
                    tint = AccentSky,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                "ADD",
                color = AccentSky.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// EMPTY CHANNEL PLACEHOLDER
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun EmptyChannelPlaceholder() {
    Box(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkPanel)
            .border(1.dp, OutlineVariant, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "â€”",
            color = OutlineVariant,
            fontSize = 18.sp
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// METRONOME CHANNEL ITEM
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun MetronomeChannelItem(
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    val accent = AccentNeonGreen
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(DarkPanel, DarkBackground))
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.3f), DarkBackground)
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Icon + label
        Icon(
            TablerIcons.Music,
            contentDescription = "Metronome",
            tint = accent.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            "CLICK",
            color = accent.copy(alpha = 0.6f),
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        // Fader
        VolumeFader(
            value = volume,
            accentColor = accent,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Text(
            "${(volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// MASTER OUTPUT CHANNEL ITEM
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun MasterOutputChannelItem(
    volume: Float,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMidiMapClick: () -> Unit
) {
    val accent = AccentPurple
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(DarkPanel, DarkBackground))
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.5f), DarkBackground)
                ),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                TablerIcons.Volume,
                contentDescription = "Master",
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "MASTER",
                color = accent.copy(alpha = 0.9f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Text(
            "L/R OUT",
            color = TextDark,
            fontSize = 7.sp
        )

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VolumeFader(
                value = volume,
                accentColor = accent,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            LevelMeter(
                level = level,
                accentColor = accent,
                modifier = Modifier.width(8.dp).fillMaxHeight()
            )
        }

        Text(
            "${(volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.3f), RoundedCornerShape(5.dp))
                .clickable(onClick = onMidiMapClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "MIDI MAP",
                color = accent.copy(alpha = 0.6f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// PITCH BEND WHEEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun PitchBendWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PB", color = TextDark, fontSize = 7.sp, letterSpacing = 1.sp)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkPanel)
                .border(1.dp, LightPanel, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onRelease() },
                        onDrag = { change, _ ->
                            val newVal = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onValueChange(newVal * 2f - 1f)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Center line
            Box(
                modifier = Modifier.fillMaxWidth().height(1.dp)
                    .background(LightPanel)
            )
            // Filled portion
            val clipped = (value + 1f) / 2f
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(clipped.coerceIn(0.01f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentSky.copy(alpha = 0.35f))
            )
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// MODULATION WHEEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun ModulationWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MOD", color = TextDark, fontSize = 7.sp, letterSpacing = 1.sp)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkPanel)
                .border(1.dp, LightPanel, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val newVal = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        onValueChange(newVal)
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(value.coerceIn(0.01f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(AccentPink.copy(alpha = 0.6f), AccentPurple.copy(alpha = 0.3f))
                        )
                    )
            )
        }
    }
}
