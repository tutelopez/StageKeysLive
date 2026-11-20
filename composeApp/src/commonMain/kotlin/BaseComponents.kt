package com.tutelopezmusic.stagekeyslive

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─────────────────────────────────────────────────────────────────────────────
// BASE COMPONENTS (FASE 2)
// Esquinas 10-14dp, bordes hairline, áreas táctiles >= 48dp, tokens consistentes
// ─────────────────────────────────────────────────────────────────────────────

enum class SkButtonVariant {
    Primary,
    Secondary,
    Outline,
    Danger,
    Ghost
}

/**
 * Botón base con área táctil mínima de 48dp, esquinas 10-14dp y estados pressed/disabled.
 */
@Composable
fun SkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SkButtonVariant = SkButtonVariant.Primary,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val containerColor = when (variant) {
        SkButtonVariant.Primary -> if (enabled) {
            if (isPressed) AccentPrimary.copy(alpha = 0.85f) else AccentPrimary
        } else AccentPrimary.copy(alpha = 0.35f)

        SkButtonVariant.Secondary -> if (enabled) {
            if (isPressed) SurfaceElevated else SurfaceCard
        } else SurfaceCard.copy(alpha = 0.4f)

        SkButtonVariant.Outline -> if (isPressed) SurfaceCard.copy(alpha = 0.6f) else Color.Transparent

        SkButtonVariant.Danger -> if (enabled) {
            if (isPressed) StateMute.copy(alpha = 0.85f) else StateMute
        } else StateMute.copy(alpha = 0.35f)

        SkButtonVariant.Ghost -> if (isPressed) SurfaceCard.copy(alpha = 0.5f) else Color.Transparent
    }

    val contentColor = when (variant) {
        SkButtonVariant.Primary -> if (enabled) Color.White else TextTertiary
        SkButtonVariant.Secondary -> if (enabled) TextPrimary else TextTertiary
        SkButtonVariant.Outline -> if (enabled) TextPrimary else TextTertiary
        SkButtonVariant.Danger -> if (enabled) Color.White else TextTertiary
        SkButtonVariant.Ghost -> if (enabled) TextSecondary else TextTertiary
    }

    val border = when (variant) {
        SkButtonVariant.Outline, SkButtonVariant.Secondary -> BorderStroke(1.dp, BorderHairline)
        else -> null
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp),
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = border,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Chip interactivo con área táctil garantizada de al menos 48dp (vía padding/touch target),
 * esquinas 10dp y borde hairline.
 */
@Composable
fun SkChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    selectedColor: Color = AccentPrimary,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val bg by animateColorAsState(
        if (selected) selectedColor.copy(alpha = 0.22f) else SurfaceCard
    )
    val borderColor by animateColorAsState(
        if (selected) selectedColor else BorderHairline
    )
    val textColor by animateColorAsState(
        if (selected) selectedColor else TextSecondary
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.invoke()
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * Tarjeta base para slots, paneles secundarios y configuraciones.
 * Esquinas 12-14dp, borde hairline y fondo SurfaceCard.
 */
@Composable
fun SkCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    containerColor: Color = SurfaceCard,
    borderColor: Color = BorderHairline,
    borderWidth: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

/**
 * Switch / Toggle base con diseño de estudio, área táctil >= 48dp y esquinas redondeadas.
 */
@Composable
fun SkSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = AccentPrimary
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = activeColor,
            uncheckedThumbColor = TextTertiary,
            uncheckedTrackColor = SurfaceCard,
            uncheckedBorderColor = BorderHairline,
            checkedBorderColor = activeColor
        )
    )
}

/**
 * Diálogo base modal con esquinas de 16dp, fondo SurfacePanel y borde hairline.
 */
@Composable
fun SkDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfacePanel),
            border = BorderStroke(1.dp, BorderHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                HorizontalDivider(color = BorderHairline, thickness = 1.dp)
                content()
            }
        }
    }
}

/**
 * Item de lista con altura mínima táctil de 48dp, bordes hairline y feedback visual consistente.
 */
@Composable
fun SkListItem(
    headlineText: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(SurfacePanel)
            .border(1.dp, BorderHairline, shape)
            .then(clickableModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        leadingContent?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headlineText,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        trailingContent?.invoke()
    }
}
