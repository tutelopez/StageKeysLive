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
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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
    currentConnectedDevices: List<String>,
    midiActivityIndicator: Boolean,

    // Metronome & Recording
    metronomeOn: Boolean,
    onMetronomeToggle: () -> Unit,
    metronomeBpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
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
    // Collapsible keyboard: start expanded on tablets (>=600dp), collapsed on phones
    val configuration = LocalConfiguration.current
    var isKeyboardVisible by remember { mutableStateOf(configuration.screenWidthDp >= 600) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0D12), Color(0xFF121220))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── TOP BAR ────────────────────────────────────────────────────────
            TopBar(
                concertName = concert.name,
                metronomeOn = metronomeOn,
                onMetronomeToggle = onMetronomeToggle,
                metronomeTick = metronomeTick,
                metronomeBpm = metronomeBpm,
                onBpmChange = onBpmChange,
                onBpmChangeFinished = onBpmChangeFinished,
                isRecording = isRecording,
                onRecordToggle = onRecordToggle,
                isPlayingRecording = isPlayingRecording,
                onPlayRecordingClick = onPlayRecordingClick,
                midiActive = midiActivityIndicator,
                onBackClick = onBackClick,
                onSettingsClick = onSettingsClick
            )

            // ─── MAIN AREA: PATCHES + MIXER ─────────────────────────────────────
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                    scrollState = scrollState
                )
            }

            // ─── KEYBOARD TOGGLE + PANEL ────────────────────────────────────
            // Chevron toggle bar
            Surface(
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                color = Color(0xFF1A1A28),
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
                        tint = Color(0xFF606080),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isKeyboardVisible) "TECLADO" else "TECLADO (oculto)",
                        color = Color(0xFF606080),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            // Animated keyboard panel
            AnimatedVisibility(
                visible = isKeyboardVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
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
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(
    concertName: String,
    metronomeOn: Boolean,
    onMetronomeToggle: () -> Unit,
    metronomeTick: Boolean,
    metronomeBpm: Int,
    onBpmChange: (Int) -> Unit,
    onBpmChangeFinished: () -> Unit,
    isRecording: Boolean,
    onRecordToggle: () -> Unit,
    isPlayingRecording: Boolean,
    onPlayRecordingClick: () -> Unit,
    midiActive: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1A26),
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
                    tint = Color(0xFF9090B0),
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
                        .background(Color(0xFF38BDF8), CircleShape)
                )
                Spacer(Modifier.width(6.dp))
            }

            // Metronome beat dot
            Box(
                modifier = Modifier.size(8.dp)
                    .background(
                        if (metronomeTick && metronomeOn) Color(0xFF39FF14) else Color(0xFF2A2A3A),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))

            // Metronome button
            PillButton(
                label = if (metronomeOn) "$metronomeBpm BPM" else "METRO",
                active = metronomeOn,
                activeColor = Color(0xFF39FF14),
                onClick = onMetronomeToggle
            )

            // BPM Slider (only when metronome on)
            if (metronomeOn) {
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = metronomeBpm.toFloat(),
                    onValueChange = { onBpmChange(it.toInt()) },
                    onValueChangeFinished = onBpmChangeFinished,
                    valueRange = 40f..240f,
                    modifier = Modifier.width(72.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF39FF14),
                        activeTrackColor = Color(0xFF39FF14),
                        inactiveTrackColor = Color(0xFF2A2A3A)
                    )
                )
            }

            Spacer(Modifier.width(8.dp))

            // Record button
            PillButton(
                label = if (isRecording) "● REC" else "REC",
                active = isRecording,
                activeColor = Color(0xFFFF2A2A),
                onClick = onRecordToggle
            )

            Spacer(Modifier.width(4.dp))

            // Play recording
            IconButton(onClick = onPlayRecordingClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (isPlayingRecording) TablerIcons.PlayerStop else TablerIcons.PlayerPlay,
                    contentDescription = "Play",
                    tint = if (isPlayingRecording) Color(0xFF38BDF8) else Color(0xFF606080),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Settings gear
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    TablerIcons.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF9090B0),
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
    onClick: () -> Unit
) {
    val bgColor = if (active) activeColor.copy(alpha = 0.18f) else Color(0xFF252535)
    val borderColor = if (active) activeColor else Color(0xFF353548)
    val textColor = if (active) activeColor else Color(0xFF8080A0)

    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
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
    onImport: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF181824),
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
                    color = Color(0xFF5A5A7A),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onImport, modifier = Modifier.size(24.dp)) {
                    Icon(TablerIcons.Download, contentDescription = "Import", tint = Color(0xFF5A5A7A), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(24.dp)) {
                    Icon(TablerIcons.Plus, contentDescription = "Add patch", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                }
            }

            Divider(color = Color(0xFF252535), thickness = 1.dp)
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
        Brush.horizontalGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.2f), Color(0xFF9D4EDD).copy(alpha = 0.1f)))
    else
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    val borderColor = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color.Transparent

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
                color = if (isSelected) Color.White else Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (patch.category.isNotBlank()) {
                Text(
                    text = patch.category,
                    color = Color(0xFF5A5A7A),
                    fontSize = 9.sp
                )
            }
        }
        if (isSelected) {
            IconButton(onClick = onEdit, modifier = Modifier.size(20.dp)) {
                Icon(TablerIcons.Edit, contentDescription = "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
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
        color = Color(0xFF161620),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            Text(
                "MIXER",
                color = Color(0xFF3A3A55),
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
                    listOf(Color.Transparent, Color(0xFF353550), Color.Transparent)
                )
            )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// KEYBOARD PANEL
// ─────────────────────────────────────────────────────────────────────────────
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
        color = Color(0xFF181824),
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
                                Brush.horizontalGradient(listOf(Color(0xFFFBBF24), Color(0xFFEF6C00)))
                            else
                                Brush.horizontalGradient(listOf(Color(0xFF252535), Color(0xFF1C1C2C)))
                        )
                        .border(
                            1.dp,
                            if (sustainActive) Color(0xFFFBBF24) else Color(0xFF353548),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onSustainToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "SUSTAIN",
                        color = if (sustainActive) Color.Black else Color(0xFF606080),
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
            .background(Color(0xFF252535))
            .border(1.dp, Color(0xFF353548), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF8080A0),
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
            .background(Color(0xFF252535))
            .border(1.dp, Color(0xFF353548), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color(0xFF8080A0),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
    }
}