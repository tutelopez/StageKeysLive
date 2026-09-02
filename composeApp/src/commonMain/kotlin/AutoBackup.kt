package com.midi.mainstage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.launch

data class AutoBackupState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val lastBackupTimestamp: Long? = null,
    val isConfigured: Boolean = false,
    val isBackingUp: Boolean = false
)

interface AutoBackupController {
    val state: AutoBackupState
    fun requestSelectFolder()
    fun clearFolder()
    suspend fun backupToFolder(concerts: List<Concert>): Result<Unit>
}

@Composable
expect fun rememberAutoBackupController(): AutoBackupController

@Composable
fun AutoBackupSettingsScreen(
    controller: AutoBackupController,
    concerts: List<Concert>,
    onShowSnackbar: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var localIsBackingUp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RESPALDO AUTOMÁTICO (SAF / GOOGLE DRIVE)",
                style = MaterialTheme.typography.titleMedium,
                color = AccentSky,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Configura una carpeta en almacenamiento local o Google Drive para respaldar automáticamente conciertos, patches y soundfonts.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDark,
            fontSize = 11.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Card
        val isConfigured = controller.state.isConfigured
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated.copy(alpha = 0.85f))
                .border(
                    1.dp,
                    if (isConfigured) StatusSuccess.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp)
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isConfigured) StatusSuccess else TextDark)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConfigured) "Respaldo Activo" else "No configurado",
                            color = if (isConfigured) StatusSuccess else TextDark,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isConfigured) {
                        Icon(
                            imageVector = TablerIcons.Check,
                            contentDescription = "Configurado",
                            tint = StatusSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isConfigured) {
                    // Folder name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TablerIcons.Folder,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Carpeta: ", color = TextDark, fontSize = 12.sp)
                        Text(
                            text = controller.state.folderName ?: "Carpeta SAF",
                            color = TextLight,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Last Backup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TablerIcons.History,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Último respaldo: ", color = TextDark, fontSize = 12.sp)
                        Text(
                            text = controller.state.lastBackupTimestamp?.let { formatTimestamp(it) } ?: "Pendiente (se guardará al modificar)",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = "Elige una carpeta donde guardar el respaldo. Cada vez que edites un concierto o canal, StageKeys generará un archivo ZIP con todos tus datos en segundo plano.",
                        color = TextDark,
                        fontSize = 11.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isConfigured) {
                        Button(
                            onClick = { controller.requestSelectFolder() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentSky,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = TablerIcons.FolderPlus,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Elegir carpeta de respaldo automático", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Change folder button
                        OutlinedButton(
                            onClick = { controller.requestSelectFolder() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextLight
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = TablerIcons.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Cambiar carpeta", fontSize = 11.5.sp)
                        }

                        // Backup Now button
                        val backingUp = localIsBackingUp || controller.state.isBackingUp
                        Button(
                            onClick = {
                                if (!backingUp) {
                                    localIsBackingUp = true
                                    coroutineScope.launch {
                                        val result = controller.backupToFolder(concerts)
                                        localIsBackingUp = false
                                        result.onSuccess {
                                            onShowSnackbar("¡Respaldo completado con éxito!")
                                        }.onFailure { err ->
                                            onShowSnackbar("Error al respaldar: ${err.message ?: "Permiso denegado"}")
                                        }
                                    }
                                }
                            },
                            enabled = !backingUp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentSky,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (backingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.Black
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Guardando...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = TablerIcons.DeviceFloppy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Respaldar ahora", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Deactivate button
                        OutlinedButton(
                            onClick = { controller.clearFolder() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StatusError
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = TablerIcons.Trash,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Desactivar", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info Card about Google Drive & SAF
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161A26))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TablerIcons.InfoCircle,
                        contentDescription = null,
                        tint = AccentSky,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "¿Cómo usar Google Drive?",
                        color = TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Al pulsar 'Elegir carpeta', el explorador de archivos de Android te permite seleccionar cualquier carpeta de tu dispositivo o de tu cuenta de Google Drive en el panel lateral. El permiso persiste entre reinicios y guarda una copia actualizada automáticamente en segundo plano.",
                    color = TextDark,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
