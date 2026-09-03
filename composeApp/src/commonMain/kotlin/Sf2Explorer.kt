package com.midi.mainstage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.launch

data class Sf2FileEntry(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long = 0L,
    val relativePath: String = ""
)

data class Sf2ExplorerState(
    val folderUri: String? = null,
    val folderName: String? = null,
    val isFolderConfigured: Boolean = false,
    val files: List<Sf2FileEntry> = emptyList(),
    val isLoading: Boolean = false,
    val previewingUri: String? = null
)

interface Sf2ExplorerController {
    val state: Sf2ExplorerState
    fun requestSelectFolder()
    fun clearFolder()
    fun refreshFiles()
    suspend fun previewFile(entry: Sf2FileEntry, synth: PlatformAudioSynth)
    fun stopPreview(synth: PlatformAudioSynth)
    suspend fun importFileForChannel(entry: Sf2FileEntry): Pair<String, String>? // returns Pair(localPath, displayName)
}

@Composable
expect fun rememberSf2ExplorerController(): Sf2ExplorerController

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        val whole = mb.toInt()
        val dec = ((mb - whole) * 10).toInt()
        "$whole.$dec MB"
    } else {
        "${kb.toInt()} KB"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sf2ExplorerDialog(
    show: Boolean,
    channelName: String,
    synth: PlatformAudioSynth,
    controller: Sf2ExplorerController,
    onDismiss: () -> Unit,
    onSelectSoundFont: (path: String, displayName: String) -> Unit
) {
    if (!show) return

    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val state = controller.state

    val filteredFiles = remember(state.files, searchQuery) {
        if (searchQuery.isBlank()) {
            state.files
        } else {
            state.files.filter { it.displayName.contains(searchQuery, ignoreCase = true) || it.relativePath.contains(searchQuery, ignoreCase = true) }
        }
    }

    Dialog(
        onDismissRequest = {
            controller.stopPreview(synth)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkPanel)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = TablerIcons.Folder,
                                contentDescription = null,
                                tint = AccentSky,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EXPLORADOR DE SOUNDFONTS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentSky
                            )
                        }
                        Text(
                            text = "Asignando sonido a: $channelName",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            controller.stopPreview(synth)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextLight)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Folder Toolbar Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, if (state.isFolderConfigured) StatusSuccess.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (state.isFolderConfigured) StatusSuccess else StatusWarning)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (state.isFolderConfigured) (state.folderName ?: "Carpeta SAF") else "Ninguna carpeta seleccionada",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isFolderConfigured) TextLight else TextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.isFolderConfigured) {
                                Text(
                                    text = "${state.files.size} archivos .sf2 encontrados",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (state.isFolderConfigured) {
                            IconButton(
                                onClick = { controller.refreshFiles() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = AccentSky, modifier = Modifier.size(20.dp))
                            }
                        }

                        Button(
                            onClick = { controller.requestSelectFolder() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.isFolderConfigured) MaterialTheme.colorScheme.surfaceVariant else AccentSky
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = TablerIcons.FolderPlus,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (state.isFolderConfigured) TextLight else Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isFolderConfigured) "Cambiar Carpeta" else "Elegir Carpeta",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isFolderConfigured) TextLight else Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar if folder configured
                if (state.isFolderConfigured && state.files.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        placeholder = { Text("Buscar por nombre de SoundFont...", fontSize = 13.sp, color = TextDark) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextDark, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextDark, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceElevated,
                            unfocusedContainerColor = SurfaceElevated,
                            focusedBorderColor = AccentSky.copy(alpha = 0.6f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                            focusedTextColor = TextLight,
                            unfocusedTextColor = TextLight
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Main Content List / Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when {
                        !state.isFolderConfigured -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(AccentSky.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = TablerIcons.Folders,
                                        contentDescription = null,
                                        tint = AccentSky,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Explorador de SoundFonts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Selecciona una carpeta para explorar y previsualizar tus archivos .sf2 en tiempo real (almacenamiento local o Google Drive vía SAF).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    fontSize = 12.sp,
                                    modifier = Modifier.widthIn(max = 440.dp)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { controller.requestSelectFolder() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Icon(TablerIcons.FolderPlus, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Elegir Carpeta de SF2", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        state.isLoading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = AccentSky, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Escaneando archivos SoundFont .sf2...", color = TextDark, fontSize = 13.sp)
                            }
                        }

                        state.files.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(TablerIcons.Music, contentDescription = null, tint = TextDark, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No se encontraron archivos .sf2", style = MaterialTheme.typography.titleSmall, color = TextLight, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Asegúrate de que la carpeta contenga archivos SoundFont con extensión .sf2", style = MaterialTheme.typography.bodySmall, color = TextDark, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(onClick = { controller.requestSelectFolder() }) {
                                    Text("Elegir otra carpeta", color = AccentSky)
                                }
                            }
                        }

                        filteredFiles.isEmpty() -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = TextDark, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No hay coincidencias con \"$searchQuery\"", color = TextDark, fontSize = 13.sp)
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredFiles, key = { it.uri }) { entry ->
                                    val isPreviewing = state.previewingUri == entry.uri
                                    val sizeStr = formatFileSize(entry.sizeBytes)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPreviewing) AccentSky.copy(alpha = 0.12f) else SurfaceElevated)
                                            .border(
                                                1.dp,
                                                if (isPreviewing) AccentSky.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.05f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                coroutineScope.launch {
                                                    controller.previewFile(entry, synth)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left Play/Preview Button + Info
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Play icon badge
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isPreviewing) AccentSky else Color.White.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPreviewing) TablerIcons.Volume else TablerIcons.PlayerPlay,
                                                    contentDescription = "Previsualizar",
                                                    tint = if (isPreviewing) Color.Black else TextLight,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.displayName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isPreviewing) AccentSky else TextLight,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (entry.relativePath.isNotEmpty()) {
                                                        Text(
                                                            text = "📁 ${entry.relativePath} • ",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextDark,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    if (sizeStr.isNotEmpty()) {
                                                        Text(
                                                            text = sizeStr,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = TextDark,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    if (isPreviewing) {
                                                        Text(
                                                            text = " • 🎵 Previsualizando...",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = AccentSky,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Action Button: "Usar este SF2"
                                        Button(
                                            onClick = {
                                                if (!isImporting) {
                                                    isImporting = true
                                                    controller.stopPreview(synth)
                                                    coroutineScope.launch {
                                                        val result = controller.importFileForChannel(entry)
                                                        isImporting = false
                                                        if (result != null) {
                                                            onSelectSoundFont(result.first, result.second)
                                                            onDismiss()
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AccentSky
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = TablerIcons.Check,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Usar este SF2",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Sf2FolderSettingsScreen(
    controller: Sf2ExplorerController,
    synth: PlatformAudioSynth? = null
) {
    val state = controller.state
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CARPETA DE SOUNDFONTS (EXPLORADOR SF2)",
                style = MaterialTheme.typography.titleMedium,
                color = AccentSky,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Configura la carpeta principal donde almacenas tus librerías SoundFont (.sf2). El explorador indexará automáticamente los archivos y subcarpetas para previsualizarlos y cargarlos en tus canales.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDark,
            fontSize = 11.5.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Card
        val isConfigured = state.isFolderConfigured
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isConfigured) StatusSuccess else StatusWarning)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isConfigured) "CARPETA CONFIGURADA" else "SIN CONFIGURAR",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isConfigured) StatusSuccess else StatusWarning,
                            fontSize = 12.sp
                        )
                    }

                    if (isConfigured) {
                        Text(
                            text = "${state.files.size} archivos encontrados",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDark,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isConfigured) {
                    Text(
                        text = state.folderName ?: "Carpeta SAF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextLight
                    )
                } else {
                    Text(
                        text = "No has seleccionado ninguna carpeta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { controller.requestSelectFolder() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConfigured) MaterialTheme.colorScheme.surfaceVariant else AccentSky
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = TablerIcons.FolderPlus,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isConfigured) TextLight else Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isConfigured) "Cambiar Carpeta" else "Elegir Carpeta",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isConfigured) TextLight else Color.Black
                        )
                    }

                    if (isConfigured) {
                        OutlinedButton(
                            onClick = { controller.refreshFiles() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refrescar", tint = AccentSky, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refrescar", color = AccentSky, style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = { controller.clearFolder() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError)
                        ) {
                            Icon(TablerIcons.Trash, contentDescription = "Desvincular", tint = StatusError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Desvincular", color = StatusError, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
