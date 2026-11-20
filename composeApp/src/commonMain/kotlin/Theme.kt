package com.tutelopezmusic.stagekeyslive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Surfaces & Backgrounds ───────────────────────────────────────────────────
val BgDark           = Color(0xFF0B0D14) // Fondo general de la app
val DarkBackground   = BgDark            // Alias para compatibilidad
val SurfacePanel     = Color(0xFF12151F) // Paneles principales
val DarkPanel        = SurfacePanel      // Alias para compatibilidad
val SurfaceElevated  = Color(0xFF181B2B) // Superficies elevadas / headers
val SurfaceCard      = Color(0xFF141724) // Tarjetas, slots y paneles secundarios
val LightPanel       = SurfaceCard       // Alias para compatibilidad temporal
val StripTop         = Color(0xFF171B2B) // Fader strip degradado superior
val StripBottom      = Color(0xFF10131C) // Fader strip degradado inferior
val BorderHairline   = Color(0xFF222840) // Bordes 1dp hairline
val OutlineVariant   = BorderHairline    // Alias para compatibilidad
val HighlightTop     = Color(0x0FFFFFFF) // Resalte superior 1dp blanco al 6% (0.06 * 255 ~= 15 = 0x0F)

val DashboardGradientTop    = Color(0xFF0B0D14)
val DashboardGradientMid    = Color(0xFF0E111C)
val DashboardGradientBottom = Color(0xFF121523)

// ─── Typography / Texts ───────────────────────────────────────────────────────
val TextPrimary      = Color(0xFFF2F4FA) // Texto primario
val TextLight        = TextPrimary       // Alias para compatibilidad
val TextSecondary    = Color(0xFF8D93AD) // Texto secundario / leyendas
val TextDark         = TextSecondary     // Alias para compatibilidad
val TextTertiary     = Color(0xFF5B6178) // Texto terciario / deshabilitado
val TextMuted        = TextTertiary

// ─── Brand & Accents ──────────────────────────────────────────────────────────
val AccentPrimary    = Color(0xFF7C5CFF) // Violeta principal
val AccentSky        = AccentPrimary     // Unificado al acento principal violeta
val AccentPurple     = AccentPrimary     // Violeta principal
val AccentLavender   = Color(0xFFB3A3F8) // Lavanda para tapas de fader y selección
val AccentCoral      = Color(0xFFFB7185)
val AccentMint       = Color(0xFF2DD4BF)
val AccentPink       = Color(0xFFF472B6)
val AccentWarmYellow = Color(0xFFFBBF24)

// ─── States ───────────────────────────────────────────────────────────────────
val StateMute        = Color(0xFFE5484D) // Mute activo
val StateSolo        = Color(0xFFF5C542) // Solo activo
val StateClip        = Color(0xFFFF4D4F) // Clip (> 0 dBFS)
val StateOk          = Color(0xFF3DDC84) // OK / Normal
val StatusSuccess    = StateOk
val StatusWarning    = StateSolo
val StatusError      = StateClip

// ─── Channel Identity Colors (para pastilla de nombre y meter) ────────────────
val ChannelYellow    = Color(0xFFF5C542)
val ChannelTurquoise = Color(0xFF4AA8B5)
val ChannelGreen     = Color(0xFF4CCF5A)
val ChannelOrange    = Color(0xFFF5A623)
val ChannelRed       = Color(0xFFE5484D)
val ChannelMagenta   = Color(0xFFE535AB)
val ChannelPink      = Color(0xFFD9627A)
val ChannelBlue      = Color(0xFF4C6FFF)

val ChannelColors = listOf(
    ChannelYellow, ChannelTurquoise, ChannelGreen, ChannelOrange,
    ChannelRed, ChannelMagenta, ChannelPink, ChannelBlue
)
val AccentColors = ChannelColors

// ─── FX Specific Colors ───────────────────────────────────────────────────────
val FxReverbColor    = Color(0xFFA855F7) // Reverb
val FxChorusColor    = Color(0xFF2DD4BF) // Chorus
val FxToneColor      = Color(0xFFFB7185) // Tone / Cutoff

// ─── External Services & Dialogs ──────────────────────────────────────────────
val ServiceDrive        = Color(0xFFFF9800)
val ServiceTelegram     = Color(0xFF229ED9)
val ServiceGoogle       = Color(0xFF4285F4)
val AvatarGradientStart = Color(0xFF2563EB)
val AvatarGradientEnd   = Color(0xFF38BDF8)
val StarGold            = Color(0xFFFFD700)

// ─── Virtual Keyboard Colors ──────────────────────────────────────────────────
val KeyWhiteBottom      = Color(0xFFE2E8F0)
val KeyWhiteBorder      = Color(0xFF12151F)
val KeyBlackTop         = Color(0xFF222840)
val KeyBlackBottom      = Color(0xFF10131C)
val KeyBlackBorder      = Color(0xFF0B0D14)

// ─── Shapes ───────────────────────────────────────────────────────────────────
val AppShapes = Shapes(
    small  = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large  = RoundedCornerShape(18.dp)
)

// ─── Color Scheme ─────────────────────────────────────────────────────────────
private val StageKeysColorScheme = darkColorScheme(
    background          = BgDark,
    surface             = SurfacePanel,
    surfaceVariant      = SurfaceCard,
    onBackground        = TextPrimary,
    onSurface           = TextPrimary,
    onSurfaceVariant    = TextSecondary,
    primary             = AccentPrimary,
    onPrimary           = Color.White,
    secondary           = AccentLavender,
    onSecondary         = BgDark,
    tertiary            = StateOk,
    onTertiary          = BgDark,
    error               = StateClip,
    onError             = Color.White,
    outline             = BorderHairline,
    outlineVariant      = BorderHairline
)

// ─── Typography with Tabular Digits ───────────────────────────────────────────
const val FontFeatureTabular = "tnum"

@Composable
fun StageKeysTheme(content: @Composable () -> Unit) {
    val outfitFamily = getOutfitFontFamily()

    val typography = remember(outfitFamily) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                letterSpacing = (-0.5).sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            titleLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            titleMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = 0.15.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            bodyLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = 0.5.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            bodyMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = 0.25.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            bodySmall = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = 0.4.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            labelLarge = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.1.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            labelMedium = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp,
                fontFeatureSettings = FontFeatureTabular
            ),
            labelSmall = TextStyle(
                fontFamily = outfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                fontFeatureSettings = FontFeatureTabular
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
        // Solid deep dark base
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(BgDark)
        )
        // Subtle edge vignette
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.40f)),
                        center = with(density) { Offset(maxWidth.toPx() * 0.5f, maxHeight.toPx() * 0.5f) },
                        radius = with(density) { kotlin.math.max(maxWidth.toPx(), maxHeight.toPx()) * 0.75f }
                    )
                )
        )
        // Ultra-subtle accent vignette (capped at 4%)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentPrimary.copy(alpha = 0.04f * glowOpacityFactor.coerceIn(0f, 1f)), Color.Transparent),
                        center = with(density) { Offset(maxWidth.toPx() * 0.15f, maxHeight.toPx() * 0.08f) },
                        radius = with(density) { maxWidth.toPx() * 0.5f }
                    )
                )
        )
        content()
    }
}
