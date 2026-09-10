package com.tutelopezmusic.stagekeyslive

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun DashboardScreen(
    concerts: List<Concert>,
    onCreateConcertClick: () -> Unit,
    onEditConcertClick: (Concert) -> Unit,
    onOpenLastConcertClick: () -> Unit,
    onSelectConcert: (Concert) -> Unit,
    onDeleteConcert: (Concert) -> Unit,
    onExportConcertClick: (Concert) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    userProfile: GoogleUserProfile? = null
) {
    val hour = getCurrentHourOfDay()
    val (baseGreeting, greetingEmoji) = remember(hour) {
        when (hour) {
            in 5..11  -> Pair("Buenos días", "🌅")
            in 12..18 -> Pair("Buenas tardes", "🎹")
            in 19..23 -> Pair("Buenas noches", "🌙")
            else      -> Pair("Buenas noches", "🎹")
        }
    }

    val greetingEyebrow = remember(baseGreeting, userProfile) {
        val name = userProfile?.firstName ?: userProfile?.displayName
        if (!name.isNullOrBlank()) {
            "$baseGreeting, $name"
        } else {
            baseGreeting
        }
    }

    val scrollState = rememberScrollState()

    AppBackground(
        modifier = Modifier.fillMaxSize(),
        glowOpacityFactor = 0.65f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── HEADER / WORDMARK ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Glowing SK Monogram (36dp)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .shadow(
                                elevation = 18.dp,
                                shape = RoundedCornerShape(11.dp),
                                ambientColor = AccentSky.copy(alpha = 0.45f),
                                spotColor = AccentSky.copy(alpha = 0.55f)
                            )
                            .clip(RoundedCornerShape(11.dp))
                            .background(
                                Brush.linearGradient(listOf(AccentSky, AccentPurple))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SK",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Wordmark
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                append("STAGE")
                            }
                            withStyle(SpanStyle(color = AccentSky, fontWeight = FontWeight.ExtraBold)) {
                                append("KEYS")
                            }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                append(" LIVE")
                            }
                        },
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (userProfile != null) {
                        UserAvatar(
                            photoUrl = userProfile.photoUrl,
                            displayName = userProfile.displayName,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated.copy(alpha = 0.75f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = TablerIcons.Settings,
                            contentDescription = "Ajustes",
                            tint = TextDark,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            // ─── GREETING ───────────────────────────────────────────────────
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = greetingEyebrow,
                    color = AccentSky,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Hora de tocar $greetingEmoji",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
            }

            // ─── ACTIONS (PRIMARY + SECONDARY ROW) ───────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Action: Nuevo Concierto
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = AccentSky.copy(alpha = 0.25f),
                            spotColor = AccentSky.copy(alpha = 0.35f)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    AccentSky.copy(alpha = 0.24f),
                                    AccentPurple.copy(alpha = 0.16f)
                                )
                            )
                        )
                        .border(1.dp, AccentSky.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                        .clickable(onClick = onCreateConcertClick)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Plus Icon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .shadow(
                                    elevation = 12.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = AccentSky.copy(alpha = 0.5f),
                                    spotColor = AccentSky.copy(alpha = 0.6f)
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(listOf(AccentSky, AccentPurple))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                TablerIcons.Plus,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Nuevo Concierto",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Arma un set desde cero",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                // Secondary Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Abrir Último
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceElevated.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenLastConcertClick)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(DarkPanel)
                                    .border(1.dp, OutlineVariant, RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    TablerIcons.PlayerPlay,
                                    contentDescription = null,
                                    tint = AccentMint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "Abrir Último",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Importar Concierto
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceElevated.copy(alpha = 0.65f))
                            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                            .clickable(onClick = onImportClick)
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(DarkPanel)
                                    .border(1.dp, OutlineVariant, RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    TablerIcons.Download,
                                    contentDescription = null,
                                    tint = AccentCoral,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "Importar Concierto",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // ─── RECENT CONCERTS SECTION ─────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TUS CONCIERTOS",
                        color = TextDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    // Count Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${concerts.size}",
                            color = TextDark,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (concerts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = AccentSky.copy(alpha = 0.15f),
                                spotColor = AccentPurple.copy(alpha = 0.25f)
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        SurfaceElevated.copy(alpha = 0.90f),
                                        DarkPanel.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .border(1.dp, AccentSky.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Glowing Piano / Music Icon
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .shadow(
                                        elevation = 14.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = AccentSky.copy(alpha = 0.45f),
                                        spotColor = AccentPurple.copy(alpha = 0.55f)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(listOf(AccentSky, AccentPurple))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TablerIcons.Music,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Text(
                                text = "¡Bienvenido a StageKeysLive! 🎹",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Aún no tienes conciertos creados. Diseña tu primer set con capas de sonido, divisiones de teclado y efectos en tiempo real.",
                                color = TextDark,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Primary Big CTA: Crear mi primer concierto
                            Button(
                                onClick = onCreateConcertClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .shadow(
                                        elevation = 10.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = AccentSky.copy(alpha = 0.35f),
                                        spotColor = AccentPurple.copy(alpha = 0.45f)
                                    ),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(listOf(AccentSky, AccentPurple)),
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(TablerIcons.Plus, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                                        Text(
                                            text = "Crear mi primer concierto",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Secondary Option: O importar concierto
                            TextButton(
                                onClick = onImportClick,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(TablerIcons.Download, contentDescription = null, tint = AccentSky, modifier = Modifier.size(13.dp))
                                    Text(
                                        text = "O importar un concierto (.skpkg)",
                                        color = AccentSky,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        concerts.forEachIndexed { index, concert ->
                            // Rotating cover accents (Sky, Purple, Coral)
                            val (coverColor, coverBg) = when (index % 3) {
                                0 -> Pair(
                                    AccentSky,
                                    Brush.linearGradient(
                                        listOf(AccentSky.copy(alpha = 0.3f), AccentSky.copy(alpha = 0.08f))
                                    )
                                )
                                1 -> Pair(
                                    AccentPurple,
                                    Brush.linearGradient(
                                        listOf(AccentPurple.copy(alpha = 0.3f), AccentPurple.copy(alpha = 0.08f))
                                    )
                                )
                                else -> Pair(
                                    AccentCoral,
                                    Brush.linearGradient(
                                        listOf(AccentCoral.copy(alpha = 0.3f), AccentCoral.copy(alpha = 0.08f))
                                    )
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = coverColor.copy(alpha = 0.15f),
                                        spotColor = coverColor.copy(alpha = 0.2f)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkPanel.copy(alpha = 0.72f))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 11.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Main Clickable Area: Cover Badge + Info
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onSelectConcert(concert) },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Cover Badge
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .shadow(
                                                    elevation = 8.dp,
                                                    shape = RoundedCornerShape(11.dp),
                                                    ambientColor = coverColor.copy(alpha = 0.25f),
                                                    spotColor = coverColor.copy(alpha = 0.35f)
                                                )
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(coverBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                TablerIcons.Music,
                                                contentDescription = null,
                                                tint = coverColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = concert.name,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            val patchCount = concert.patches.size
                                            Text(
                                                text = if (patchCount == 1) "1 patch" else "$patchCount patches",
                                                color = TextDark,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Actions Mini
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onEditConcertClick(concert) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                TablerIcons.Edit,
                                                contentDescription = "Editar",
                                                tint = TextDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onExportConcertClick(concert) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                TablerIcons.Share,
                                                contentDescription = "Exportar",
                                                tint = TextDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteConcert(concert) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                TablerIcons.Trash,
                                                contentDescription = "Eliminar",
                                                tint = StatusError,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── FOOTER HINT ─────────────────────────────────────────────────
            Text(
                text = "StageKeysLive · listo para tocar",
                color = TextDark.copy(alpha = 0.5f),
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }
}
