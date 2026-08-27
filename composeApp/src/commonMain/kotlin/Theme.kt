package com.midi.mainstage

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
val LightPanel       = Color(0xFF252535)
val TextLight        = Color(0xFFFFFFFF)
val TextDark         = Color(0xFF9090B0)

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
