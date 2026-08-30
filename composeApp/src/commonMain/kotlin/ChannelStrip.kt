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
            .width(80.dp)
            .fillMaxHeight()
            .shadow(
                elevation = 8.dp, 
                shape = RoundedCornerShape(14.dp), 
                ambientColor = accentColor.copy(alpha = 0.35f), 
                spotColor = accentColor.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceElevated, DarkBackground)
                )
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 5.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // â”€â”€ Channel name + gear â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Text(
                text = state.name.take(9).uppercase(),
                color = accentColor.copy(alpha = 0.9f),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            )

            IconButton(onClick = onGearClick, modifier = Modifier.size(16.dp)) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Configure",
                    tint = TextDark,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        // â”€â”€ Fader + VU meter â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            LevelMeter(
                level = level,
                accentColor = accentColor,
                modifier = Modifier.width(8.dp).fillMaxHeight()
            )
        }

        // â”€â”€ Volume % label â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text(
            text = "${(state.volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
        )

        // â”€â”€ Loaded Patch Name â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        Text(
            text = state.sf2Name.substringBefore(".sf2").take(10),
            color = TextDark,
            fontSize = 7.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // â”€â”€ Mute / Solo â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                shape = RoundedCornerShape(4.dp),
                ambientColor = if (active) activeColor else Color.Transparent,
                spotColor = if (active) activeColor else Color.Transparent
            )
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (active) activeColor.copy(alpha = 0.85f) else SurfaceElevated
            )
            .border(
                1.dp,
                if (active) activeColor else LightPanel,
                RoundedCornerShape(4.dp)
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
