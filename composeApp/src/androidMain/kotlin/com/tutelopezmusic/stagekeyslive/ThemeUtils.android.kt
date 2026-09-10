package com.tutelopezmusic.stagekeyslive

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable

// Using standard Android native font loading from res/font/
// and try/catch fallback to FontFamily.Default to prevent fatal OEM/MIUI font loading crashes
@Composable
actual fun getOutfitFontFamily(): FontFamily {
    return try {
        FontFamily(
            Font(R.font.outfit_regular, weight = FontWeight.Normal),
            Font(R.font.outfit_semibold, weight = FontWeight.SemiBold),
            Font(R.font.outfit_bold, weight = FontWeight.Bold)
        )
    } catch (e: Throwable) {
        FontFamily.Default
    }
}
