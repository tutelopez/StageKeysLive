package com.midi.mainstage

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
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
    onSettingsClick: () -> Unit
) {
    // Saludo dinÃƒÂ¡mico segÃƒÂºn hora del dÃƒÂ­a usando expect/actual (100% nativo)
    val greeting = remember {
        when (getCurrentHourOfDay()) {
            in 5..11  -> "Buenos dÃƒÂ­as, hora de tocar Ã°Å¸Å’â€¦"
            in 12..18 -> "Buenas tardes, hora de tocar Ã°Å¸Å½Â¹"
            in 19..23 -> "Buenas noches, hora de tocar Ã°Å¸Å’â„¢"
            else      -> "Hora de tocar Ã°Å¸Å½Â¹"  // 0Ã¢â‚¬â€œ4 AM
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to DarkBackground,
                        0.45f to DarkPanel,
                        1.0f to SurfaceElevated
                    )
                )
            )
            .padding(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            // Header
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)
            ) {
                // Brand row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(AccentSky, AccentPurple)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "SK",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "STAGEKEYS LIVE",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onSettingsClick) {
                        Icon(TablerIcons.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Dynamic greeting Ã¢â‚¬â€ primary hierarchy
                Text(
                    text = greeting,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 0.sp
                )
            }

            // Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onCreateConcertClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = DarkBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.Plus, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuevo Concierto", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onOpenLastConcertClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.PlayerPlay, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir ÃƒÅ¡ltimo", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.height(56.dp).weight(1f)
                ) {
                    Icon(TablerIcons.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importar Concierto", fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "TUS CONCIERTOS",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (concerts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No hay conciertos creados.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(concerts) { concert ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectConcert(concert) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(TablerIcons.Music, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(concert.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("${concert.patches.size} Patches", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                                }
                                IconButton(onClick = { onEditConcertClick(concert) }) {
                                    Icon(TablerIcons.Edit, contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onExportConcertClick(concert) }) {
                                    Icon(TablerIcons.Share, contentDescription = "Exportar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteConcert(concert) }) {
                                    Icon(TablerIcons.Trash, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
