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
    googleDriveService: GoogleDriveService,
    concerts: List<Concert>,
    onRestoreConcerts: (List<Concert>) -> Unit,
    onShowSnackbar: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var localIsBackingUp by remember { mutableStateOf(false) }

    // Google Drive dialog states
    var showDriveRestoreDialog by remember { mutableStateOf(false) }
    var showAccountChooserDialog by remember { mutableStateOf(false) }
    var detectedAccounts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingDriveBackups by remember { mutableStateOf(false) }
    var driveBackupsList by remember { mutableStateOf<List<DriveBackupItem>>(emptyList()) }
    var selectedBackupToRestore by remember { mutableStateOf<DriveBackupItem?>(null) }
    var isRestoringFromDrive by remember { mutableStateOf(false) }

    val driveState = googleDriveService.state

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ─── GOOGLE DRIVE CLOUD BACKUP SECTION ─────────────────────────────
        Text(
            text = "CUENTA DE GOOGLE & GOOGLE DRIVE",
            style = MaterialTheme.typography.titleMedium,
            color = AccentSky,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Inicia sesión con Google para respaldar automáticamente tus conciertos y soundfonts en tu propia nube (Google Drive).",
            style = MaterialTheme.typography.bodySmall,
            color = TextDark,
            fontSize = 11.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated.copy(alpha = 0.85f))
                .border(
                    1.dp,
                    if (driveState.isSignedIn) StatusSuccess.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                    RoundedCornerShape(10.dp)
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!driveState.isSignedIn) {
                    // Not signed in state
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
                                    .background(TextDark)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sin sesión iniciada",
                                color = TextDark,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "El respaldo en la nube es 100% opcional. Al iniciar sesión, la app creará una carpeta privada 'StageKeysLive Backups' en tu Drive sin acceder al resto de tus archivos.",
                        color = TextDark,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val accs = googleDriveService.getDeviceAccounts()
                            if (accs.isNotEmpty()) {
                                detectedAccounts = accs
                                showAccountChooserDialog = true
                            } else {
                                googleDriveService.signIn(
                                    onSuccess = { profile ->
                                        onShowSnackbar("¡Bienvenido, ${profile.firstName ?: profile.displayName}!")
                                    },
                                    onError = { err ->
                                        onShowSnackbar(err)
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = TablerIcons.BrandGoogle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Iniciar sesión con Google", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Signed in state
                    val user = driveState.user
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                photoUrl = user?.photoUrl,
                                displayName = user?.displayName,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = user?.displayName ?: "Usuario Google",
                                    color = TextLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = user?.email ?: "",
                                    color = TextDark,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                googleDriveService.signOut()
                                onShowSnackbar("Sesión cerrada")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusError.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Cerrar sesión", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud Last Backup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = TablerIcons.CloudUpload,
                            contentDescription = null,
                            tint = AccentSky,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Último respaldo en la nube: ", color = TextDark, fontSize = 12.sp)
                        Text(
                            text = driveState.lastCloudBackupTimestamp?.let { formatTimestamp(it) } ?: "Ninguno todavía",
                            color = TextLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Cloud Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!driveState.isBackingUp) {
                                    coroutineScope.launch {
                                        val res = googleDriveService.backupNow(concerts)
                                        res.onSuccess {
                                            onShowSnackbar("¡Copia subida a Google Drive!")
                                        }.onFailure { err ->
                                            onShowSnackbar("Error al subir a Drive: ${err.message}")
                                        }
                                    }
                                }
                            },
                            enabled = !driveState.isBackingUp,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentSky,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (driveState.isBackingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Subiendo a Drive...", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(imageVector = TablerIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Respaldar ahora en Drive", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showDriveRestoreDialog = true
                                isLoadingDriveBackups = true
                                coroutineScope.launch {
                                    val res = googleDriveService.listBackups()
                                    isLoadingDriveBackups = false
                                    res.onSuccess { list ->
                                        driveBackupsList = list
                                    }.onFailure { err ->
                                        onShowSnackbar("Error al leer Drive: ${err.message}")
                                    }
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = TablerIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar desde Drive", fontSize = 11.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ─── LOCAL SAF BACKUP SECTION ─────────────────────────────────────
        Text(
            text = "RESPALDO LOCAL (CARPETA SAF)",
            style = MaterialTheme.typography.titleMedium,
            color = AccentSky,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Guarda copias automáticas en cualquier carpeta del almacenamiento interno de tu dispositivo.",
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
                            text = if (isConfigured) "Respaldo Local Activo" else "No configurado",
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
                        Text("Último respaldo local: ", color = TextDark, fontSize = 12.sp)
                        Text(
                            text = controller.state.lastBackupTimestamp?.let { formatTimestamp(it) } ?: "Pendiente (se guardará al modificar)",
                            color = TextLight,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = "Elige una carpeta local donde guardar el respaldo. Cada vez que edites un concierto o canal, StageKeys generará un archivo ZIP con todos tus datos en segundo plano.",
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
                            Text("Elegir carpeta local", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                            onShowSnackbar("¡Respaldo local completado con éxito!")
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
    }

    // ─── DIALOG: RESTORE FROM GOOGLE DRIVE ─────────────────────────────────
    if (showDriveRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringFromDrive) showDriveRestoreDialog = false
            },
            title = {
                Text(
                    text = "Respaldos en Google Drive",
                    color = TextLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isLoadingDriveBackups) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentSky)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Cargando respaldos...", color = TextLight, fontSize = 13.sp)
                        }
                    } else if (driveBackupsList.isEmpty()) {
                        Text(
                            text = "No se encontraron respaldos en tu carpeta 'StageKeysLive Backups'.",
                            color = TextDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Text(
                            text = "Selecciona el respaldo que deseas restaurar:",
                            color = TextDark,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        driveBackupsList.forEach { item ->
                            val isSelected = selectedBackupToRestore?.id == item.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AccentSky.copy(alpha = 0.2f) else SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentSky else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedBackupToRestore = item }
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = TextLight,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Fecha: ${item.createdTimeFormatted} • Tamaño: ${item.sizeFormatted}",
                                            color = TextDark,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = TablerIcons.Check,
                                            contentDescription = null,
                                            tint = AccentSky,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val backup = selectedBackupToRestore
                        if (backup != null && !isRestoringFromDrive) {
                            isRestoringFromDrive = true
                            coroutineScope.launch {
                                val result = googleDriveService.restoreBackup(backup.id)
                                isRestoringFromDrive = false
                                result.onSuccess { restoredList ->
                                    onRestoreConcerts(restoredList)
                                    showDriveRestoreDialog = false
                                    selectedBackupToRestore = null
                                    onShowSnackbar("¡Conciertos y datos restaurados exitosamente!")
                                }.onFailure { err ->
                                    onShowSnackbar("Error al restaurar: ${err.message}")
                                }
                            }
                        }
                    },
                    enabled = selectedBackupToRestore != null && !isRestoringFromDrive,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky, contentColor = Color.Black)
                ) {
                    if (isRestoringFromDrive) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restaurando...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Restaurar Seleccionado", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDriveRestoreDialog = false },
                    enabled = !isRestoringFromDrive
                ) {
                    Text("Cancelar", color = TextDark)
                }
            },
            containerColor = DarkPanel
        )
    }

    // ─── DIALOG: CHOOSE GOOGLE ACCOUNT ────────────────────────────────────
    if (showAccountChooserDialog) {
        AlertDialog(
            onDismissRequest = { showAccountChooserDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = TablerIcons.BrandGoogle, contentDescription = null, tint = AccentSky, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Selecciona tu cuenta de Google",
                        color = TextLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Elige una cuenta para sincronizar con Google Drive:",
                        color = TextDark,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    detectedAccounts.forEach { email ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .clickable {
                                    showAccountChooserDialog = false
                                    googleDriveService.signInWithEmail(
                                        email = email,
                                        onSuccess = { profile ->
                                            onShowSnackbar("¡Bienvenido, ${profile.firstName ?: profile.displayName}!")
                                        },
                                        onError = { err ->
                                            onShowSnackbar(err)
                                        }
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(AccentSky.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = email.firstOrNull()?.uppercase() ?: "G",
                                        color = AccentSky,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = email,
                                    color = TextLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option to use system account chooser
                    OutlinedButton(
                        onClick = {
                            showAccountChooserDialog = false
                            googleDriveService.signIn(
                                onSuccess = { profile ->
                                    onShowSnackbar("¡Bienvenido, ${profile.firstName ?: profile.displayName}!")
                                },
                                onError = { err ->
                                    onShowSnackbar(err)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextLight),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Usar otra cuenta de Google...", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountChooserDialog = false }) {
                    Text("Cancelar", color = TextDark)
                }
            },
            containerColor = DarkPanel
        )
    }
}
