package com.midi.mainstage

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.runtime.Composable

// Using standard Android native font loading from res/font/
// This bypasses the composeResources KMP bug that causes fatal crashes on Android
@Composable
actual fun getOutfitFontFamily(): FontFamily {
    return FontFamily(
        Font(R.font.outfit_regular, weight = FontWeight.Normal),
        Font(R.font.outfit_semibold, weight = FontWeight.SemiBold),
        Font(R.font.outfit_bold, weight = FontWeight.Bold)
    )
}
