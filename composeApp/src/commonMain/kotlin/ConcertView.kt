package com.tutelopezmusic.stagekeyslive

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
    vuLevels: List<Animatable<Float, *>>
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Use BoxWithConstraints to detect screen width (KMP-safe, no LocalConfiguration needed)
    // Tablets (>=600dp) start expanded; phones start collapsed to maximize mixer space
    var isKeyboardVisible by rememberSaveable { mutableStateOf(false) }
    var isPerformanceMode by rememberSaveable { mutableStateOf(false) }

    // AppBackground handles the BoxWithConstraints and gradient glows
    AppBackground(
        modifier = Modifier.fillMaxSize(),
        glowOpacityFactor = 0.5f
    ) {

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        padEnabled = padEnabled,
                        onPadEnabledChange = onPadEnabledChange,
                        padVolume = padVolume,
                        onPadVolumeChange = onPadVolumeChange,
                        padBank = padBank,
                        onPadBankChange = onPadBankChange,
                        availablePadBanks = availablePadBanks,
                        activePadNote = activePadNote,
                        onPadNoteToggle = onPadNoteToggle
                    )
                }
            } else {
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
                            onImport = onImportPatchClick,
                            onToggleFavorite = onToggleFavorite,
                            onMovePatch = onMovePatch
                        )

                        // MIXER PANEL
                        MixerPanel(
                            modifier = Modifier.weight(1f),
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
            }
        } // End Column inside BoxWithConstraints
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
    // Pads
    padEnabled: Boolean,
    onPadEnabledChange: (Boolean) -> Unit,
    padVolume: Float,
    onPadVolumeChange: (Float) -> Unit,
    padBank: String,
    onPadBankChange: (String) -> Unit,
    availablePadBanks: List<String>,
    activePadNote: Int?,
    onPadNoteToggle: (Int) -> Unit
) {
    val currentPatch = concert.patches.getOrNull(selectedPatchIndex)
    val prevPatch = concert.patches.getOrNull(selectedPatchIndex - 1)
    val nextPatch = concert.patches.getOrNull(selectedPatchIndex + 1)
    val patchColor = concert.channels.firstOrNull { !it.isMuted }?.let { parseColorHex(it.colorHex) } ?: AccentSky
    val mixerScrollState = rememberScrollState()

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
                        tint = if (prevPatch != null) AccentSky else TextDark.copy(alpha = 0.3f),
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
                        fontSize = 10.5.sp,
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
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Next button
                IconButton(
                    onClick = { if (nextPatch != null) onSelectPatch(selectedPatchIndex + 1) },
                    enabled = nextPatch != null,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        TablerIcons.ChevronRight,
                        contentDescription = "Patch Siguiente",
                        tint = if (nextPatch != null) AccentSky else TextDark.copy(alpha = 0.3f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // ── 2. CENTER: VIRTUAL nanoKONTROL MIXING SURFACE ─────────────────────
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = DarkBackground.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(6.dp).horizontalScroll(mixerScrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                channels.forEach { chState ->
                    val levelIdx = (chState.id - 1).coerceIn(0, 7)
                    val animLevel = vuLevels.getOrNull(levelIdx)?.value ?: 0f
                    val isRevMapped = midiMappings.values.any { it is MidiTarget.ChannelReverb && it.channelIndex == chState.id - 1 }
                    val isChoMapped = midiMappings.values.any { it is MidiTarget.ChannelChorus && it.channelIndex == chState.id - 1 }
                    val isCutMapped = midiMappings.values.any { it is MidiTarget.ChannelCutoff && it.channelIndex == chState.id - 1 }
                    PerformanceChannelStripItem(
                        state = chState,
                        level = animLevel,
                        onVolumeChange = { vol -> onVolumeChange(chState.id, vol) },
                        onMuteToggle = { onMuteToggle(chState.id) },
                        onSoloToggle = { onSoloToggle(chState.id) },
                        onGearClick = { onChannelGearClick(chState) },
                        onReverbChange = { v -> onReverbChange(chState.id, v) },
                        onChorusChange = { v -> onChorusChange(chState.id, v) },
                        onCutoffChange = { v -> onCutoffChange(chState.id, v) },
                        isReverbMapped = isRevMapped,
                        isChorusMapped = isChoMapped,
                        isCutoffMapped = isCutMapped
                    )
                }

                Spacer(Modifier.width(4.dp))
                VerticalDividerLine()
                Spacer(Modifier.width(4.dp))

                PerformanceMetronomeChannelItem(
                    volume = metronomeVolume,
                    onVolumeChange = onMetronomeVolumeChange
                )

                Spacer(Modifier.width(4.dp))

                PerformanceMasterChannelItem(
                    volume = masterVolume,
                    level = masterVuLevel,
                    pan = masterPan,
                    isLimiterActive = isLimiterActive,
                    onVolumeChange = onMasterVolumeChange,
                    onPanChange = onMasterPanChange
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
private fun PerformanceChannelStripItem(
    state: ChannelStripState,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onGearClick: () -> Unit,
    onReverbChange: (Float) -> Unit,
    onChorusChange: (Float) -> Unit,
    onCutoffChange: (Float) -> Unit,
    isReverbMapped: Boolean,
    isChorusMapped: Boolean,
    isCutoffMapped: Boolean
) {
    val accentColor = parseColorHex(state.colorHex)
    var selectedFx by remember { mutableStateOf(FxKnobType.REVERB) }

    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = accentColor.copy(alpha = 0.3f),
                spotColor = accentColor.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(listOf(SurfaceElevated, DarkBackground))
            )
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // ── 1. Header: Color + Name + Gear ─────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )

            Text(
                text = state.name.take(9).uppercase(),
                color = accentColor,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            )

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onGearClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Configurar",
                    tint = TextDark.copy(alpha = 0.7f),
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        // ── 2. FX Knobs Section (TOP, nanoKONTROL hardware layout) ──────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            // Mini selector tabs [R | C | T]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF1B1D28))
                    .padding(1.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FxKnobType.entries.forEach { fxType ->
                    val isSel = selectedFx == fxType
                    val isMapped = when (fxType) {
                        FxKnobType.REVERB -> isReverbMapped
                        FxKnobType.CHORUS -> isChorusMapped
                        FxKnobType.TONE -> isCutoffMapped
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isSel) fxType.color.copy(alpha = 0.28f) else Color.Transparent)
                            .clickable { selectedFx = fxType }
                            .padding(vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = fxType.label.take(1),
                                color = if (isSel) fxType.color else TextDark,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isMapped) {
                                Spacer(Modifier.width(1.dp))
                                Box(
                                    modifier = Modifier
                                        .size(2.5.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B))
                                )
                            }
                        }
                    }
                }
            }

            // Active Rotary Knob
            when (selectedFx) {
                FxKnobType.REVERB -> MainstageRotaryKnob(
                    value = state.reverbSend,
                    onValueChange = onReverbChange,
                    type = FxKnobType.REVERB,
                    isMidiMapped = isReverbMapped,
                    knobSize = 30.dp,
                    showLabel = false
                )
                FxKnobType.CHORUS -> MainstageRotaryKnob(
                    value = state.chorusSend,
                    onValueChange = onChorusChange,
                    type = FxKnobType.CHORUS,
                    isMidiMapped = isChorusMapped,
                    knobSize = 30.dp,
                    showLabel = false
                )
                FxKnobType.TONE -> MainstageRotaryKnob(
                    value = state.filterCutoff,
                    onValueChange = onCutoffChange,
                    type = FxKnobType.TONE,
                    isMidiMapped = isCutoffMapped,
                    knobSize = 30.dp,
                    showLabel = false
                )
            }
        }

        // ── 3. Mute / Solo Buttons ────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state.isMuted) StatusError else SurfaceElevated)
                    .border(1.dp, if (state.isMuted) StatusError else OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onMuteToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    color = if (state.isMuted) Color.White else TextDark,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state.isSoloed) StatusWarning else SurfaceElevated)
                    .border(1.dp, if (state.isSoloed) StatusWarning else OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onSoloToggle),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "S",
                    color = if (state.isSoloed) Color.Black else TextDark,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── 4. Volume Fader + Level Meter (Full remaining height) ───────────
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VolumeFader(
                value = state.volume,
                accentColor = accentColor,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            LevelMeter(
                level = level,
                accentColor = accentColor,
                modifier = Modifier.width(5.dp).fillMaxHeight()
            )
        }

        // ── 5. Volume % label ──────────────────────────────────────────────
        Text(
            text = "${(state.volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 7.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PerformanceMetronomeChannelItem(
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    val accent = AccentNeonGreen
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = accent.copy(alpha = 0.3f),
                spotColor = accent.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(listOf(SurfaceElevated, DarkBackground))
            )
            .border(
                1.dp,
                accent.copy(alpha = 0.45f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Icon + label
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "CLICK",
                color = Color(0xFF7DFFB0),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(Modifier.height(48.dp)) // Aligns fader top with channel strips

        // Fader
        VolumeFader(
            value = volume,
            accentColor = accent,
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Text(
            "${(volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 7.5.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PerformanceMasterChannelItem(
    volume: Float,
    level: Float,
    pan: Float = 0.5f,
    isLimiterActive: Boolean = false,
    onVolumeChange: (Float) -> Unit,
    onPanChange: (Float) -> Unit = {}
) {
    val accent = AccentPurple
    Column(
        modifier = Modifier
            .width(82.dp)
            .fillMaxHeight()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(14.dp),
                ambientColor = accent.copy(alpha = 0.3f),
                spotColor = accent.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(listOf(SurfaceElevated, DarkBackground))
            )
            .border(
                1.dp,
                accent.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "MASTER",
                color = Color(0xFFC9A3F7),
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            if (isLimiterActive) {
                Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 2.dp, vertical = 0.5.dp)
                ) {
                    Text("LIM", color = Color.Black, fontSize = 6.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Master Pan Control
        val panPercent = ((pan - 0.5f) * 200).toInt()
        val panText = when {
            panPercent == 0 -> "C"
            panPercent < 0 -> "L${-panPercent}"
            else -> "R${panPercent}"
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("PAN", color = TextDark, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                Text(panText, color = accent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = pan,
                onValueChange = onPanChange,
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().height(16.dp),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = OutlineVariant
                )
            )
        }

        Spacer(Modifier.height(18.dp))

        // Fader + Stereo LevelMeter
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            VolumeFader(
                value = volume,
                accentColor = accent,
                onValueChange = onVolumeChange,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            LevelMeter(
                level = level,
                accentColor = accent,
                modifier = Modifier.width(6.dp).fillMaxHeight()
            )
        }

        Text(
            "${(volume * 100).toInt()}%",
            color = TextDark,
            fontSize = 8.sp,
            textAlign = TextAlign.Center
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
                        color = if (enabled) StatusSuccess else TextDark,
                        fontSize = 10.sp,
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
                                    Brush.verticalGradient(listOf(StatusSuccess, Color(0xFF0A8A63)))
                                else
                                    Brush.verticalGradient(listOf(LightPanel, DarkPanel))
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
                            color = if (isActive) Color.White else if (enabled) TextLight else TextDark.copy(alpha = 0.4f),
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

            if (isPerformanceMode) {
                // ─── RESOURCE MONITOR (CPU & RAM) - SOLO EN MODO EJECUCIÓN ───────────
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

            if (!isPerformanceMode) {
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
            }

            Spacer(Modifier.width(8.dp))

            // MIDI Activity Indicator LED
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (midiActive) AccentNeonGreen.copy(alpha = 0.18f) else DarkPanel)
                    .border(1.2.dp, if (midiActive) AccentNeonGreen else OutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (midiActive) AccentNeonGreen else Color(0xFF555566))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "MIDI",
                        color = if (midiActive) AccentNeonGreen else TextDark,
                        fontSize = 10.sp,
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

            if (!isPerformanceMode) {
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

            Spacer(Modifier.width(6.dp))

            // Performance mode toggle button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isPerformanceMode) AccentNeonGreen.copy(alpha = 0.18f)
                        else DarkPanel
                    )
                    .border(
                        1.dp,
                        if (isPerformanceMode) AccentNeonGreen else OutlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(onClick = onPerformanceModeToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.DeviceDesktop,
                    contentDescription = if (isPerformanceMode) "Salir del modo ejecución" else "Modo ejecución",
                    tint = if (isPerformanceMode) AccentNeonGreen else TextDark,
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
        else -> AccentNeonGreen
    }

    val ramColor = when {
        ramPercent >= 85 -> StatusError
        ramPercent >= 65 -> StatusWarning
        else -> AccentSky
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF13151F).copy(alpha = 0.9f),
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
                    color = TextDark,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$cpu%",
                    color = cpuColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Micro CPU bar
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF222533))
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
                    color = TextDark,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "$ramPercent%",
                    color = ramColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                // Micro RAM bar
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF222533))
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
    onToggleFavorite: (PatchState) -> Unit = {},
    onMovePatch: (Int, Int) -> Unit = { _, _ -> }
) {
    var showFavoritesOnly by remember { mutableStateOf(false) }
    val displayPatches = if (showFavoritesOnly) patches.filter { it.isFavorite } else patches

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = DarkPanel.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f)),
        modifier = Modifier.width(140.dp).fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PATCHES",
                    color = TextDark,
                    fontSize = 9.sp,
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
                        color = if (showFavoritesOnly) Color(0xFFFFD700) else TextDark,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onImport, modifier = Modifier.size(20.dp)) {
                    Icon(TablerIcons.Download, contentDescription = "Import", tint = TextDark, modifier = Modifier.size(12.dp))
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(20.dp)) {
                    Icon(TablerIcons.Plus, contentDescription = "Add patch", tint = AccentSky, modifier = Modifier.size(14.dp))
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
                            color = TextDark,
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(2.dp))
                        Button(
                            onClick = onAdd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("+ Agregar", color = Color.Black, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
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
                            onMoveDown = { onMovePatch(actualIdx, actualIdx + 1) }
                        )
                    }
                }
            }

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
                        color = TextDark,
                        fontSize = 10.5.sp,
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
    onMoveDown: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

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
            .padding(horizontal = 6.dp, vertical = 6.dp),
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
                color = if (patch.isFavorite) Color(0xFFFFD700) else TextDark.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(4.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patch.name,
                color = if (isSelected) Color.White else TextDark,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (patch.category.isNotBlank()) {
                Text(
                    text = patch.category,
                    color = if (isSelected) AccentSky.copy(alpha = 0.8f) else TextDark.copy(alpha = 0.6f),
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canMoveUp) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DarkBackground.copy(alpha = 0.6f))
                            .clickable(onClick = onMoveUp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▲", color = AccentSky, fontSize = 8.sp)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                if (canMoveDown) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DarkBackground.copy(alpha = 0.6f))
                            .clickable(onClick = onMoveDown),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▼", color = AccentSky, fontSize = 8.sp)
                    }
                    Spacer(Modifier.width(2.dp))
                }
                
                // Share / Export Icon
                IconButton(onClick = onExport, modifier = Modifier.size(16.dp)) {
                    Icon(TablerIcons.Share, contentDescription = "Exportar Patch", tint = AccentSky, modifier = Modifier.size(11.dp))
                }
                
                Spacer(Modifier.width(2.dp))

                // Edit Icon
                IconButton(onClick = onEdit, modifier = Modifier.size(16.dp)) {
                    Icon(TablerIcons.Edit, contentDescription = "Edit", tint = AccentSky, modifier = Modifier.size(11.dp))
                }

                Spacer(Modifier.width(2.dp))

                // Delete Icon
                IconButton(onClick = onDelete, modifier = Modifier.size(16.dp)) {
                    Icon(TablerIcons.Trash, contentDescription = "Eliminar Patch", tint = StatusError, modifier = Modifier.size(11.dp))
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
                    val isRevMapped = midiMappings.values.any { it is MidiTarget.ChannelReverb && it.channelIndex == chState.id - 1 }
                    val isChoMapped = midiMappings.values.any { it is MidiTarget.ChannelChorus && it.channelIndex == chState.id - 1 }
                    val isCutMapped = midiMappings.values.any { it is MidiTarget.ChannelCutoff && it.channelIndex == chState.id - 1 }
                    ChannelStripItem(
                        state = chState,
                        level = animLevel,
                        onVolumeChange = { vol -> onVolumeChange(chState.id, vol) },
                        onMuteToggle = { onMuteToggle(chState.id) },
                        onSoloToggle = { onSoloToggle(chState.id) },
                        onGearClick = { onChannelGearClick(chState) },
                        onReverbChange = { v -> onReverbChange(chState.id, v) },
                        onChorusChange = { v -> onChorusChange(chState.id, v) },
                        onCutoffChange = { v -> onCutoffChange(chState.id, v) },
                        isReverbMapped = isRevMapped,
                        isChorusMapped = isChoMapped,
                        isCutoffMapped = isCutMapped
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
                    pan = masterPan,
                    isLimiterActive = isLimiterActive,
                    onVolumeChange = onMasterVolumeChange,
                    onPanChange = onMasterPanChange,
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

