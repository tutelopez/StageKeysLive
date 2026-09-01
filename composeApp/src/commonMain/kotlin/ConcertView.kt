package com.midi.mainstage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.platform.LocalConfiguration
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

    // Master output
    masterVolume: Float,
    onMasterVolumeChange: (Float) -> Unit,
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
    vuLevels: List<Animatable<Float, *>>
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Use BoxWithConstraints to detect screen width (KMP-safe, no LocalConfiguration needed)
    // Tablets (>=600dp) start expanded; phones start collapsed to maximize mixer space
    var isKeyboardVisible by rememberSaveable { mutableStateOf(false) }

    // AppBackground handles the BoxWithConstraints and gradient glows
    AppBackground(
        modifier = Modifier.fillMaxSize(),
        glowOpacityFactor = 0.5f
    ) {

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // â”€â”€â”€ TOP BAR â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            val nextPatchName = if (concert.patches.size > 1) {
                concert.patches.getOrNull((selectedPatchIndex + 1) % concert.patches.size)?.name
            } else null

            TopBar(
                concertName = concert.name,
                nextPatchName = nextPatchName,
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
                onSettingsClick = onSettingsClick,
                onPanicClick = onPanicClick,
                performanceStats = performanceStats,
                batteryLevel = batteryLevel,
                batteryCharging = batteryCharging,
                audioDiagnostics = audioDiagnostics
            )

            // ─── SCROLLABLE MAIN CONTENT (PATCHES + MIXER + PADS) ─────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ─── MAIN ROW: PATCHES + MIXER (HEIGHT 280dp like HTML mockup) ──────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // PATCHES PANEL
                    PatchesPanel(
                        patches = concert.patches,
                        selectedIndex = selectedPatchIndex,
                        onSelect = onSelectPatch,
                        onAdd = onAddPatchClick,
                        onEdit = onEditPatchClick,
                        onDelete = onDeletePatch,
                        onExport = onExportPatchClick,
                        onImport = onImportPatchClick
                    )

                    // MIXER PANEL
                    MixerPanel(
                        modifier = Modifier.weight(1f),
                        channels = concert.channels,
                        vuLevels = vuLevels,
                        metronomeVolume = metronomeVolume,
                        onMetronomeVolumeChange = onMetronomeVolumeChange,
                        masterVolume = masterVolume,
                        masterVuLevel = masterVuLevel,
                        onMasterVolumeChange = onMasterVolumeChange,
                        onVolumeChange = onVolumeChange,
                        onMuteToggle = onMuteToggle,
                        onSoloToggle = onSoloToggle,
                        onAddChannelClick = onAddChannelClick,
                        onChannelGearClick = onChannelGearClick,
                        onSettingsClick = onSettingsClick,
                        scrollState = rememberScrollState()
                    )
                }

                // ─── PAD STRIP (ALWAYS FULLY ACCESSIBLE) ─────────────────────
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

            // ─── KEYBOARD UNIFIED ATTACHED TAB + PANEL ───────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Attached tab toggle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            if (isKeyboardVisible) RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                            else RoundedCornerShape(12.dp)
                        )
                        .background(DarkPanel.copy(alpha = 0.85f))
                        .clickable { isKeyboardVisible = !isKeyboardVisible }
                        .padding(vertical = 5.dp),
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
                            color = if (isKeyboardVisible) Color.White else TextDark,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Keyboard panel directly attached underneath
                val keyboardHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isKeyboardVisible) 142.dp else 0.dp
                )
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
        } // End Column inside BoxWithConstraints
    } // End AppBackground
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    concertName: String,
    nextPatchName: String? = null,
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
    audioDiagnostics: String
) {
    var panicBlink by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkPanel.copy(alpha = 0.8f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().height(54.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    TablerIcons.ArrowLeft,
                    contentDescription = "Back",
                    tint = TextLight,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            // Concert name + Next patch preview
            Column {
                Text(
                    text = concertName.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        fontSize = 14.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (nextPatchName != null) {
                    Text(
                        text = "Siguiente: $nextPatchName",
                        color = TextDark,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Metronome / BPM pill button
            var showMetroPopup by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkPanel)
                        .border(1.2.dp, if (metronomeOn) AccentNeonGreen else AccentSky.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .clickable { onMetronomeToggle() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$metronomeBpm BPM",
                        color = if (metronomeOn) AccentNeonGreen else AccentSky,
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
                            thumbColor = AccentNeonGreen,
                            activeTrackColor = AccentNeonGreen,
                            inactiveTrackColor = OutlineVariant
                        )
                    )
                }
            }

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
                        if (isPlayingRecording) AccentNeonGreen else if (hasRecording) AccentSky.copy(alpha = 0.7f) else OutlineVariant.copy(alpha = 0.35f),
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
                        tint = if (isPlayingRecording) AccentNeonGreen else if (hasRecording) AccentSky else TextDark.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isPlayingRecording) "STOP" else "PLAY",
                        color = if (isPlayingRecording) AccentNeonGreen else if (hasRecording) AccentSky else TextDark.copy(alpha = 0.35f),
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
                        tint = if (hasRecording) AccentPurple else TextDark.copy(alpha = 0.35f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "MIDI",
                        color = if (hasRecording) AccentPurple else TextDark.copy(alpha = 0.35f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Panic Button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(StatusError.copy(alpha = 0.15f))
                    .border(1.dp, StatusError.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { 
                        onPanicClick()
                        panicBlink = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.AlertTriangle,
                    contentDescription = "Panic",
                    tint = StatusError,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            // Settings gear
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkPanel)
                    .border(1.dp, OutlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Settings",
                    tint = TextDark,
                    modifier = Modifier.size(16.dp)
                )
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
    val bgColor = if (active) activeColor.copy(alpha = 0.18f) else LightPanel
    val borderColor = if (active) activeColor else OutlineVariant
    val textColor = if (active) activeColor else TextDark

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
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// PATCHES PANEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun PatchesPanel(
    patches: List<PatchState>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onEdit: (PatchState) -> Unit,
    onDelete: (PatchState) -> Unit,
    onExport: (PatchState) -> Unit,
    onImport: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkPanel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.width(132.dp).fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PATCHES",
                    color = TextDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImport, modifier = Modifier.size(22.dp)) {
                    Icon(TablerIcons.Download, contentDescription = "Import", tint = TextDark, modifier = Modifier.size(13.dp))
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(22.dp)) {
                    Icon(TablerIcons.Plus, contentDescription = "Add patch", tint = AccentSky, modifier = Modifier.size(15.dp))
                }
            }

            if (patches.size > 1) {
                val nextPatch = patches.getOrNull((selectedIndex + 1) % patches.size)
                Text(
                    text = "Siguiente: ${nextPatch?.name ?: "—"}",
                    color = TextDark.copy(alpha = 0.65f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(patches) { patch ->
                    val idx = patches.indexOf(patch)
                    val isSelected = idx == selectedIndex
                    PatchRow(
                        patch = patch,
                        isSelected = isSelected,
                        onClick = { onSelect(idx) },
                        onEdit = { onEdit(patch) },
                        onDelete = { onDelete(patch) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // + Nuevo button at bottom of list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+ Nuevo",
                    color = TextDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PatchRow(
    patch: PatchState,
    isSelected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = RoundedCornerShape(10.dp),
                        ambientColor = AccentSky.copy(alpha = 0.35f),
                        spotColor = AccentSky.copy(alpha = 0.45f)
                    )
                } else Modifier
            )
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (isSelected) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                AccentSky.copy(alpha = 0.22f),
                                AccentPurple.copy(alpha = 0.14f)
                            )
                        )
                    )
                } else {
                    Modifier.background(SurfaceElevated)
                }
            )
            .border(
                width = 1.dp,
                color = if (isSelected) AccentSky.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patch.name,
                color = if (isSelected) Color.White else TextDark,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (patch.category.isNotBlank()) {
                Text(
                    text = patch.category,
                    color = if (isSelected) AccentSky.copy(alpha = 0.8f) else TextDark.copy(alpha = 0.6f),
                    fontSize = 8.5.sp
                )
            }
        }
        if (isSelected) {
            IconButton(onClick = onEdit, modifier = Modifier.size(18.dp)) {
                Icon(TablerIcons.Edit, contentDescription = "Edit", tint = AccentSky, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// MIXER PANEL
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun MixerPanel(
    modifier: Modifier = Modifier,
    channels: List<ChannelStripState>,
    vuLevels: List<Animatable<Float, *>>,
    metronomeVolume: Float,
    onMetronomeVolumeChange: (Float) -> Unit,
    masterVolume: Float,
    masterVuLevel: Float,
    onMasterVolumeChange: (Float) -> Unit,
    onVolumeChange: (Int, Float) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit,
    onAddChannelClick: () -> Unit,
    onChannelGearClick: (ChannelStripState) -> Unit,
    onSettingsClick: () -> Unit,
    scrollState: ScrollState
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkBackground.copy(alpha = 0.72f),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                "MIXER",
                color = OutlineVariant,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxSize().horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                channels.forEach { chState ->
                    val levelIdx = (chState.id - 1).coerceIn(0, 7)
                    val animLevel = vuLevels[levelIdx].value
                    ChannelStripItem(
                        state = chState,
                        level = animLevel,
                        onVolumeChange = { vol -> onVolumeChange(chState.id, vol) },
                        onMuteToggle = { onMuteToggle(chState.id) },
                        onSoloToggle = { onSoloToggle(chState.id) },
                        onGearClick = { onChannelGearClick(chState) }
                    )
                }

                if (channels.size < 8) {
                    AddChannelButton(onClick = onAddChannelClick)
                }

                Spacer(Modifier.width(4.dp))
                VerticalDividerLine()
                Spacer(Modifier.width(4.dp))

                MetronomeChannelItem(
                    volume = metronomeVolume,
                    onVolumeChange = onMetronomeVolumeChange
                )

                Spacer(Modifier.width(4.dp))

                MasterOutputChannelItem(
                    volume = masterVolume,
                    level = masterVuLevel,
                    onVolumeChange = onMasterVolumeChange,
                    onMidiMapClick = onSettingsClick
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (sustainActive) {
                                Modifier.background(
                                    Brush.horizontalGradient(listOf(AccentWarmYellow, AccentCoral))
                                )
                            } else {
                                Modifier.background(SurfaceElevated.copy(alpha = 0.6f))
                            }
                        )
                        .border(
                            1.dp,
                            if (sustainActive) AccentWarmYellow else OutlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onSustainToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SUSTAIN",
                        color = if (sustainActive) Color.Black else TextDark,
                        fontSize = 8.sp,
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
            .background(LightPanel)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextDark,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
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
            .background(LightPanel)
            .border(1.dp, OutlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = TextDark,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
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
    onPadNoteToggle: (Int) -> Unit
) {
    Surface(
        color = DarkPanel.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
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
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bank.ifEmpty { "Bank A" },
                            color = Color.White,
                            fontSize = 10.5.sp,
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

                Spacer(Modifier.width(10.dp))

                // Volume Slider
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

                Spacer(Modifier.width(10.dp))

                // PAD Label
                Text(
                    "PAD",
                    color = if (enabled) TextLight else TextDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { onEnabledChange(!enabled) }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Pad note triggers
            val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                notes.forEachIndexed { index, noteName ->
                    val isActive = activePadNote == index
                    Box(
                        modifier = Modifier
                            .size(44.dp, 36.dp)
                            .then(
                                if (isActive) {
                                    Modifier.shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(9.dp),
                                        ambientColor = StatusSuccess.copy(alpha = 0.55f),
                                        spotColor = StatusSuccess.copy(alpha = 0.65f)
                                    )
                                } else Modifier
                            )
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (isActive)
                                    Brush.verticalGradient(listOf(StatusSuccess, Color(0xFF0A8A63)))
                                else
                                    Brush.verticalGradient(listOf(LightPanel, DarkPanel))
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) StatusSuccess.copy(alpha = 0.6f) else Color.Transparent,
                                shape = RoundedCornerShape(9.dp)
                            )
                            .clickable(enabled = enabled) { onPadNoteToggle(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = noteName,
                            color = if (isActive) Color.White else TextDark,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

