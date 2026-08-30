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
            TopBar(
                concertName = concert.name,
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
                midiActive = midiActivityIndicator,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick,
                onPanicClick = onPanicClick,
                performanceStats = performanceStats,
                batteryLevel = batteryLevel,
                batteryCharging = batteryCharging,
                audioDiagnostics = audioDiagnostics
            )

            // â”€â”€â”€ MAIN AREA: PATCHES + MIXER â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 320.dp, max = 500.dp).fillMaxWidth(),
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

            Spacer(modifier = Modifier.height(12.dp))

            // --- PAD STRIP ---
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
        } // End scrollable column
            
            Spacer(modifier = Modifier.height(12.dp))

            // â”€â”€â”€ KEYBOARD TOGGLE + PANEL â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // Chevron toggle bar
            Surface(
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = DarkPanel,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isKeyboardVisible = !isKeyboardVisible }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isKeyboardVisible) TablerIcons.ChevronDown else TablerIcons.ChevronUp,
                        contentDescription = if (isKeyboardVisible) "Colapsar teclado" else "Expandir teclado",
                        tint = TextDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKeyboardVisible) "TECLADO" else "TECLADO (oculto)",
                        color = TextDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            // Animated keyboard panel (Fixed expansion issue)
            val keyboardHeight by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isKeyboardVisible) 140.dp else 0.dp
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(keyboardHeight)
                    .clipToBounds()
            ) {
                KeyboardPanel(
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
    } // End Column inside BoxWithConstraints
    } // End AppBackground
}

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// TOP BAR
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
private fun TopBar(
    concertName: String,
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
    var showSysPopup by remember { mutableStateOf(false) }
    
    LaunchedEffect(panicBlink) {
        if (panicBlink) {
            delay(300)
            panicBlink = false
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DarkPanel,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back
            IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.ArrowLeft,
                    contentDescription = "Back",
                    tint = TextDark,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Concert name
            Text(
                text = concertName.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 14.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )

            Spacer(Modifier.weight(1f))

            // MIDI Activity dot
            if (midiActive) {
                Box(
                    modifier = Modifier.size(8.dp)
                        .background(AccentSky, CircleShape)
                )
                Spacer(Modifier.width(6.dp))
            }

            // System status dot
            val audioDegraded = audioDiagnostics.contains("xrun", ignoreCase = true) || audioDiagnostics.contains("underflow", ignoreCase = true)
            val sysColor = when {
                batteryLevel < 10 -> StatusError
                batteryLevel < 20 || audioDegraded -> StatusWarning // Amber
                else -> StatusSuccess // Green
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showSysPopup = true },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).background(sysColor, CircleShape))
                }
                
                DropdownMenu(
                    expanded = showSysPopup,
                    onDismissRequest = { showSysPopup = false },
                    modifier = Modifier.background(DarkPanel)
                ) {
                    Text("Battery: $batteryLevel% ${if (batteryCharging) "(Charging)" else ""}", modifier = Modifier.padding(8.dp), color = Color.White)
                    if (performanceStats != null) {
                        Text("CPU: ${performanceStats.cpuPercent ?: "?"}%", modifier = Modifier.padding(8.dp), color = Color.White)
                        Text("RAM: ${performanceStats.ramMb} MB", modifier = Modifier.padding(8.dp), color = Color.White)
                    }
                    Text("Audio: $audioDiagnostics", modifier = Modifier.padding(8.dp), color = Color.White)
                }
            }
            
            Spacer(Modifier.width(6.dp))
            
            // Panic Button
            IconButton(
                onClick = { 
                    onPanicClick()
                    panicBlink = true
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    TablerIcons.AlertTriangle,
                    contentDescription = "Panic",
                    tint = if (panicBlink) StatusError else TextDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(Modifier.width(6.dp))

            // Metronome beat dot
            Box(
                modifier = Modifier.size(8.dp)
                    .background(
                        if (metronomeTick && metronomeOn) AccentNeonGreen else OutlineVariant,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))

            // Metronome button
            var showMetroPopup by remember { mutableStateOf(false) }
            Box {
                PillButton(
                    label = if (metronomeOn) "$metronomeBpm BPM" else "METRO",
                    active = metronomeOn,
                    activeColor = AccentNeonGreen,
                    onClick = onMetronomeToggle,
                    onLongClick = { showMetroPopup = true }
                )
                
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
            var tapPulseKey by remember { mutableStateOf(0L) }
            LaunchedEffect(tapPulseKey) {
                if (tapPulseKey > 0L) {
                    delay(150)
                    tapPulseKey = 0L
                }
            }
            PillButton(
                label = "TAP",
                active = (System.currentTimeMillis() - tapPulseKey) < 150,
                activeColor = AccentSky,
                onClick = { 
                    tapPulseKey = System.currentTimeMillis()
                    onTapTempo() 
                }
            )


            Spacer(Modifier.width(8.dp))

            // Record button
            PillButton(
                label = if (isRecording) "â— REC" else "REC",
                active = isRecording,
                activeColor = StatusError,
                onClick = onRecordToggle
            )

            Spacer(Modifier.width(4.dp))

            // Play recording
            IconButton(onClick = onPlayRecordingClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isPlayingRecording) TablerIcons.PlayerStop else TablerIcons.PlayerPlay,
                    contentDescription = "Play",
                    tint = if (isPlayingRecording) AccentSky else TextDark,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Settings gear
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Settings",
                    tint = TextDark,
                    modifier = Modifier.size(20.dp)
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
        shape = RoundedCornerShape(20.dp),
        color = DarkPanel.copy(alpha = 0.78f),
        tonalElevation = 2.dp,
        modifier = Modifier.width(168.dp).fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PATCHES",
                    color = TextDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImport, modifier = Modifier.size(24.dp)) {
                    Icon(TablerIcons.Download, contentDescription = "Import", tint = TextDark, modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) {
                    Icon(TablerIcons.Plus, contentDescription = "Add patch", tint = AccentSky, modifier = Modifier.size(16.dp))
                }
            }

            Divider(color = LightPanel, thickness = 1.dp)
            Spacer(Modifier.height(4.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
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
                    Spacer(Modifier.height(2.dp))
                }
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
    val bgColor = if (isSelected)
        Brush.horizontalGradient(listOf(AccentSky.copy(alpha = 0.2f), AccentPurple.copy(alpha = 0.1f)))
    else
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    val borderColor = if (isSelected) AccentSky.copy(alpha = 0.7f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = patch.name,
                color = if (isSelected) Color.White else TextDark,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (patch.category.isNotBlank()) {
                Text(
                    text = patch.category,
                    color = TextDark,
                    fontSize = 9.sp
                )
            }
        }
        if (isSelected) {
            IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
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
@Composable
private fun KeyboardPanel(
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
        color = DarkPanel,
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth().height(140.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left controls: Sustain + Pitch/Mod
            Column(
                modifier = Modifier.fillMaxHeight().width(76.dp).padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sustain button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (sustainActive)
                                Brush.horizontalGradient(listOf(AccentWarmYellow, AccentCoral))
                            else
                                Brush.horizontalGradient(listOf(LightPanel, DarkPanel))
                        )
                        .border(
                            1.dp,
                            if (sustainActive) AccentWarmYellow else OutlineVariant,
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

            Spacer(Modifier.width(4.dp))

            // Piano
            val keyboardScrollState = rememberScrollState()
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                ScrollablePianoKeyboard(
                    scrollState = keyboardScrollState,
                    activeNote = activeNote,
                    onNoteDown = onNoteDown,
                    onNoteUp = onNoteUp
                )
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
}@Composable
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
        color = SurfaceElevated,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bank Selector
                var expanded by remember { mutableStateOf(false) }
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OutlineVariant)
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bank.ifEmpty { "Bank" },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(TablerIcons.ChevronDown, contentDescription = null, tint = TextDark, modifier = Modifier.size(14.dp))
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(OutlineVariant)
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

                Spacer(Modifier.width(16.dp))

                // Volume Slider
                Icon(TablerIcons.Volume, contentDescription = null, tint = TextDark, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
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

                Spacer(Modifier.width(16.dp))

                // Toggle
                Text("PAD", color = TextDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentSky,
                        uncheckedThumbColor = TextDark,
                        uncheckedTrackColor = OutlineVariant
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Pad note triggers
            val notes = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                notes.forEachIndexed { index, noteName ->
                    val isActive = activePadNote == index
                    Box(
                        modifier = Modifier
                            .size(64.dp, 54.dp)
                            .shadow(
                                elevation = if (isActive) 6.dp else 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = if (isActive) StatusSuccess.copy(alpha = 0.5f) else Color.Transparent,
                                spotColor = if (isActive) StatusSuccess.copy(alpha = 0.5f) else Color.Transparent
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) Brush.verticalGradient(listOf(StatusSuccess, StatusSuccess.copy(alpha=0.7f)))
                                else Brush.verticalGradient(listOf(LightPanel, DarkPanel))
                            )
                            .border(1.dp, if (isActive) StatusSuccess else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable(enabled = enabled) { onPadNoteToggle(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = noteName,
                            color = if (isActive || !enabled) Color.White else TextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

