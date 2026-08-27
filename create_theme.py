import os

filepath = "composeApp/src/commonMain/kotlin/Theme.kt"
content = """package com.midi.mainstage

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import mainstageandroid.composeapp.generated.resources.Res
import mainstageandroid.composeapp.generated.resources.outfit_regular
import mainstageandroid.composeapp.generated.resources.outfit_semibold
import mainstageandroid.composeapp.generated.resources.outfit_bold

// Theme Colors (Sunday Keys inspired dark professional UI)
val DarkBackground = Color(0xFF121217) // Graphite dark blue
val DarkPanel = Color(0xFF1C1C24)
val LightPanel = Color(0xFF262630)
val TextLight = Color(0xFFFFFFFF)
val TextDark = Color(0xFFA0A0B0)

// Accent Colors for Channels/Patches
val AccentPurple = Color(0xFF9D4EDD)
val AccentMint = Color(0xFF2DD4BF)
val AccentSky = Color(0xFF38BDF8)
val AccentCoral = Color(0xFFFB7185)
val AccentPink = Color(0xFFF472B6)
val AccentWarmYellow = Color(0xFFFBBF24)
val AccentNeonGreen = Color(0xFF39FF14) // legacy compatibility

val AccentColors = listOf(
    AccentPurple, AccentMint, AccentSky, AccentCoral, AccentPink, AccentWarmYellow
)

val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun getOutfitFontFamily() = FontFamily(
    Font(Res.font.outfit_regular, weight = FontWeight.Normal),
    Font(Res.font.outfit_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.outfit_bold, weight = FontWeight.Bold)
)

@Composable
fun StageKeysTheme(content: @Composable () -> Unit) {
    val outfit = getOutfitFontFamily()
    
    val typography = Typography(
        displayLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp
        ),
        titleLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        ),
        titleMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        labelLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    )

    val colorScheme = darkColorScheme(
        background = DarkBackground,
        surface = DarkPanel,
        surfaceVariant = LightPanel,
        onBackground = TextLight,
        onSurface = TextLight,
        onSurfaceVariant = TextDark,
        primary = AccentSky
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = AppShapes,
        content = content
    )
}
"""
with open(filepath, "w", encoding="utf-8") as f:
    f.write(content)
