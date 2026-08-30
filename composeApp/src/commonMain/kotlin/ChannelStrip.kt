package com.midi.mainstage

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.compose.ui.Alignment

@Composable
fun ChannelStripItem(
    state: ChannelStripState,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onGearClick: () -> Unit
) {
    val accentColor = parseColorHex(state.colorHex)

    Column(
        modifier = Modifier
            .width(74.dp)
            .fillMaxHeight()
            .shadow(
                elevation = 12.dp, 
                shape = RoundedCornerShape(14.dp), 
                ambientColor = accentColor.copy(alpha = 0.4f), 
                spotColor = accentColor.copy(alpha = 0.55f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(SurfaceElevated, DarkBackground))
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Channel name + gear ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Text(
                text = state.name.take(10).uppercase(),
                color = accentColor,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            )

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onGearClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Configure",
                    tint = TextDark.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // ── Fader + VU meter ────────────────────────────────────────────
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VolumeFader(
                value = state.volume,
                accentColor = accentColor,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            if (level > 0.01f) {
                LevelMeter(
                    level = level,
                    accentColor = accentColor,
                    modifier = Modifier.width(6.dp).fillMaxHeight()
                )
            }
        }

        // ── Volume % label ──────────────────────────────────────────────
        Text(
            text = "${(state.volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )

        // ── Mute / Solo ──────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MuteSoloButton(
                label = "M",
                active = state.isMuted,
                activeColor = StatusError,
                onClick = onMuteToggle,
                modifier = Modifier.weight(1f)
            )
            MuteSoloButton(
                label = "S",
                active = state.isSoloed,
                activeColor = StatusWarning,
                onClick = onSoloToggle,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MuteSoloButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(20.dp)
            .shadow(
                elevation = if (active) 6.dp else 0.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = if (active) activeColor else Color.Transparent,
                spotColor = if (active) activeColor else Color.Transparent
            )
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) activeColor else SurfaceElevated
            )
            .border(
                1.dp,
                if (active) activeColor else OutlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else TextDark,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
