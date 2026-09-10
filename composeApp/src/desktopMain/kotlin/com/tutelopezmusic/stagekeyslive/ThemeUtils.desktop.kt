package com.tutelopezmusic.stagekeyslive

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
    val regular = Font(Res.font.outfit_regular, weight = FontWeight.Normal)
    val semibold = Font(Res.font.outfit_semibold, weight = FontWeight.SemiBold)
    val bold = Font(Res.font.outfit_bold, weight = FontWeight.Bold)
    return FontFamily(regular, semibold, bold)
}
