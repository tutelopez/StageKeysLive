package com.midi.mainstage

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mainstageandroid.composeapp.generated.resources.Res
// KMP resources no longer imported for font directly to avoid crashes on Android

// ─── Brand Colors (Sunday Keys inspired) ─────────────────────────────────────
val DarkBackground   = Color(0xFF0D0D12)
val DarkPanel        = Color(0xFF181824)
val SurfaceElevated  = Color(0xFF1E1E2C) // Replaces #1E1E2C, #141420, #0C0C14, #12121E, #0E0E18, #1A1424, #110E1A, #161A16, #101410, #1A1A28, #1A1A26
val LightPanel       = Color(0xFF252535)
val OutlineVariant   = Color(0xFF2A2A3A)

val DashboardGradientTop    = Color(0xFF0A0B10)
val DashboardGradientMid    = Color(0xFF0D1220)
val DashboardGradientBottom = Color(0xFF101B33)

val TextLight        = Color(0xFFFFFFFF)
val TextDark         = Color(0xFF9090B0)

val StatusSuccess    = Color(0xFF10B981)
val StatusWarning    = Color(0xFFF59E0B)
val StatusError      = Color(0xFFFF2A2A)

val AccentSky        = Color(0xFF38BDF8)
val AccentNeonGreen  = Color(0xFF39FF14)
val AccentPurple     = Color(0xFF9D4EDD)
val AccentCoral      = Color(0xFFFB7185)
val AccentMint       = Color(0xFF2DD4BF)
val AccentPink       = Color(0xFFF472B6)
val AccentWarmYellow = Color(0xFFFBBF24)

val AccentColors = listOf(
    AccentSky, AccentPurple, AccentCoral, AccentMint, AccentPink, AccentWarmYellow
)

// ─── Shapes ───────────────────────────────────────────────────────────────────
val AppShapes = Shapes(
    small  = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large  = RoundedCornerShape(24.dp)
)

// ─── Color Scheme ─────────────────────────────────────────────────────────────
private val StageKeysColorScheme = darkColorScheme(
    background          = DarkBackground,
    surface             = DarkPanel,
    surfaceVariant      = LightPanel,
    onBackground        = TextLight,
    onSurface           = TextLight,
    onSurfaceVariant    = TextDark,
    primary             = AccentSky,
    onPrimary           = Color(0xFF001829),
    secondary           = AccentWarmYellow,
    onSecondary         = Color(0xFF1A1000),
    tertiary            = AccentNeonGreen,
    onTertiary          = Color(0xFF001500),
    error               = Color(0xFFFF2A2A),
    onError             = Color.White,
    outline             = LightPanel,
    outlineVariant      = Color(0xFF2A2A3A)
)

// ─── Theme ────────────────────────────────────────────────────────────────────
// Font loading uses an expect/actual getOutfitFontFamily() to bypass
// org.jetbrains.compose.resources.Font crashes on older Android devices.
@Composable
fun StageKeysTheme(content: @Composable () -> Unit) {
    val outfitFamily = getOutfitFontFamily()

    val typography = remember(outfitFamily) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp
            ),
            titleLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.sp
            ),
            titleMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp
            ),
            bodyLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp
            ),
            bodyMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.25.sp
            ),
            bodySmall = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp
            ),
            labelLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp
            ),
            labelMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            ),
            labelSmall = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            )
        )
    }

    MaterialTheme(
        colorScheme = StageKeysColorScheme,
        typography  = typography,
        shapes      = AppShapes,
        content     = content
    )
}



@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    glowOpacityFactor: Float = 1.0f,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to DashboardGradientTop,
                            0.5f to DashboardGradientMid,
                            1.0f to DashboardGradientBottom
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentPurple.copy(alpha = 0.16f * glowOpacityFactor), Color.Transparent),
                        center = with(density) { Offset(maxWidth.toPx() * 0.12f, maxHeight.toPx() * 0.08f) },
                        radius = with(density) { maxWidth.toPx() * 0.55f }
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentSky.copy(alpha = 0.14f * glowOpacityFactor), Color.Transparent),
                        center = with(density) { Offset(maxWidth.toPx() * 0.88f, maxHeight.toPx() * 0.92f) },
                        radius = with(density) { maxWidth.toPx() * 0.6f }
                    )
                )
        )
        content()
    }
}
