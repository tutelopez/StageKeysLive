package com.midi.mainstage

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import mainstageandroid.composeapp.generated.resources.Res
import mainstageandroid.composeapp.generated.resources.outfit_regular
import mainstageandroid.composeapp.generated.resources.outfit_semibold
import mainstageandroid.composeapp.generated.resources.outfit_bold
import org.jetbrains.compose.resources.Font

import androidx.compose.runtime.Composable

@Composable
actual fun getOutfitFontFamily(): FontFamily {
    // Desktop KMP composeResources is stable, no crash issues here
    return FontFamily(
        Font(Res.font.outfit_regular, weight = FontWeight.Normal),
        Font(Res.font.outfit_semibold, weight = FontWeight.SemiBold),
        Font(Res.font.outfit_bold, weight = FontWeight.Bold)
    )
}
