package com.tutelopezmusic.stagekeyslive

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.compose.ui.Alignment

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
                    listOf(AccentSky.copy(alpha = 0.3f), SurfaceCard)
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
                color = AccentPrimary.copy(alpha = 0.85f),
                fontSize = 11.sp,
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

@Composable
fun PitchBendWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("PB", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkPanel)
                .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
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
                    .background(BorderHairline)
            )
            // Filled portion
            val clipped = (value + 1f) / 2f
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .fillMaxHeight(clipped.coerceIn(0.01f, 1f))
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentPrimary.copy(alpha = 0.35f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODULATION WHEEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ModulationWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("MOD", color = TextSecondary, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkPanel)
                .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
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
                            listOf(AccentLavender.copy(alpha = 0.6f), AccentPrimary.copy(alpha = 0.3f))
                        )
                    )
            )
        }
    }
}
