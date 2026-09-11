package com.tutelopezmusic.stagekeyslive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*

private data class OnboardingSlide(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val subtitle: String
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = 4

    val slides = listOf(
        OnboardingSlide(
            icon = TablerIcons.Keyboard,
            iconTint = AccentSky,
            title = "Bienvenido a StageKeysLive",
            subtitle = "Tu estación de trabajo para piano en vivo.\nConciertos, patches y samplers SF2 en tu bolsillo."
        ),
        OnboardingSlide(
            icon = TablerIcons.Music,
            iconTint = AccentWarmYellow,
            title = "Organiza tu setlist",
            subtitle = "Crea un Concierto para cada presentación.\nDentro, guarda tantos Patches como necesites: cada uno recuerda tus sonidos, canales y configuración completa."
        ),
        OnboardingSlide(
            icon = TablerIcons.Adjustments,
            iconTint = AccentPurple,
            title = "Hasta 8 canales simultáneos",
            subtitle = "Asigna un archivo SF2 a cada canal, ajusta volumen, reverb, chorus y rango de teclado.\nCambia de patch en escena sin interrupciones."
        ),
        OnboardingSlide(
            icon = TablerIcons.DeviceGamepad,
            iconTint = AccentNeonGreen,
            title = "Listo para el escenario",
            subtitle = "Conecta tu teclado MIDI, selecciona tu patch y toca.\nRespaldo automático en Google Drive incluido."
        )
    )

    AppBackground(glowOpacityFactor = 0.5f) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Animated Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it })
                                .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it })
                        } else {
                            (fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { -it })
                                .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { it })
                        }
                    },
                    label = "OnboardingSlideAnimation"
                ) { page ->
                    val slide = slides.getOrElse(page) { slides[0] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Left Icon Circle
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(slide.iconTint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = slide.icon,
                                contentDescription = null,
                                tint = slide.iconTint,
                                modifier = Modifier.size(62.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(36.dp))

                        // Right Content Column
                        Column(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = slide.title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextLight
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = slide.subtitle,
                                fontSize = 14.5.sp,
                                color = TextDark,
                                lineHeight = 21.sp
                            )
                        }
                    }
                }
            }

            // Bottom Fixed Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Saltar Button
                if (currentPage < totalPages - 1) {
                    TextButton(onClick = onFinish) {
                        Text(
                            text = "Saltar",
                            color = TextDark,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(72.dp))
                }

                // Center: Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isSelected = index == currentPage
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) AccentSky else OutlineVariant)
                        )
                    }
                }

                // Right: Siguiente / ¡Empezar! Button
                Button(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                        } else {
                            onFinish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentSky,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (currentPage < totalPages - 1) "Siguiente" else "¡Empezar!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )
                }
            }
        }
    }
}
