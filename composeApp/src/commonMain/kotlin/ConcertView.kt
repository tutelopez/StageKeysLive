package com.tutelopezmusic.stagekeyslive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConcertViewScreen(
    concert: Concert,
    selectedPatchIndex: Int,
    onSelectPatch: (Int) -> Unit,
    onAddPatchClick: () -> Unit,
    onEditPatchClick: (PatchState) -> Unit,
    onDeletePatch: (PatchState) -> Unit,
    onExportPatchClick: (PatchState) -> Unit,
    onImportPatchClick: () -> Unit,
    onToggleFavorite: (PatchState) -> Unit = {},
    onMovePatch: (Int, Int) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPanicClick: () -> Unit,
    performanceStats: PerformanceStats?,
    batteryLevel: Int,
    batteryCharging: Boolean,
    audioDiagnostics: String,
    currentConnectedDevices: List<String>,
    midiActivityIndicator: Boolean,

    // Metronome & Recording
    metronomeOn: Boolean,
    onMetronomeToggle: () -> Unit,
    metronomeBpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    onTapTempo: () -> Unit,
    metronomeVolume: Float,
    onMetronomeVolumeChange: (Float) -> Unit,
    metronomeTick: Boolean,
    isRecording: Boolean,
    onRecordToggle: () -> Unit,
    isPlayingRecording: Boolean,
    onPlayRecordingClick: () -> Unit,
    hasRecording: Boolean = false,
    onExportMidiClick: () -> Unit = {},

    // Channels logic
    onVolumeChange: (Int, Float) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onAddChannelClick: () -> Unit,
    onChannelGearClick: (ChannelStripState) -> Unit,
    onReverbChange: (Int, Float) -> Unit = { _, _ -> },
    onChorusChange: (Int, Float) -> Unit = { _, _ -> },
    onCutoffChange: (Int, Float) -> Unit = { _, _ -> },
    midiMappings: Map<Int, MidiTarget> = emptyMap(),

    // Master output
    masterVolume: Float,
    masterPan: Float = 0.5f,
    isMasterLimiterActive: Boolean = false,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterPanChange: (Float) -> Unit = {},
    masterVuLevel: Float,

    // Pad Engine
    padEnabled: Boolean,
    onPadEnabledChange: (Boolean) -> Unit,
    padVolume: Float,
    onPadVolumeChange: (Float) -> Unit,
    padBank: String,
    onPadBankChange: (String) -> Unit,
    availablePadBanks: List<String>,
    activePadNote: Int?,
    onPadNoteToggle: (Int) -> Unit,

    // Keyboard & Expression
    activeNote: Int?,
    onNoteDown: (Int) -> Unit,
    onNoteUp: (Int) -> Unit,
    pitchBend: Animatable<Float, *>,
    modulation: Animatable<Float, *>,
    onModulationChange: (Float) -> Unit,
    sustainActive: Boolean,
    onSustainToggle: () -> Unit,
    vuLevels: List<Animatable<Float, *>>,
    initialStageLocked: Boolean = false
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Use BoxWithConstraints to detect screen width (KMP-safe, no LocalConfiguration needed)
    // Tablets (>=600dp) start expanded; phones start collapsed to maximize mixer space
    var userKeyboardPreference by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showPatchesDrawer by rememberSaveable { mutableStateOf(false) }
    var isPerformanceMode by rememberSaveable { mutableStateOf(false) }

    // AppBackground handles the BoxWithConstraints and gradient glows
    AppBackground(
        modifier = Modifier.fillMaxSize(),
        glowOpacityFactor = 0.5f
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeight = maxHeight
            val screenWidth = maxWidth

            // ─── PRESUPUESTO DE ALTURA Y LAYOUT BUDGET (Puntos 1, 2, 3, 5) ─────────────
            val isCompactScreen = screenHeight < 500.dp || screenWidth < 900.dp
            val canExpandKeyboard = screenHeight >= 760.dp
            val isKeyboardVisible = userKeyboardPreference ?: (if (isCompactScreen) false else canExpandKeyboard)
            val areStripsCondensed = if (isCompactScreen) true else if (isKeyboardVisible) screenHeight < 760.dp else screenHeight < 640.dp
            val arePadsCollapsed = isCompactScreen

            // Stage Lock (Bloqueo de escenario en vivo - NO persistido)
            var isStageLocked by remember { mutableStateOf(initialStageLocked) }

            // Datos existentes para chips de TopBar
            val currentPatch = concert.patches.getOrNull(selectedPatchIndex)
            val semitones = currentPatch?.transposeSemitones ?: 0
            val tonalityName = when (((semitones % 12) + 12) % 12) {
                0 -> "C"; 1 -> "C#"; 2 -> "D"; 3 -> "D#"; 4 -> "E"; 5 -> "F"
                6 -> "F#"; 7 -> "G"; 8 -> "G#"; 9 -> "A"; 10 -> "A#"; 11 -> "B"
                else -> "C"
            }
            val tonalityText = "KEY: $tonalityName${if (semitones > 0) " (+$semitones)" else if (semitones < 0) " ($semitones)" else ""}"
            val activeLayers = concert.channels.count { !it.isMuted }
            val layerCountText = "$activeLayers ${if (activeLayers == 1) "CAPA" else "CAPAS"}"
            val prevPatchIndex = if (selectedPatchIndex > 0) selectedPatchIndex - 1 else null
            val nextPatchIndex = if (selectedPatchIndex < concert.patches.size - 1) selectedPatchIndex + 1 else null

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (isCompactScreen) 4.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 4.dp else 8.dp)
            ) {

                // ─── TOP BAR ────────────────────────────────────────────────────────
                val nextPatchName = if (concert.patches.size > 1) {
                    concert.patches.getOrNull((selectedPatchIndex + 1) % concert.patches.size)?.name
                } else null

                TopBar(
                    concertName = concert.name,
                    nextPatchName = nextPatchName,
                    isPerformanceMode = isPerformanceMode,
                    onPerformanceModeToggle = { isPerformanceMode = !isPerformanceMode },
                    isCompact = isCompactScreen,
                    currentPatchName = currentPatch?.name,
                    onOpenPatchesDrawer = if (isCompactScreen) { { showPatchesDrawer = true } } else null,
                    metronomeOn = metronomeOn,
                    onMetronomeToggle = onMetronomeToggle,
                    metronomeTick = metronomeTick,
                    metronomeBpm = metronomeBpm,
                    onBpmChange = onBpmChange,
                    onBpmChangeFinished = onBpmChangeFinished,
                    onTapTempo = onTapTempo,
                    isRecording = isRecording,
                    onRecordToggle = onRecordToggle,
                    isPlayingRecording = isPlayingRecording,
                    onPlayRecordingClick = onPlayRecordingClick,
                    hasRecording = hasRecording,
                    onExportMidiClick = onExportMidiClick,
                    midiActive = midiActivityIndicator,
                    onBackClick = onBackClick,
                    onSettingsClick = { if (!isStageLocked) onSettingsClick() },
                    onPanicClick = onPanicClick,
                    performanceStats = performanceStats,
                    batteryLevel = batteryLevel,
                    batteryCharging = batteryCharging,
                    audioDiagnostics = audioDiagnostics,
                    isStageLocked = isStageLocked,
                    onStageLockChange = { isStageLocked = it },
                    tonalityText = tonalityText,
                    layerCountText = layerCountText,
                    onPrevPatch = if (prevPatchIndex != null) { { onSelectPatch(prevPatchIndex) } } else null,
                    onNextPatch = if (nextPatchIndex != null) { { onSelectPatch(nextPatchIndex) } } else null
                )

                if (isPerformanceMode) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        PerformanceModeView(
                            concert = concert,
                            selectedPatchIndex = selectedPatchIndex,
                            onSelectPatch = onSelectPatch,
                            channels = concert.channels,
                            vuLevels = vuLevels,
                            metronomeVolume = metronomeVolume,
                            onMetronomeVolumeChange = onMetronomeVolumeChange,
                            masterVolume = masterVolume,
                            masterPan = masterPan,
                            isLimiterActive = isMasterLimiterActive,
                            masterVuLevel = masterVuLevel,
                            onMasterVolumeChange = onMasterVolumeChange,
                            onMasterPanChange = onMasterPanChange,
                            onVolumeChange = onVolumeChange,
                            onMuteToggle = onMuteToggle,
                            onSoloToggle = onSoloToggle,
                            onChannelGearClick = onChannelGearClick,
                            onSettingsClick = onSettingsClick,
                            onReverbChange = onReverbChange,
                            onChorusChange = onChorusChange,
                            onCutoffChange = onCutoffChange,
                            midiMappings = midiMappings,
                            metronomeOn = metronomeOn,
                            onMetronomeToggle = onMetronomeToggle,
                            metronomeBpm = metronomeBpm,
                            onTapTempo = onTapTempo,
                            metronomeTick = metronomeTick,
                            padEnabled = padEnabled,
                            onPadEnabledChange = onPadEnabledChange,
                            padVolume = padVolume,
                            onPadVolumeChange = onPadVolumeChange,
                            padBank = padBank,
                            onPadBankChange = onPadBankChange,
                            availablePadBanks = availablePadBanks,
                            activePadNote = activePadNote,
                            onPadNoteToggle = onPadNoteToggle,
                            onPanicClick = onPanicClick
                        )
                    }
                } else {
                    if (!isCompactScreen) {
                        // ─── DESKTOP / TABLET: PATCHES PANEL + MIXER PANEL (PRIORIDAD VERTICAL COMPLETA) ───
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PatchesPanel(
                                patches = concert.patches,
                                selectedIndex = selectedPatchIndex,
                                onSelect = onSelectPatch,
                                onAdd = onAddPatchClick,
                                onEdit = onEditPatchClick,
                                onDelete = onDeletePatch,
                                onExport = onExportPatchClick,
                                onImport = onImportPatchClick,
                                onToggleFavorite = onToggleFavorite,
                                onMovePatch = onMovePatch,
                                isStageLocked = isStageLocked,
                                modifier = Modifier.width(160.dp).fillMaxHeight()
                            )

                            MixerPanel(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                channels = concert.channels,
                                vuLevels = vuLevels,
                                metronomeVolume = metronomeVolume,
                                onMetronomeVolumeChange = onMetronomeVolumeChange,
                                masterVolume = masterVolume,
                                masterPan = masterPan,
                                isLimiterActive = isMasterLimiterActive,
                                masterVuLevel = masterVuLevel,
                                onMasterVolumeChange = onMasterVolumeChange,
                                onMasterPanChange = onMasterPanChange,
                                onVolumeChange = onVolumeChange,
                                onMuteToggle = onMuteToggle,
                                onSoloToggle = onSoloToggle,
                                onAddChannelClick = onAddChannelClick,
                                onChannelGearClick = onChannelGearClick,
                                onSettingsClick = onSettingsClick,
                                onReverbChange = onReverbChange,
                                onChorusChange = onChorusChange,
                                onCutoffChange = onCutoffChange,
                                midiMappings = midiMappings,
                                scrollState = rememberScrollState(),
                                metronomeOn = metronomeOn,
                                onMetronomeToggle = onMetronomeToggle,
                                metronomeBpm = metronomeBpm,
                                onTapTempo = onTapTempo,
                                metronomeTick = metronomeTick,
                                isCondensed = areStripsCondensed,
                                isStageLocked = isStageLocked
                            )
                        }

                        // Desktop single row PadStrip
                        if (!arePadsCollapsed) {
                            PadStrip(
                                enabled = padEnabled,
                                onEnabledChange = onPadEnabledChange,
                                volume = padVolume,
                                onVolumeChange = onPadVolumeChange,
                                bank = padBank,
                                onBankChange = onPadBankChange,
                                availableBanks = availablePadBanks,
                                activePadNote = activePadNote,
                                onPadNoteToggle = onPadNoteToggle
                            )
                        }
                    } else {
                        // ─── COMPACTO (800x360dp): MEZCLADOR ANCHO COMPLETO ───────────────────
                        MixerPanel(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            channels = concert.channels,
                            vuLevels = vuLevels,
                            metronomeVolume = metronomeVolume,
                            onMetronomeVolumeChange = onMetronomeVolumeChange,
                            masterVolume = masterVolume,
                            masterPan = masterPan,
                            isLimiterActive = isMasterLimiterActive,
                            masterVuLevel = masterVuLevel,
                            onMasterVolumeChange = onMasterVolumeChange,
                            onMasterPanChange = onMasterPanChange,
                            onVolumeChange = onVolumeChange,
                            onMuteToggle = onMuteToggle,
                            onSoloToggle = onSoloToggle,
                            onAddChannelClick = onAddChannelClick,
                            onChannelGearClick = onChannelGearClick,
                            onSettingsClick = onSettingsClick,
                            onReverbChange = onReverbChange,
                            onChorusChange = onChorusChange,
                            onCutoffChange = onCutoffChange,
                            midiMappings = midiMappings,
                            scrollState = rememberScrollState(),
                            metronomeOn = metronomeOn,
                            onMetronomeToggle = onMetronomeToggle,
                            metronomeBpm = metronomeBpm,
                            onTapTempo = onTapTempo,
                            metronomeTick = metronomeTick,
                            isCondensed = true,
                            isStageLocked = isStageLocked
                        )
                    }

                    // ─── SECCIÓN TECLADO: CABECERA ADJUNTA + PANEL ────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(
                                    if (isKeyboardVisible) RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                                    else RoundedCornerShape(10.dp)
                                )
                                .background(DarkPanel.copy(alpha = 0.85f))
                                .clickable { userKeyboardPreference = !isKeyboardVisible }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isKeyboardVisible) TablerIcons.ChevronDown else TablerIcons.ChevronUp,
                                    contentDescription = if (isKeyboardVisible) "Colapsar teclado" else "Expandir teclado",
                                    tint = AccentSky,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "TECLADO",
                                    color = if (isKeyboardVisible) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }

                        if (isKeyboardVisible) {
                            val keyboardHeight = if (screenHeight < 600.dp) 110.dp else 140.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(keyboardHeight)
                                    .clipToBounds()
                            ) {
                                KeyboardPanel(
                                    channels = concert.channels,
                                    activeNote = activeNote,
                                    onNoteDown = onNoteDown,
                                    onNoteUp = onNoteUp,
                                    pitchBend = pitchBend,
                                    modulation = modulation,
                                    onModulationChange = onModulationChange,
                                    sustainActive = sustainActive,
                                    onSustainToggle = onSustainToggle,
                                    coroutineScope = coroutineScope
                                )
                            }
                        }
                    }
                }
            } // End Column

            // Drawer / Modal de Patches para modo compacto
            if (showPatchesDrawer) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { showPatchesDrawer = false }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.85f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        PatchesPanel(
                            patches = concert.patches,
                            selectedIndex = selectedPatchIndex,
                            onSelect = { idx ->
                                onSelectPatch(idx)
                                showPatchesDrawer = false
                            },
                            onAdd = onAddPatchClick,
                            onEdit = onEditPatchClick,
                            onDelete = onDeletePatch,
                            onExport = onExportPatchClick,
                            onImport = onImportPatchClick,
                            onToggleFavorite = onToggleFavorite,
                            onMovePatch = onMovePatch,
                            isStageLocked = isStageLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } // End BoxWithConstraints
    } // End AppBackground
}

// ─────────────────────────────────────────────────────────────────────────────
// PERFORMANCE MODE VIEW
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PerformanceModeView(
    concert: Concert,
    selectedPatchIndex: Int,
    onSelectPatch: (Int) -> Unit,
    channels: List<ChannelStripState>,
    vuLevels: List<Animatable<Float, *>>,
    metronomeVolume: Float,
    onMetronomeVolumeChange: (Float) -> Unit,
    masterVolume: Float,
    masterPan: Float,
    isLimiterActive: Boolean,
    masterVuLevel: Float,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterPanChange: (Float) -> Unit,
    onVolumeChange: (Int, Float) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onChannelGearClick: (ChannelStripState) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onReverbChange: (Int, Float) -> Unit = { _, _ -> },
    onChorusChange: (Int, Float) -> Unit = { _, _ -> },
    onCutoffChange: (Int, Float) -> Unit = { _, _ -> },
    midiMappings: Map<Int, MidiTarget> = emptyMap(),
    metronomeOn: Boolean = false,
    onMetronomeToggle: () -> Unit = {},
    metronomeBpm: Int = 120,
    onTapTempo: () -> Unit = {},
    metronomeTick: Boolean = false,
    // Pads
    padEnabled: Boolean,
    onPadEnabledChange: (Boolean) -> Unit,
    padVolume: Float,
    onPadVolumeChange: (Float) -> Unit,
    padBank: String,
    onPadBankChange: (String) -> Unit,
    availablePadBanks: List<String>,
    activePadNote: Int?,
    onPadNoteToggle: (Int) -> Unit,
    onPanicClick: () -> Unit = {}
) {
    val currentPatch = concert.patches.getOrNull(selectedPatchIndex)
    val prevPatch = concert.patches.getOrNull(selectedPatchIndex - 1)
    val nextPatch = concert.patches.getOrNull(selectedPatchIndex + 1)
    val patchColor = concert.channels.firstOrNull { !it.isMuted }?.let { parseColorHex(it.colorHex) } ?: AccentSky
    val mixerScrollState = rememberScrollState()
    var panicBlink by remember { mutableStateOf(false) }
    LaunchedEffect(panicBlink) {
        if (panicBlink) {
            delay(300)
            panicBlink = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. HEADER: PATCH SWITCHER BANNER ─────────────────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkPanel.copy(alpha = 0.9f),
            border = BorderStroke(1.2.dp, patchColor.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().height(40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Prev button
                IconButton(
                    onClick = { if (prevPatch != null) onSelectPatch(selectedPatchIndex - 1) },
                    enabled = prevPatch != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        TablerIcons.ChevronLeft,
                        contentDescription = "Patch Anterior",
                        tint = if (prevPatch != null) AccentSky else TextSecondary.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Patch Info
                Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PATCH ${selectedPatchIndex + 1}/${concert.patches.size}".uppercase(),
                        color = patchColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = currentPatch?.name ?: "SIN PATCH",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    // Sound layer badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        concert.channels.filter { !it.isMuted }.forEach { ch ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(parseColorHex(ch.colorHex).copy(alpha = 0.2f))
                                    .border(1.dp, parseColorHex(ch.colorHex).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = ch.sf2Name.substringBefore(".sf2").uppercase(),
                                    color = parseColorHex(ch.colorHex),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Next button
                    IconButton(
                        onClick = { if (nextPatch != null) onSelectPatch(selectedPatchIndex + 1) },
                        enabled = nextPatch != null,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            TablerIcons.ChevronRight,
                            contentDescription = "Patch Siguiente",
                            tint = if (nextPatch != null) AccentSky else TextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Panic Button en Modo Ejecución
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StatusError.copy(alpha = if (panicBlink) 0.6f else 0.18f))
                            .border(1.2.dp, StatusError, RoundedCornerShape(8.dp))
                            .clickable {
                                onPanicClick()
                                panicBlink = true
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(TablerIcons.AlertTriangle, contentDescription = "Panic", tint = StatusError, modifier = Modifier.size(14.dp))
                            Text("PANIC", color = StatusError, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // ── 2. CENTER: VIRTUAL PRO MIXING SURFACE ─────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkBackground.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, BorderHairline),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Canales con scroll horizontal y barra violeta fina abajo
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 6.dp)
                            .horizontalScroll(mixerScrollState)
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        channels.forEach { chState ->
                            val levelIdx = (chState.id - 1).coerceIn(0, 7)
                            val isRevMapped = midiMappings.values.any { it is MidiTarget.ChannelReverb && it.channelIndex == chState.id - 1 }
                            val isChoMapped = midiMappings.values.any { it is MidiTarget.ChannelChorus && it.channelIndex == chState.id - 1 }
                            val isCutMapped = midiMappings.values.any { it is MidiTarget.ChannelCutoff && it.channelIndex == chState.id - 1 }
                            val isVolMapped = midiMappings.values.any { it is MidiTarget.ChannelVolume && it.channelIndex == chState.id - 1 }
                            val isMapped = isRevMapped || isChoMapped || isCutMapped || isVolMapped

                            ProChannelStrip(
                                channel = chState,
                                levelProvider = { vuLevels.getOrNull(levelIdx)?.value ?: 0f },
                                onVolumeChange = { vol -> onVolumeChange(chState.id, vol) },
                                onMuteToggle = { onMuteToggle(chState.id) },
                                onSoloToggle = { onSoloToggle(chState.id) },
                                onGearClick = { onChannelGearClick(chState) },
                                onFxClick = { onChannelGearClick(chState) },
                                onPanChange = {},
                                modifier = Modifier.width(104.dp),
                                isMidiMapped = isMapped
                            )
                        }
                    }

                    // Fade izquierdo de 16dp solo si hay scroll hacia la izquierda
                    if (mixerScrollState.value > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(DarkPanel, Color.Transparent)
                                    )
                                )
                        )
                    }

                    // Fade derecho de 16dp solo si queda contenido para scrollear
                    if (mixerScrollState.value < mixerScrollState.maxValue) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, DarkPanel)
                                    )
                                )
                        )
                    }

                    // Barra de scroll fina violeta abajo
                    FineVioletScrollbar(
                        scrollState = mixerScrollState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                // Separador vertical
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(BorderHairline)
                )

                // Metrónomo
                ProMetronomeStrip(
                    volume = metronomeVolume,
                    onVolumeChange = onMetronomeVolumeChange,
                    enabled = metronomeOn,
                    onToggle = onMetronomeToggle,
                    bpm = metronomeBpm,
                    onTapTempo = onTapTempo,
                    levelProvider = { if (metronomeTick) 0.8f else 0f },
                    modifier = Modifier.width(104.dp)
                )

                // Master anclado a la derecha fuera del scroll
                ProMasterStrip(
                    volume = masterVolume,
                    onVolumeChange = onMasterVolumeChange,
                    levelProvider = { masterVuLevel },
                    isLimiterActive = isLimiterActive,
                    onLimiterToggle = {},
                    onMidiMapClick = onSettingsClick,
                    modifier = Modifier.width(114.dp)
                )
            }
        }

        // ── 3. BOTTOM: LARGE AMBIENT PADS STRIP ───────────────────────────────
        PerformancePadStrip(
            enabled = padEnabled,
            onEnabledChange = onPadEnabledChange,
            volume = padVolume,
            onVolumeChange = onPadVolumeChange,
            bank = padBank,
            onBankChange = onPadBankChange,
            availableBanks = availablePadBanks,
            activePadNote = activePadNote,
            onPadNoteToggle = onPadNoteToggle
        )
    }
}

@Composable
private fun PerformancePadStrip(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    bank: String,
    onBankChange: (String) -> Unit,
    availableBanks: List<String>,
    activePadNote: Int?,
    onPadNoteToggle: (Int) -> Unit
) {
    Surface(
        color = DarkPanel.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Header: Bank selector + Volume + On/Off
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bank Selector
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(OutlineVariant)
                            .clickable { expanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bank.ifEmpty { "Bank A" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(TablerIcons.ChevronDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(DarkPanel)
                    ) {
                        availableBanks.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b, color = Color.White) },
                                onClick = {
                                    onBankChange(b)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Volume slider
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentSky,
                        activeTrackColor = AccentSky,
                        inactiveTrackColor = OutlineVariant
                    )
                )

                Spacer(Modifier.width(12.dp))

                // PAD ON/OFF Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (enabled) StatusSuccess.copy(alpha = 0.2f) else DarkBackground)
                        .border(1.dp, if (enabled) StatusSuccess else OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { onEnabledChange(!enabled) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "PADS ${if (enabled) "ON" else "OFF"}",
                        color = if (enabled) StatusSuccess else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.height(5.dp))

            // 12 Large Note buttons spanning full width with weight(1f)
            val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                notes.forEachIndexed { index, noteName ->
                    val isActive = activePadNote == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .then(
                                if (isActive) {
                                    Modifier.shadow(
                                        elevation = 14.dp,
                                        shape = RoundedCornerShape(9.dp),
                                        ambientColor = StatusSuccess.copy(alpha = 0.6f),
                                        spotColor = StatusSuccess.copy(alpha = 0.7f)
                                    )
                                } else Modifier
                            )
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isActive)
                                    Brush.verticalGradient(listOf(StateOk, StateOk.copy(alpha = 0.7f)))
                                else
                                    Brush.verticalGradient(listOf(SurfaceCard, SurfacePanel))
                            )
                            .border(
                                width = 1.2.dp,
                                color = if (isActive) StatusSuccess else OutlineVariant.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable(enabled = enabled) { onPadNoteToggle(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = noteName,
                            color = if (isActive) Color.White else if (enabled) TextLight else TextSecondary.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    concertName: String,
    nextPatchName: String? = null,
    isPerformanceMode: Boolean = false,
    onPerformanceModeToggle: () -> Unit = {},
    isCompact: Boolean = false,
    currentPatchName: String? = null,
    onOpenPatchesDrawer: (() -> Unit)? = null,
    metronomeOn: Boolean,
    onMetronomeToggle: () -> Unit,
    metronomeTick: Boolean,
    metronomeBpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    onTapTempo: () -> Unit,
    isRecording: Boolean,
    onRecordToggle: () -> Unit,
    isPlayingRecording: Boolean,
    onPlayRecordingClick: () -> Unit,
    hasRecording: Boolean = false,
    onExportMidiClick: () -> Unit = {},
    midiActive: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPanicClick: () -> Unit,
    performanceStats: PerformanceStats?,
    batteryLevel: Int,
    batteryCharging: Boolean,
    audioDiagnostics: String,
    isStageLocked: Boolean = false,
    onStageLockChange: (Boolean) -> Unit = {},
    tonalityText: String = "",
    layerCountText: String = "",
    onPrevPatch: (() -> Unit)? = null,
    onNextPatch: (() -> Unit)? = null
) {
    var panicBlink by remember { mutableStateOf(false) }
    LaunchedEffect(panicBlink) {
        if (panicBlink) {
            delay(300)
            panicBlink = false
        }
    }
    var showCompactMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkPanel.copy(alpha = 0.85f),
        border = BorderStroke(
            1.2.dp,
            if (isStageLocked) AccentPrimary.copy(alpha = 0.8f) else Color.Transparent
        ),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().height(if (isCompact) 42.dp else 52.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick, modifier = Modifier.size(if (isCompact) 30.dp else 36.dp)) {
                Icon(
                    TablerIcons.ArrowLeft,
                    contentDescription = "Back",
                    tint = TextLight,
                    modifier = Modifier.size(if (isCompact) 17.dp else 20.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Botones Anterior / Siguiente Parche en desktop/tablet (área >= 48dp)
            if (!isCompact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { onPrevPatch?.invoke() },
                        enabled = onPrevPatch != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            TablerIcons.ChevronLeft,
                            contentDescription = "Patch Anterior",
                            tint = if (onPrevPatch != null) AccentPrimary else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onNextPatch?.invoke() },
                        enabled = onNextPatch != null,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            TablerIcons.ChevronRight,
                            contentDescription = "Patch Siguiente",
                            tint = if (onNextPatch != null) AccentPrimary else TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            // Concert name + Current patch
            Column {
                Text(
                    text = concertName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (isCompact) 0.5.sp else 1.sp,
                        fontSize = if (isCompact) 11.sp else 12.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentPatchName != null) {
                    Text(
                        text = currentPatchName,
                        color = Color.White,
                        fontSize = if (isCompact) 12.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isCompact && currentPatchName != null && onOpenPatchesDrawer != null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentPrimary.copy(alpha = 0.25f))
                        .border(1.dp, AccentPrimary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .clickable { onOpenPatchesDrawer() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentPatchName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        TablerIcons.ChevronDown,
                        contentDescription = "Ver Patches",
                        tint = AccentLavender,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Chips de Tonalidad y Capas en desktop/tablet
            if (!isCompact) {
                if (tonalityText.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tonalityText,
                            color = AccentLavender,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (layerCountText.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderHairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = layerCountText,
                            color = AccentPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Monitor de recursos CPU/RAM con barras finas y colores de estado en desktop/tablet
            if (!isCompact) {
                ResourceMonitorWidget(stats = performanceStats)
                Spacer(Modifier.weight(1f))
            }

            // Metronome / BPM pill button
            var showMetroPopup by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkPanel)
                        .border(1.2.dp, if (metronomeOn) StateOk else AccentPrimary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .clickable { onMetronomeToggle() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$metronomeBpm BPM",
                        color = if (metronomeOn) StateOk else AccentPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                
                DropdownMenu(
                    expanded = showMetroPopup,
                    onDismissRequest = { showMetroPopup = false },
                    modifier = Modifier.background(DarkPanel)
                ) {
                    Slider(
                        value = metronomeBpm.toFloat(),
                        onValueChange = { onBpmChange(it.toInt()) },
                        onValueChangeFinished = onBpmChangeFinished,
                        valueRange = 40f..240f,
                        modifier = Modifier.width(150.dp).padding(horizontal = 16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = StateOk,
                            activeTrackColor = StateOk,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Tap Tempo button
            var tapPulse by remember { mutableStateOf(false) }
            LaunchedEffect(tapPulse) {
                if (tapPulse) {
                    delay(150)
                    tapPulse = false
                }
            }
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (tapPulse) AccentSky.copy(alpha = 0.35f) else DarkPanel)
                    .border(1.2.dp, if (tapPulse) AccentSky else OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable {
                        tapPulse = true
                        onTapTempo()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "TAP",
                    color = if (tapPulse) Color.White else AccentSky,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            if (!isPerformanceMode && !isCompact) {
                Spacer(Modifier.width(8.dp))

                // Record button (Pill)
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkPanel)
                        .border(1.2.dp, StatusError.copy(alpha = if (isRecording) 0.9f else 0.45f), RoundedCornerShape(16.dp))
                        .clickable { onRecordToggle() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StatusError)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "REC",
                            color = StatusError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Play / Stop Recording button (Pill)
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkPanel)
                        .border(
                            1.2.dp,
                            if (isPlayingRecording) StateOk else if (hasRecording) AccentPrimary.copy(alpha = 0.7f) else OutlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = hasRecording || isPlayingRecording, onClick = onPlayRecordingClick)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPlayingRecording) TablerIcons.PlayerStop else TablerIcons.PlayerPlay,
                            contentDescription = if (isPlayingRecording) "Detener" else "Reproducir Grabación",
                            tint = if (isPlayingRecording) StateOk else if (hasRecording) AccentPrimary else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isPlayingRecording) "STOP" else "PLAY",
                            color = if (isPlayingRecording) StateOk else if (hasRecording) AccentPrimary else TextSecondary.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Export as MIDI button (Pill)
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkPanel)
                        .border(
                            1.2.dp,
                            if (hasRecording) AccentPurple.copy(alpha = 0.85f) else OutlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = hasRecording, onClick = onExportMidiClick)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            TablerIcons.Share,
                            contentDescription = "Exportar como MIDI",
                            tint = if (hasRecording) AccentPurple else TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "MIDI",
                            color = if (hasRecording) AccentPurple else TextSecondary.copy(alpha = 0.35f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // MIDI Activity Indicator LED
            Box(
                modifier = Modifier
                    .height(if (isCompact) 28.dp else 32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (midiActive) StateOk.copy(alpha = 0.18f) else DarkPanel)
                    .border(1.2.dp, if (midiActive) StateOk else OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isCompact) 6.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (midiActive) StateOk else TextTertiary)
                    )
                    if (!isCompact) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "MIDI",
                            color = if (midiActive) StateOk else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // ─── STAGE LOCK BUTTON (CANDADO) ──────────────────────────────────
            var isHoldingUnlock by remember { mutableStateOf(false) }
            var unlockProgress by remember { mutableStateOf(0f) }

            LaunchedEffect(isHoldingUnlock) {
                if (isHoldingUnlock) {
                    val totalMs = 1000L
                    val intervalMs = 25L
                    var elapsed = 0L
                    while (isHoldingUnlock && elapsed < totalMs) {
                        delay(intervalMs)
                        elapsed += intervalMs
                        unlockProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                    }
                    if (isHoldingUnlock && elapsed >= totalMs) {
                        onStageLockChange(false)
                        isHoldingUnlock = false
                        unlockProgress = 0f
                    }
                } else {
                    unlockProgress = 0f
                }
            }

            Box(
                modifier = Modifier
                    .size(if (isCompact) 28.dp else 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isStageLocked) AccentPrimary.copy(alpha = 0.25f) else DarkPanel)
                    .border(
                        1.2.dp,
                        if (isStageLocked) AccentPrimary else OutlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp)
                    )
                    .pointerInput(isStageLocked) {
                        if (!isStageLocked) {
                            detectTapGestures(
                                onTap = { onStageLockChange(true) }
                            )
                        } else {
                            detectTapGestures(
                                onPress = {
                                    isHoldingUnlock = true
                                    tryAwaitRelease()
                                    isHoldingUnlock = false
                                }
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isStageLocked && unlockProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { unlockProgress },
                        modifier = Modifier.size(if (isCompact) 24.dp else 28.dp),
                        color = AccentLavender,
                        strokeWidth = 2.dp
                    )
                }
                Icon(
                    if (isStageLocked) TablerIcons.Lock else TablerIcons.LockOpen,
                    contentDescription = if (isStageLocked) "Bloqueo de escenario activo (mantén 1s para desbloquear)" else "Bloquear escenario",
                    tint = if (isStageLocked) AccentPrimary else TextSecondary,
                    modifier = Modifier.size(if (isCompact) 15.dp else 17.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Panic Button - SIEMPRE VISIBLE EN TODOS LOS TAMAÑOS
            Box(
                modifier = Modifier
                    .height(if (isCompact) 28.dp else 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StatusError.copy(alpha = if (panicBlink) 0.6f else 0.18f))
                    .border(1.5.dp, StatusError, RoundedCornerShape(8.dp))
                    .clickable { 
                        onPanicClick()
                        panicBlink = true
                    }
                    .padding(horizontal = if (isCompact) 6.dp else 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        TablerIcons.AlertTriangle,
                        contentDescription = "Panic",
                        tint = StatusError,
                        modifier = Modifier.size(if (isCompact) 15.dp else 17.dp)
                    )
                    if (!isCompact) {
                        Text(
                            text = "PANIC",
                            color = StatusError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            if (!isPerformanceMode) {
                Spacer(Modifier.width(6.dp))

                if (!isCompact) {
                    // Settings gear en desktop (deshabilitado si está bloqueado el escenario)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkPanel)
                            .border(1.dp, if (isStageLocked) OutlineVariant.copy(alpha = 0.2f) else OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isStageLocked, onClick = onSettingsClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            TablerIcons.Settings,
                            contentDescription = "Settings",
                            tint = if (isStageLocked) TextSecondary.copy(alpha = 0.3f) else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // Menú overflow "⋮" en compacto para no saturar la barra superior
                    Box {
                        IconButton(
                            onClick = { showCompactMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                TablerIcons.DotsVertical,
                                contentDescription = "Más opciones",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showCompactMenu,
                            onDismissRequest = { showCompactMenu = false },
                            modifier = Modifier.background(DarkPanel)
                        ) {
                            // Monitor de recursos en menú compacto
                            DropdownMenuItem(
                                text = {
                                    val cpu = performanceStats?.cpuPercent ?: 0
                                    val ram = performanceStats?.ramPercent ?: ((performanceStats?.ramMb ?: 0) * 100 / 512).coerceIn(0, 100)
                                    Text("CPU: $cpu% | RAM: $ram%", color = AccentLavender, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text(if (isStageLocked) "Desbloquear escenario (mantén 1s)" else "Bloquear escenario", color = AccentPrimary, fontSize = 11.sp) },
                                onClick = {
                                    showCompactMenu = false
                                    onStageLockChange(!isStageLocked)
                                },
                                leadingIcon = {
                                    Icon(if (isStageLocked) TablerIcons.Lock else TablerIcons.LockOpen, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isRecording) "Detener Grabación" else "Grabar (REC)", color = StatusError, fontSize = 11.sp) },
                                onClick = {
                                    showCompactMenu = false
                                    onRecordToggle()
                                },
                                leadingIcon = {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(StatusError))
                                }
                            )
                            if (hasRecording || isPlayingRecording) {
                                DropdownMenuItem(
                                    text = { Text(if (isPlayingRecording) "Detener Reproducción" else "Reproducir Grabación", color = StateOk, fontSize = 11.sp) },
                                    onClick = {
                                        showCompactMenu = false
                                        onPlayRecordingClick()
                                    },
                                    leadingIcon = {
                                        Icon(if (isPlayingRecording) TablerIcons.PlayerStop else TablerIcons.PlayerPlay, contentDescription = null, tint = StateOk, modifier = Modifier.size(14.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Exportar como MIDI", color = AccentPurple, fontSize = 11.sp) },
                                    onClick = {
                                        showCompactMenu = false
                                        onExportMidiClick()
                                    },
                                    leadingIcon = {
                                        Icon(TablerIcons.Share, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Ajustes", color = if (isStageLocked) TextSecondary.copy(alpha = 0.4f) else TextPrimary, fontSize = 11.sp) },
                                enabled = !isStageLocked,
                                onClick = {
                                    showCompactMenu = false
                                    onSettingsClick()
                                },
                                leadingIcon = {
                                    Icon(TablerIcons.Settings, contentDescription = null, tint = if (isStageLocked) TextSecondary.copy(alpha = 0.4f) else TextSecondary, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Performance mode toggle button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isPerformanceMode) StateOk.copy(alpha = 0.18f)
                        else DarkPanel
                    )
                    .border(
                        1.dp,
                        if (isPerformanceMode) StateOk else OutlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onPerformanceModeToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.DeviceDesktop,
                    contentDescription = if (isPerformanceMode) "Salir del modo ejecución" else "Modo ejecución",
                    tint = if (isPerformanceMode) StateOk else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ResourceMonitorWidget(
    stats: PerformanceStats?,
    modifier: Modifier = Modifier
) {
    val cpu = stats?.cpuPercent ?: 0
    val ramPercent = stats?.ramPercent ?: ((stats?.ramMb ?: 0) * 100 / 512).coerceIn(0, 100)

    val cpuColor = when {
        cpu >= 80 -> StatusError
        cpu >= 50 -> StatusWarning
        else -> StateOk
    }

    val ramColor = when {
        ramPercent >= 85 -> StatusError
        ramPercent >= 65 -> StatusWarning
        else -> AccentPrimary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SurfacePanel.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, OutlineVariant.copy(alpha = 0.45f)),
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // CPU Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CPU",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$cpu%",
                    color = cpuColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Micro CPU bar
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderHairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((cpu / 100f).coerceIn(0.06f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(cpuColor)
                    )
                }
            }

            // Separator
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(13.dp)
                    .background(OutlineVariant.copy(alpha = 0.5f))
            )

            // RAM Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "RAM",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$ramPercent%",
                    color = ramColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Micro RAM bar
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderHairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((ramPercent / 100f).coerceIn(0.06f, 1f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(ramColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val bgColor = if (active) activeColor.copy(alpha = 0.18f) else SurfaceCard
    val borderColor = if (active) activeColor else OutlineVariant
    val textColor = if (active) activeColor else TextSecondary

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PATCHES PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PatchesPanel(
    patches: List<PatchState>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (PatchState) -> Unit,
    onDelete: (PatchState) -> Unit,
    onExport: (PatchState) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier.width(160.dp).fillMaxHeight(),
    onToggleFavorite: (PatchState) -> Unit = {},
    onMovePatch: (Int, Int) -> Unit = { _, _ -> },
    isStageLocked: Boolean = false
) {
    var showFavoritesOnly by remember { mutableStateOf(false) }
    val displayPatches = if (showFavoritesOnly) patches.filter { it.isFavorite } else patches

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkPanel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PATCHES",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f)
                )
                // Favorites filter toggle button
                IconButton(
                    onClick = { showFavoritesOnly = !showFavoritesOnly },
                    modifier = Modifier.size(20.dp)
                ) {
                    Text(
                        if (showFavoritesOnly) "★" else "☆",
                        color = if (showFavoritesOnly) StarGold else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!isStageLocked) {
                    IconButton(onClick = onImport, modifier = Modifier.size(20.dp)) {
                        Icon(TablerIcons.Download, contentDescription = "Import", tint = TextSecondary, modifier = Modifier.size(12.dp))
                    }
                    IconButton(onClick = onAdd, modifier = Modifier.size(20.dp)) {
                        Icon(TablerIcons.Plus, contentDescription = "Add patch", tint = AccentSky, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (patches.size > 1) {
                val nextPatch = patches.getOrNull((selectedIndex + 1) % patches.size)
                Text(
                    text = "Siguiente: ${nextPatch?.name ?: "—"}",
                    color = TextSecondary.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            if (patches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(AccentSky.copy(alpha = 0.3f), AccentPurple.copy(alpha = 0.3f)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TablerIcons.Music,
                                contentDescription = null,
                                tint = AccentSky,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "Sin patches",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Agrega tu primer patch",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (!isStageLocked) {
                            Spacer(Modifier.height(2.dp))
                            SkButton(
                                onClick = onAdd,
                                modifier = Modifier.fillMaxWidth(),
                                variant = SkButtonVariant.Primary
                            ) {
                                Text("+ Agregar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(displayPatches) { patch ->
                        val actualIdx = patches.indexOf(patch)
                        val isSelected = actualIdx == selectedIndex
                        val canMoveUp = !showFavoritesOnly && actualIdx > 0
                        val canMoveDown = !showFavoritesOnly && actualIdx < patches.size - 1

                        PatchRow(
                            patch = patch,
                            isSelected = isSelected,
                            canMoveUp = canMoveUp,
                            canMoveDown = canMoveDown,
                            onClick = { onSelect(actualIdx) },
                            onEdit = { onEdit(patch) },
                            onDelete = { onDelete(patch) },
                            onExport = { onExport(patch) },
                            onToggleFavorite = { onToggleFavorite(patch) },
                            onMoveUp = { onMovePatch(actualIdx, actualIdx - 1) },
                            onMoveDown = { onMovePatch(actualIdx, actualIdx + 1) },
                            isStageLocked = isStageLocked
                        )
                    }
                }
            }

            if (!isStageLocked) {
                Spacer(Modifier.height(6.dp))

                // Bottom Buttons: + Nuevo and Importar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .clickable(onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "+ Nuevo",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceElevated)
                            .clickable(onClick = onImport),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            TablerIcons.Download,
                            contentDescription = "Importar Patch",
                            tint = AccentSky,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatchRow(
    patch: PatchState,
    isSelected: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    isStageLocked: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) AccentPrimary.copy(alpha = 0.20f) else SurfaceElevated
            )
            .border(
                width = 1.dp,
                color = if (isSelected) AccentPrimary.copy(alpha = 0.6f) else BorderHairline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Favorite Star
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFavorite),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (patch.isFavorite) "★" else "☆",
                color = if (patch.isFavorite) StarGold else TextSecondary.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patch.name,
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (patch.category.isNotBlank()) {
                Text(
                    text = patch.category,
                    color = if (isSelected) AccentLavender else TextSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isStageLocked) {
            // Menú de opciones ⋮
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        TablerIcons.DotsVertical,
                        contentDescription = "Opciones",
                        tint = if (isSelected) Color.White else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(DarkPanel)
                ) {
                    if (canMoveUp) {
                        DropdownMenuItem(
                            text = { Text("Subir", color = TextPrimary, fontSize = 11.5.sp) },
                            leadingIcon = { Icon(TablerIcons.ArrowUp, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (canMoveDown) {
                        DropdownMenuItem(
                            text = { Text("Bajar", color = TextPrimary, fontSize = 11.5.sp) },
                            leadingIcon = { Icon(TablerIcons.ArrowDown, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp)) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Editar", color = TextPrimary, fontSize = 11.5.sp) },
                        leadingIcon = { Icon(TablerIcons.Edit, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar", color = TextPrimary, fontSize = 11.5.sp) },
                        leadingIcon = { Icon(TablerIcons.Share, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp)) },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar", color = StateMute, fontSize = 11.5.sp) },
                        leadingIcon = { Icon(TablerIcons.Trash, contentDescription = null, tint = StateMute, modifier = Modifier.size(14.dp)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MIXER PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MixerPanel(
    modifier: Modifier = Modifier,
    channels: List<ChannelStripState>,
    vuLevels: List<Animatable<Float, *>>,
    metronomeVolume: Float,
    onMetronomeVolumeChange: (Float) -> Unit,
    masterVolume: Float,
    masterPan: Float = 0.5f,
    isLimiterActive: Boolean = false,
    masterVuLevel: Float,
    onMasterVolumeChange: (Float) -> Unit,
    onMasterPanChange: (Float) -> Unit = {},
    onVolumeChange: (Int, Float) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onAddChannelClick: () -> Unit,
    onChannelGearClick: (ChannelStripState) -> Unit,
    onSettingsClick: () -> Unit,
    onReverbChange: (Int, Float) -> Unit = { _, _ -> },
    onChorusChange: (Int, Float) -> Unit = { _, _ -> },
    onCutoffChange: (Int, Float) -> Unit = { _, _ -> },
    midiMappings: Map<Int, MidiTarget> = emptyMap(),
    scrollState: ScrollState,
    metronomeOn: Boolean = false,
    onMetronomeToggle: () -> Unit = {},
    metronomeBpm: Int = 120,
    onTapTempo: () -> Unit = {},
    metronomeTick: Boolean = false,
    isCondensed: Boolean = false,
    isStageLocked: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkPanel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(if (isCondensed) 4.dp else 6.dp)) {
            if (!isCondensed) {
                Text(
                    "MIXER",
                    color = OutlineVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Canales con scroll horizontal y fade de 16dp a los bordes
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 6.dp)
                            .horizontalScroll(scrollState)
                            .padding(start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        channels.forEach { chState ->
                            val levelIdx = (chState.id - 1).coerceIn(0, 7)
                            val isRevMapped = midiMappings.values.any { it is MidiTarget.ChannelReverb && it.channelIndex == chState.id - 1 }
                            val isChoMapped = midiMappings.values.any { it is MidiTarget.ChannelChorus && it.channelIndex == chState.id - 1 }
                            val isCutMapped = midiMappings.values.any { it is MidiTarget.ChannelCutoff && it.channelIndex == chState.id - 1 }
                            val isVolMapped = midiMappings.values.any { it is MidiTarget.ChannelVolume && it.channelIndex == chState.id - 1 }
                            val isMapped = isRevMapped || isChoMapped || isCutMapped || isVolMapped

                            ProChannelStrip(
                                channel = chState,
                                levelProvider = { vuLevels.getOrNull(levelIdx)?.value ?: 0f },
                                onVolumeChange = { vol -> onVolumeChange(chState.id, vol) },
                                onMuteToggle = { onMuteToggle(chState.id) },
                                onSoloToggle = { onSoloToggle(chState.id) },
                                onGearClick = { if (!isStageLocked) onChannelGearClick(chState) },
                                onFxClick = { if (!isStageLocked) onChannelGearClick(chState) },
                                onPanChange = {},
                                isCondensed = isCondensed,
                                modifier = Modifier.width(if (isCondensed) 92.dp else 108.dp),
                                isMidiMapped = isMapped
                            )
                        }

                        if (channels.size < 8 && !isStageLocked) {
                            AddChannelButton(onClick = onAddChannelClick)
                        }
                    }

                    // Fade izquierdo de 16dp solo si hay scroll hacia la izquierda
                    if (scrollState.value > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(DarkPanel, Color.Transparent)
                                    )
                                )
                        )
                    }

                    // Fade derecho de 16dp solo si queda contenido para scrollear
                    if (scrollState.value < scrollState.maxValue) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(16.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, DarkPanel)
                                    )
                                )
                        )
                    }

                    FineVioletScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }

                Spacer(Modifier.width(2.dp))
                VerticalDividerLine()
                Spacer(Modifier.width(2.dp))

                // Metrónomo
                ProMetronomeStrip(
                    volume = metronomeVolume,
                    onVolumeChange = onMetronomeVolumeChange,
                    enabled = metronomeOn,
                    onToggle = onMetronomeToggle,
                    bpm = metronomeBpm,
                    onTapTempo = onTapTempo,
                    levelProvider = { if (metronomeTick) 0.8f else 0f },
                    isCondensed = isCondensed,
                    modifier = Modifier.width(if (isCondensed) 96.dp else 104.dp)
                )

                // Master anclado a la derecha fuera del scroll
                ProMasterStrip(
                    volume = masterVolume,
                    onVolumeChange = onMasterVolumeChange,
                    levelProvider = { masterVuLevel },
                    isLimiterActive = isLimiterActive,
                    onLimiterToggle = {},
                    isCondensed = isCondensed,
                    onMidiMapClick = { if (!isStageLocked) onSettingsClick() },
                    modifier = Modifier.width(if (isCondensed) 106.dp else 114.dp)
                )
            }
        }
    }
}

@Composable
private fun VerticalDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, OutlineVariant, Color.Transparent)
                )
            )
    )
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// KEYBOARD PANEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
private val whitePianoNotes = listOf(
    21, 23, 
    24, 26, 28, 29, 31, 33, 35, // Octave 1
    36, 38, 40, 41, 43, 45, 47, // Octave 2
    48, 50, 52, 53, 55, 57, 59, // Octave 3
    60, 62, 64, 65, 67, 69, 71, // Octave 4
    72, 74, 76, 77, 79, 81, 83, // Octave 5
    84, 86, 88, 89, 91, 93, 95, // Octave 6
    96, 98, 100, 101, 103, 105, 107, // Octave 7
    108 // C8
)

private fun getMidiNoteStartX(note: Int, keyWidth: Dp = 32.dp): Dp {
    val clamped = note.coerceIn(21, 108)
    val whiteIdx = whitePianoNotes.indexOf(clamped)
    return if (whiteIdx >= 0) {
        keyWidth * whiteIdx
    } else {
        val prevWhite = whitePianoNotes.filter { it < clamped }.maxOrNull() ?: 21
        val prevIdx = whitePianoNotes.indexOf(prevWhite)
        keyWidth * (prevIdx + 0.5f)
    }
}

private fun getMidiNoteEndX(note: Int, keyWidth: Dp = 32.dp): Dp {
    val clamped = note.coerceIn(21, 108)
    val whiteIdx = whitePianoNotes.indexOf(clamped)
    return if (whiteIdx >= 0) {
        keyWidth * (whiteIdx + 1)
    } else {
        val prevWhite = whitePianoNotes.filter { it < clamped }.maxOrNull() ?: 21
        val prevIdx = whitePianoNotes.indexOf(prevWhite)
        keyWidth * (prevIdx + 1.5f)
    }
}

@Composable
private fun KeyboardPanel(
    channels: List<ChannelStripState> = emptyList(),
    activeNote: Int?,
    onNoteDown: (Int) -> Unit,
    onNoteUp: (Int) -> Unit,
    pitchBend: Animatable<Float, *>,
    modulation: Animatable<Float, *>,
    onModulationChange: (Float) -> Unit,
    sustainActive: Boolean,
    onSustainToggle: () -> Unit,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = DarkBackground.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.fillMaxWidth().height(142.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left controls: Sustain + Pitch/Mod
            Column(
                modifier = Modifier.fillMaxHeight().width(76.dp).padding(2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sustain button
                val sustainBg = if (sustainActive) StateSolo else SurfaceElevated.copy(alpha = 0.6f)
                val sustainBorder = if (sustainActive) StateSolo else BorderHairline
                val sustainTextColor = if (sustainActive) Color.Black else TextSecondary

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(sustainBg)
                        .border(1.dp, sustainBorder, RoundedCornerShape(8.dp))
                        .clickable(onClick = onSustainToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SUSTAIN",
                        color = sustainTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PitchBendWheel(
                        value = pitchBend.value,
                        onValueChange = { newVal ->
                            coroutineScope.launch { pitchBend.snapTo(newVal) }
                        },
                        onRelease = {
                            coroutineScope.launch {
                                pitchBend.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    ModulationWheel(
                        value = modulation.value,
                        onValueChange = onModulationChange,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Piano with MainStage-style Channel Layer Bars
            val keyboardScrollState = rememberScrollState()
            val totalKeyboardWidth = 32.dp * 52

            val barHeight = when {
                channels.size <= 2 -> 5.dp
                channels.size <= 4 -> 4.dp
                channels.size <= 6 -> 3.2.dp
                else -> 2.6.dp
            }
            val barSpacing = when {
                channels.size <= 3 -> 2.dp
                channels.size <= 6 -> 1.5.dp
                else -> 1.dp
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                // ─── CHANNEL LAYER BARS STACK (MAINSTAGE STYLE) ───────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(keyboardScrollState)
                ) {
                    Column(
                        modifier = Modifier
                            .width(totalKeyboardWidth)
                            .padding(vertical = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(barSpacing)
                    ) {
                        channels.forEach { ch ->
                            val accentColor = parseColorHex(ch.colorHex)
                            val alpha = if (ch.isMuted) 0.28f else 0.95f
                            val startX = getMidiNoteStartX(ch.keyRangeStart)
                            val endX = getMidiNoteEndX(ch.keyRangeEnd)
                            val barWidth = (endX - startX).coerceAtLeast(6.dp)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = startX)
                                        .width(barWidth)
                                        .fillMaxHeight()
                                        .shadow(
                                            elevation = if (ch.isMuted) 0.dp else 4.dp,
                                            shape = RoundedCornerShape(2.dp),
                                            ambientColor = accentColor.copy(alpha = 0.4f),
                                            spotColor = accentColor.copy(alpha = 0.5f)
                                        )
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accentColor.copy(alpha = alpha))
                                        .border(
                                            0.5.dp,
                                            Color.White.copy(alpha = if (ch.isMuted) 0.1f else 0.35f),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // ─── SCROLLABLE PIANO KEYBOARD ────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ScrollablePianoKeyboard(
                        scrollState = keyboardScrollState,
                        activeNote = activeNote,
                        onNoteDown = onNoteDown,
                        onNoteUp = onNoteUp
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            // Octave controls
            Column(
                modifier = Modifier.fillMaxHeight().width(52.dp).padding(4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OctaveButton("OCT\n+") {
                    coroutineScope.launch {
                        keyboardScrollState.animateScrollTo(
                            (keyboardScrollState.value + 7 * 32).coerceAtMost(keyboardScrollState.maxValue)
                        )
                    }
                }
                OctaveButton("OCT\n-") {
                    coroutineScope.launch {
                        keyboardScrollState.animateScrollTo(
                            (keyboardScrollState.value - 7 * 32).coerceAtLeast(0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.OctaveButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

// Needed to call OctaveButton from KeyboardPanel's Column scope
@Composable
private fun OctaveButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAD STRIP
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PadStrip(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    bank: String,
    onBankChange: (String) -> Unit,
    availableBanks: List<String>,
    activePadNote: Int?,
    onPadNoteToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkPanel.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, BorderHairline),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Selector de Banco
            var expanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bank.ifEmpty { "Bank A" },
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(TablerIcons.ChevronDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DarkPanel)
                ) {
                    availableBanks.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b, color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                onBankChange(b)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 2. Control de Volumen con etiqueta PAD y lectura numérica legible (área táctil >= 48dp)
            Row(
                modifier = Modifier
                    .width(210.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "PAD",
                    color = if (enabled) AccentPrimary else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { onEnabledChange(!enabled) }
                )
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentPrimary,
                        activeTrackColor = AccentPrimary,
                        inactiveTrackColor = SurfaceElevated
                    )
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )
            }

            // 3. 12 Notas de Pad lado a lado en la misma fila (activo en AccentPrimary violeta)
            val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                notes.forEachIndexed { index, noteName ->
                    val isActive = activePadNote == index
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isActive) AccentPrimary else SurfaceCard
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) AccentLavender else BorderHairline,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable(enabled = enabled) { onPadNoteToggle(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = noteName,
                            color = if (isActive) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
