package com.midi.mainstage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Theme Colors (Mainstage-inspired dark professional UI)
val DarkBackground = Color(0xFF0F0F11)
val DarkPanel = Color(0xFF16161A)
val LightPanel = Color(0xFF22222B)
val NeonGreen = Color(0xFF39FF14)
val MainstageBlue = Color(0xFF00D2FF)
val BrightOrange = Color(0xFFFF8C00)
val TextLight = Color(0xFFE5E5E9)
val TextDark = Color(0xFF7F7F8C)

// Data Models mapping to JSON persistence
enum class ScreenState { DASHBOARD, CONCERT, SETTINGS }
enum class SettingsTab { MIDI_MAP, SPLIT_ZONES, AUDIO }

data class RecordingEvent(
    val deltaMs: Long,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean
)

@Composable
fun App(synth: PlatformAudioSynth = remember { PlatformAudioSynth() }) {
    val coroutineScope = rememberCoroutineScope()

    // Navigation and Concert State
    var currentScreen by remember { mutableStateOf(ScreenState.DASHBOARD) }
    var concerts by remember { mutableStateOf<List<Concert>>(emptyList()) }
    var activeConcert by remember { mutableStateOf<Concert?>(null) }
    var selectedPatchIndex by remember { mutableStateOf(0) }

    // Dialog flags
    var showCreateConcertDialog by remember { mutableStateOf(false) }
    var newConcertName by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(SettingsTab.MIDI_MAP) }
    var showChannelSettingsDialog by remember { mutableStateOf<ChannelStripState?>(null) }
    var showAddPatchDialog by remember { mutableStateOf(false) }

    // New Patch Form States
    var newPatchName by remember { mutableStateOf("") }
    var newPatchCategory by remember { mutableStateOf("Keyboards") }
    var newPatchProgram by remember { mutableStateOf("0") }
    var newPatchDescription by remember { mutableStateOf("") }

    // Audio & Metronome State
    var metronomeOn by remember { mutableStateOf(false) }
    var metronomeBpm by remember { mutableStateOf(120) }
    var metronomeVolume by remember { mutableStateOf(0.7f) }
    var metronomeTickLight by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingRecording by remember { mutableStateOf(false) }
    val recordedEvents = remember { mutableStateListOf<RecordingEvent>() }
    var recordingStartTimestamp by remember { mutableStateOf(0L) }

    // Master Output volume and Level meters
    var masterVolume by remember { mutableStateOf(0.8f) }
    val masterVuLevel = remember { Animatable(0f) }

    // Audio Interfaces & Settings state
    var selectedSampleRate by remember { mutableStateOf(44100) }
    var selectedAudioOutput by remember { mutableStateOf("Salida Estéreo Principal (System Default)") }

    // Live performance controls state
    var activeNote by remember { mutableStateOf<Int?>(null) }
    val pitchBend = remember { Animatable(0f) }
    var sustainActive by remember { mutableStateOf(false) }

    // Key states tracker for sustain mapping
    val heldKeys = remember { mutableStateMapOf<Int, Boolean>() }
    val sustainedKeys = remember { mutableStateMapOf<Int, Boolean>() }

    // VU meter level animations per channel strip (up to 8 channels)
    val vuLevels = remember { List(8) { Animatable(0f) } }

    // MIDI mapping state (Mapea CC a Controladores)
    val midiCcMappings = remember { mutableStateMapOf(7 to "Volume", 74 to "Filter Cutoff", 91 to "Reverb Mix", 20 to "Master Volume") }
    var mappingTarget by remember { mutableStateOf<String?>(null) }

    // Trigger visual MIDI indicator
    var midiActivityIndicator by remember { mutableStateOf(false) }
    val triggerMidiFlash = {
        midiActivityIndicator = true
        coroutineScope.launch {
            delay(80)
            midiActivityIndicator = false
        }
    }

    // Load initial concerts database on startup
    LaunchedEffect(Unit) {
        val json = readTextFromFile("concerts.json")
        if (json != null) {
            val list = ConcertSerializer.deserialize(json)
            concerts = list
        } else {
            // Seed default concerts database
            val defaultConcerts = listOf(
                Concert(
                    id = "1",
                    name = "Tour Rock Latino 2026",
                    lastModified = System.currentTimeMillis() - 3600000,
                    patches = listOf(
                        PatchState("Grand Piano Stage", "Keyboards", 0, "Acoustic Grand Steinway soundbank"),
                        PatchState("Rhodes EP Classic", "Keyboards", 4, "Vintage MK I tines with chorus"),
                        PatchState("Synth Horns Poly", "Synths", 62, "Fat 80s polyphonic synth brass"),
                        PatchState("Deep Synth Bass", "Synths", 38, "Warm analog bass with low filter cutoff")
                    ),
                    channels = listOf(
                        ChannelStripState(1, "Piano.sf2", 0.8f, false, false, 0, 127, "#00D2FF"),
                        ChannelStripState(2, "RhodesEP.sf2", 0.7f, false, false, 0, 127, "#FFFF8C00"),
                        ChannelStripState(3, "BassSynth.sf2", 0.6f, false, false, 0, 59, "#FF39FF14"),
                        ChannelStripState(4, "BrassPoly.sf2", 0.75f, false, false, 60, 127, "#FFFF0055")
                    )
                ),
                Concert(
                    id = "2",
                    name = "Jazz Fusion Live Set",
                    lastModified = System.currentTimeMillis() - 86400000,
                    patches = listOf(
                        PatchState("Hammond B3 Organ", "Organs", 16, "Hammond rotary simulation on Channel 1"),
                        PatchState("Soft Tines Vibraphone", "Mallets", 11, "Electric vibraphone with stereo chorus"),
                        PatchState("Warm Strings Pad", "Strings", 49, "Slow attack pad for jazz ballads")
                    ),
                    channels = listOf(
                        ChannelStripState(1, "TonewheelOrgan.sf2", 0.8f, false, false, 0, 127, "#00D2FF"),
                        ChannelStripState(2, "VibeMallets.sf2", 0.65f, false, false, 0, 127, "#FFFF8C00"),
                        ChannelStripState(3, "AmbientStrings.sf2", 0.7f, false, false, 0, 127, "#FF39FF14")
                    )
                )
            )
            concerts = defaultConcerts
            saveTextToFile("concerts.json", ConcertSerializer.serialize(defaultConcerts))
        }
    }

    // Save concerts list helper
    val saveConcertsList = { list: List<Concert> ->
        concerts = list
        saveTextToFile("concerts.json", ConcertSerializer.serialize(list))
    }

    // Metronome sound click volume control
    LaunchedEffect(metronomeOn, metronomeBpm, metronomeVolume) {
        if (metronomeOn) {
            val delayMs = (60000L / metronomeBpm)
            while (metronomeOn) {
                metronomeTickLight = true
                synth.noteOn(96, (metronomeVolume * 127).toInt()) // Click controlled by volume
                delay(80)
                synth.noteOff(96)
                metronomeTickLight = false
                delay((delayMs - 80).coerceAtLeast(10))
            }
        }
    }

    // Keep Master Volume updated on Audio Synth
    LaunchedEffect(masterVolume) {
        synth.setVolume(masterVolume)
    }

    // Play Note On triggers individual VU meters and Master VU meter
    val playNoteOn: (Int) -> Unit = { note ->
        activeNote = note
        triggerMidiFlash()
        heldKeys[note] = true

        activeConcert?.let { concert ->
            val pitchOffset = (pitchBend.value * 2).toInt()
            val playedNote = note + pitchOffset

            var noteTriggered = false
            concert.channels.forEachIndexed { idx, ch ->
                if (!ch.isMuted && playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                    synth.noteOn(playedNote, (ch.volume * 90).toInt())
                    noteTriggered = true
                    
                    coroutineScope.launch {
                        vuLevels[idx].animateTo(ch.volume * 0.9f, tween(50))
                    }
                }
            }

            if (noteTriggered) {
                coroutineScope.launch {
                    masterVuLevel.animateTo(masterVolume * 0.9f, tween(50))
                }
            }

            // Record MIDI event
            if (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTimestamp
                recordedEvents.add(RecordingEvent(elapsed, note, 90, true))
            }
        }
    }

    val playNoteOff: (Int) -> Unit = { note ->
        heldKeys.remove(note)
        triggerMidiFlash()

        if (sustainActive) {
            sustainedKeys[note] = true
        } else {
            activeNote = if (activeNote == note) null else activeNote
            activeConcert?.let { concert ->
                val pitchOffset = (pitchBend.value * 2).toInt()
                val playedNote = note + pitchOffset

                concert.channels.forEachIndexed { idx, ch ->
                    if (playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                        synth.noteOff(playedNote)
                        coroutineScope.launch {
                            vuLevels[idx].animateTo(0f, tween(250))
                        }
                    }
                }

                coroutineScope.launch {
                    masterVuLevel.animateTo(0f, tween(250))
                }

                // Record MIDI event
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTimestamp
                    recordedEvents.add(RecordingEvent(elapsed, note, 0, false))
                }
            }
        }
    }

    // Handle Sustain release
    LaunchedEffect(sustainActive) {
        if (!sustainActive) {
            val notesToRelease = sustainedKeys.keys.filter { !heldKeys.containsKey(it) }
            activeConcert?.let { concert ->
                val pitchOffset = (pitchBend.value * 2).toInt()
                notesToRelease.forEach { note ->
                    val playedNote = note + pitchOffset
                    concert.channels.forEachIndexed { idx, ch ->
                        if (playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                            synth.noteOff(playedNote)
                            coroutineScope.launch {
                                vuLevels[idx].animateTo(0f, tween(250))
                            }
                        }
                    }
                    if (activeNote == note) activeNote = null
                }
                coroutineScope.launch {
                    masterVuLevel.animateTo(0f, tween(250))
                }
            }
            sustainedKeys.clear()
        }
    }

    // UI SCREEN CONTROLLER
    when (currentScreen) {
        ScreenState.DASHBOARD -> {
            DashboardScreen(
                concerts = concerts,
                onCreateConcertClick = { showCreateConcertDialog = true },
                onOpenLastConcertClick = {
                    val last = concerts.maxByOrNull { it.lastModified }
                    if (last != null) {
                        activeConcert = last
                        currentScreen = ScreenState.CONCERT
                    }
                },
                onSelectConcert = { concert ->
                    activeConcert = concert
                    currentScreen = ScreenState.CONCERT
                }
            )
        }
        ScreenState.CONCERT -> {
            activeConcert?.let { concert ->
                ConcertViewScreen(
                    concert = concert,
                    selectedPatchIndex = selectedPatchIndex,
                    onSelectPatch = { selectedPatchIndex = it },
                    onAddPatchClick = { showAddPatchDialog = true },
                    onBackClick = { currentScreen = ScreenState.DASHBOARD },
                    onSettingsClick = { showSettingsDialog = true },
                    
                    // Metronome & Recording
                    metronomeOn = metronomeOn,
                    onMetronomeToggle = { metronomeOn = !metronomeOn },
                    metronomeBpm = metronomeBpm,
                    onBpmChange = { metronomeBpm = it },
                    metronomeVolume = metronomeVolume,
                    onMetronomeVolumeChange = { metronomeVolume = it },
                    metronomeTick = metronomeTickLight,
                    isRecording = isRecording,
                    onRecordToggle = {
                        if (isRecording) {
                            isRecording = false
                        } else {
                            recordedEvents.clear()
                            recordingStartTimestamp = System.currentTimeMillis()
                            isRecording = true
                        }
                    },
                    isPlayingRecording = isPlayingRecording,
                    onPlayRecordingClick = {
                        if (isPlayingRecording) {
                            isPlayingRecording = false
                        } else if (recordedEvents.isNotEmpty()) {
                            isPlayingRecording = true
                            coroutineScope.launch {
                                var elapsed = 0L
                                recordedEvents.forEach { ev ->
                                    val sleepTime = ev.deltaMs - elapsed
                                    if (sleepTime > 0) {
                                        delay(sleepTime)
                                    }
                                    if (ev.isNoteOn) {
                                        synth.noteOn(ev.note, ev.velocity)
                                    } else {
                                        synth.noteOff(ev.note)
                                    }
                                    elapsed = ev.deltaMs
                                }
                                isPlayingRecording = false
                            }
                        }
                    },

                    // Channel strip callback triggers dialog and additions
                    onVolumeChange = { chId, vol ->
                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(volume = vol) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
                    },
                    onMuteToggle = { chId ->
                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isMuted = !it.isMuted) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
                    },
                    onSoloToggle = { chId ->
                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isSoloed = !it.isSoloed) else it
                        }
                        val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                        activeConcert = updatedConcert
                        saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
                    },
                    onAddChannelClick = {
                        if (concert.channels.size < 8) {
                            val nextId = (concert.channels.maxOfOrNull { it.id } ?: 0) + 1
                            val newChannel = ChannelStripState(
                                id = nextId,
                                sf2Name = "SynthPreset_${nextId}.sf2",
                                volume = 0.8f,
                                isMuted = false,
                                isSoloed = false,
                                keyRangeStart = 0,
                                keyRangeEnd = 127,
                                colorHex = listOf("#00D2FF", "#FFFF8C00", "#FF39FF14", "#FFFF0055", "#FF9D00FF", "#FFFFEE00", "#FF0044FF", "#FFEA00FF").random()
                            )
                            val updatedChannels = concert.channels + newChannel
                            val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                            activeConcert = updatedConcert
                            saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
                        }
                    },
                    onChannelGearClick = { showChannelSettingsDialog = it },

                    // Master output configuration mapping
                    masterVolume = masterVolume,
                    onMasterVolumeChange = { masterVolume = it },
                    masterVuLevel = masterVuLevel.value,

                    // Key events
                    activeNote = activeNote,
                    onNoteDown = playNoteOn,
                    onNoteUp = playNoteOff,
                    pitchBend = pitchBend,
                    sustainActive = sustainActive,
                    onSustainToggle = { sustainActive = !sustainActive },
                    vuLevels = vuLevels
                )
            }
        }
        ScreenState.SETTINGS -> {
            // Handled as dialog overlay
        }
    }

    // --- DIALOGS ---

    // 1. Create Concert Dialog
    if (showCreateConcertDialog) {
        AlertDialog(
            onDismissRequest = { showCreateConcertDialog = false },
            title = { Text("CREAR NUEVO CONCIERTO", color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newConcertName,
                    onValueChange = { newConcertName = it },
                    label = { Text("Nombre del Concierto") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextLight,
                        unfocusedTextColor = TextLight,
                        focusedBorderColor = MainstageBlue,
                        unfocusedBorderColor = LightPanel
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newConcertName.isNotBlank()) {
                            val newConcert = Concert(
                                id = System.currentTimeMillis().toString(),
                                name = newConcertName,
                                lastModified = System.currentTimeMillis(),
                                patches = listOf(
                                    PatchState("Default Piano", "Keyboards", 0, "Acoustic Grand Piano")
                                ),
                                channels = listOf(
                                    ChannelStripState(1, "PianoDefault.sf2", 0.8f, false, false, 0, 127, "#00D2FF")
                                )
                            )
                            val newList = concerts + newConcert
                            saveConcertsList(newList)
                            activeConcert = newConcert
                            selectedPatchIndex = 0
                            newConcertName = ""
                            showCreateConcertDialog = false
                            currentScreen = ScreenState.CONCERT
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MainstageBlue)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateConcertDialog = false }) {
                    Text("Cancelar", color = TextDark)
                }
            },
            containerColor = DarkPanel
        )
    }

    // 2. Add Patch Dialog
    if (showAddPatchDialog) {
        AlertDialog(
            onDismissRequest = { showAddPatchDialog = false },
            title = { Text("AÑADIR NUEVO PATCH", color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newPatchName,
                        onValueChange = { newPatchName = it },
                        label = { Text("Nombre") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchCategory,
                        onValueChange = { newPatchCategory = it },
                        label = { Text("Categoría") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchProgram,
                        onValueChange = { newPatchProgram = it },
                        label = { Text("Número de Programa MIDI (0-127)") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchDescription,
                        onValueChange = { newPatchDescription = it },
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val active = activeConcert
                        if (active != null && newPatchName.isNotBlank()) {
                            val program = newPatchProgram.toIntOrNull() ?: 0
                            val newPatch = PatchState(newPatchName, newPatchCategory, program, newPatchDescription)
                            val updatedPatches = active.patches + newPatch
                            val updatedConcert = active.copy(
                                patches = updatedPatches,
                                lastModified = System.currentTimeMillis()
                            )
                            val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                            saveConcertsList(newList)
                            activeConcert = updatedConcert
                            selectedPatchIndex = updatedPatches.lastIndex
                            
                            // Reset inputs
                            newPatchName = ""
                            newPatchCategory = "Keyboards"
                            newPatchProgram = "0"
                            newPatchDescription = ""
                            showAddPatchDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                ) {
                    Text("Añadir", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPatchDialog = false }) {
                    Text("Cancelar", color = TextDark)
                }
            },
            containerColor = DarkPanel
        )
    }

    // 3. Channel Settings Dialog (Gear Menu on Channel Strip)
    showChannelSettingsDialog?.let { chState ->
        AlertDialog(
            onDismissRequest = { showChannelSettingsDialog = null },
            title = { Text("CONFIGURAR CANAL ${chState.id}", color = TextLight, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("SoundFont Actual: ${chState.sf2Name}", color = TextDark, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
                    
                    // Options List
                    Button(
                        onClick = {
                            val active = activeConcert
                            if (active != null) {
                                val sf2List = listOf("PianoGrand.sf2", "StringsClassic.sf2", "SynthLeadFat.sf2", "MidiDrums.sf2", "HammondOrgan.sf2")
                                val currentIdx = sf2List.indexOf(chState.sf2Name)
                                val nextSf2 = sf2List[(currentIdx + 1) % sf2List.size]
                                
                                val updatedChannels = active.channels.map {
                                    if (it.id == chState.id) it.copy(sf2Name = nextSf2) else it
                                }
                                val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                                saveConcertsList(newList)
                                activeConcert = updatedConcert
                                showChannelSettingsDialog = updatedConcert.channels.find { it.id == chState.id }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LightPanel),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Text("Cambiar SF2 Preset", color = TextLight)
                    }

                    // Dynamic Vibrant Color grid
                    Text("CAMBIAR COLOR DE CANAL:", color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    
                    val vibrantColors = listOf(
                        "#00D2FF", // Neon Cyan
                        "#FFFF8C00", // Neon Orange
                        "#FF39FF14", // Neon Green
                        "#FFFF0055", // Neon Red/Pink
                        "#FF9D00FF", // Neon Purple
                        "#FFFFEE00", // Neon Yellow
                        "#FF0044FF", // Electric Blue
                        "#FFEA00FF"  // Hot Magenta
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        vibrantColors.forEach { hexColor ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(parseColorHex(hexColor))
                                    .border(
                                        width = if (chState.colorHex == hexColor) 2.dp else 0.dp,
                                        color = if (chState.colorHex == hexColor) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        val active = activeConcert
                                        if (active != null) {
                                            val updatedChannels = active.channels.map {
                                                if (it.id == chState.id) it.copy(colorHex = hexColor) else it
                                            }
                                            val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                            val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                                            saveConcertsList(newList)
                                            activeConcert = updatedConcert
                                            showChannelSettingsDialog = updatedConcert.channels.find { it.id == chState.id }
                                        }
                                    }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val active = activeConcert
                            if (active != null) {
                                val updatedChannels = active.channels.filter { it.id != chState.id }
                                val updatedConcert = active.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                                saveConcertsList(newList)
                                activeConcert = updatedConcert
                                showChannelSettingsDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1D1D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eliminar Canal", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChannelSettingsDialog = null }) {
                    Text("Cerrar", color = MainstageBlue)
                }
            },
            containerColor = DarkPanel
        )
    }

    // 4. Global Settings Dialog
    if (showSettingsDialog) {
        Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBackground.copy(alpha = 0.95f))
                    .padding(24.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GEAR DE CONFIGURACION GLOBAL",
                            color = TextLight,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSettingsDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close settings", tint = TextLight)
                        }
                    }

                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .width(180.dp)
                                .fillMaxHeight()
                                .background(DarkPanel, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            val tabs = listOf(
                                SettingsTab.MIDI_MAP to "Mapear MIDI",
                                SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                SettingsTab.AUDIO to "Interfaces de Audio"
                            )
                            tabs.forEach { (tab, label) ->
                                val isSelected = activeSettingsTab == tab
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (isSelected) MainstageBlue.copy(alpha = 0.15f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) MainstageBlue else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { activeSettingsTab = tab }
                                        .padding(12.dp)
                                ) {
                                    Text(label, color = if (isSelected) TextLight else TextDark, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(DarkPanel, RoundedCornerShape(8.dp))
                                .padding(16.dp)
                        ) {
                            when (activeSettingsTab) {
                                SettingsTab.MIDI_MAP -> {
                                    MidiMappingSettingsScreen(
                                        mappings = midiCcMappings,
                                        mappingTarget = mappingTarget,
                                        onStartMapping = { target ->
                                            mappingTarget = target
                                            // [POINT 2 FIX] Real MIDI Learn — listens for the
                                            // next CC from a physical controller (7-second window)
                                            synth.startMidiLearn(
                                                target = target,
                                                onCaptured = { cc ->
                                                    midiCcMappings[cc] = target
                                                    mappingTarget = null
                                                },
                                                onTimeout = {
                                                    // Desktop or no MIDI device: silently cancel
                                                    if (mappingTarget == target) mappingTarget = null
                                                }
                                            )
                                        }
                                    )
                                }
                                SettingsTab.SPLIT_ZONES -> {
                                    activeConcert?.let { concert ->
                                        SplitKeyboardSettingsScreen(
                                            concert = concert,
                                            onUpdateRange = { channelId, start, end ->
                                                val updatedChannels = concert.channels.map {
                                                    if (it.id == channelId) it.copy(keyRangeStart = start, keyRangeEnd = end) else it
                                                }
                                                val updatedConcert = concert.copy(channels = updatedChannels, lastModified = System.currentTimeMillis())
                                                val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                                                saveConcertsList(newList)
                                                activeConcert = updatedConcert
                                            }
                                        )
                                    }
                                }
                                SettingsTab.AUDIO -> {
                                    AudioSettingsTabScreen(
                                        sampleRate = selectedSampleRate,
                                        onSampleRateChange = { selectedSampleRate = it },
                                        selectedOutput = selectedAudioOutput,
                                        onOutputChange = { selectedAudioOutput = it }
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

// --- SUB-SCREENS AND TAB RENDERING ---

@Composable
fun DashboardScreen(
    concerts: List<Concert>,
    onCreateConcertClick: () -> Unit,
    onOpenLastConcertClick: () -> Unit,
    onSelectConcert: (Concert) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(16.dp).background(BrightOrange, RoundedCornerShape(8.dp)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "MIDILIVE PRO",
                    color = TextLight,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f).height(120.dp).clickable { onCreateConcertClick() },
                    colors = CardDefaults.cardColors(containerColor = DarkPanel),
                    border = BorderStroke(1.dp, LightPanel)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = NeonGreen, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("CREAR CONCIERTO", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Card(
                    modifier = Modifier.weight(1f).height(120.dp).clickable { onOpenLastConcertClick() },
                    colors = CardDefaults.cardColors(containerColor = DarkPanel),
                    border = BorderStroke(1.dp, LightPanel)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎹", fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ABRIR ULTIMO CONCIERTO", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Text(
                text = "MIS CONCIERTOS RECIENTES",
                color = TextDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sorted = concerts.sortedByDescending { it.lastModified }
                items(sorted) { concert ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkPanel, RoundedCornerShape(8.dp))
                            .border(1.dp, LightPanel, RoundedCornerShape(8.dp))
                            .clickable { onSelectConcert(concert) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(concert.name, color = TextLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${concert.patches.size} Patches  •  ${concert.channels.size} Canales SF2", color = TextDark, fontSize = 11.sp)
                        }
                        Text("Abrir ➔", color = MainstageBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConcertViewScreen(
    concert: Concert,
    selectedPatchIndex: Int,
    onSelectPatch: (Int) -> Unit,
    onAddPatchClick: () -> Unit,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,

    // Metronome & Recording
    metronomeOn: Boolean,
    onMetronomeToggle: () -> Unit,
    metronomeBpm: Int,
    onBpmChange: (Int) -> Unit,
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

    // Master output volume fader
    masterVolume: Float,
    onMasterVolumeChange: (Float) -> Unit,
    masterVuLevel: Float,

    // Keyboard & Expression
    activeNote: Int?,
    onNoteDown: (Int) -> Unit,
    onNoteUp: (Int) -> Unit,
    pitchBend: Animatable<Float, *>,
    sustainActive: Boolean,
    onSustainToggle: () -> Unit,
    vuLevels: List<Animatable<Float, *>>
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(DarkBackground).padding(8.dp)) {
        
        // 1. TOP HEADER: Status, Metronome, Recorder & Gear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(DarkPanel, RoundedCornerShape(6.dp))
                .border(1.dp, LightPanel, RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextLight)
                }
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = concert.name.uppercase(),
                    color = TextLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
            }

            // METRONOME & RECORDER WITH VOLUME SLIDER & ICONS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (metronomeTick) NeonGreen else Color(0xFF1E3525), RoundedCornerShape(5.dp))
                        .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                )

                // Metronome Toggle Button with Icon
                Button(
                    onClick = onMetronomeToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (metronomeOn) NeonGreen else LightPanel
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⏱️ ", fontSize = 12.sp)
                        Text(
                            "METRONOMO",
                            color = if (metronomeOn) Color.Black else TextLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Metronome Volume Control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊", fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                    Slider(
                        value = metronomeVolume,
                        onValueChange = onMetronomeVolumeChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.width(70.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = LightPanel
                        )
                    )
                }

                // BPM speed slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$metronomeBpm BPM", color = TextLight, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.width(4.dp))
                    Slider(
                        value = metronomeBpm.toFloat(),
                        onValueChange = { onBpmChange(it.toInt()) },
                        valueRange = 40f..240f,
                        modifier = Modifier.width(80.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MainstageBlue,
                            activeTrackColor = MainstageBlue,
                            inactiveTrackColor = LightPanel
                        )
                    )
                }

                // Recorder LED & Button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isRecording) Color.Red else Color(0xFF3A1010), RoundedCornerShape(5.dp))
                            .border(1.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = onRecordToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) Color.Red else LightPanel
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            if (isRecording) "GRABANDO" else "GRABAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = onPlayRecordingClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingRecording) MainstageBlue else LightPanel
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        if (isPlayingRecording) "PLAYING" else "PLAY REC",
                        color = if (isPlayingRecording) Color.Black else TextLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = "Global Settings", tint = TextLight)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. MAIN MIXER & PATCHES VIEW
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            
            // Patches list (adopted with create patch "+" button)
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(DarkPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, LightPanel, RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PATCHES",
                        color = TextDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onAddPatchClick,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add patch", tint = NeonGreen, modifier = Modifier.size(16.dp))
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(concert.patches.size) { index ->
                        val patch = concert.patches[index]
                        val isSelected = index == selectedPatchIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isSelected) MainstageBlue.copy(alpha = 0.15f) else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MainstageBlue else Color.Transparent,
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable { onSelectPatch(index) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = patch.programNumber.toString().padStart(3, '0'),
                                color = if (isSelected) MainstageBlue else TextDark,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(24.dp)
                            )
                            Column {
                                Text(
                                    text = patch.name,
                                    color = if (isSelected) TextLight else TextLight.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // MIXER DE CANALES CON SCROLL HORIZONTAL Y CANAL DE SALIDA MASTER GENERAL
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(DarkPanel, RoundedCornerShape(6.dp))
                    .border(1.dp, LightPanel, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "MIXER DE CANALES SF2",
                    color = TextDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Populate up to 8 channels dynamically
                    concert.channels.forEach { chState ->
                        // Match channels to their VU levels
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

                    // Add channel strip "+" Card button if channels size < 8
                    if (concert.channels.size < 8) {
                        AddChannelButton(onClick = onAddChannelClick)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Fixed Master Output General Channel (L-R OUT)
                    MasterOutputChannelItem(
                        volume = masterVolume,
                        level = masterVuLevel,
                        onVolumeChange = onMasterVolumeChange,
                        onMidiMapClick = {
                            // Automatically triggers Settings Dialog tab MIDI Learn
                            onSettingsClick()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. BOTTOM PANEL: Piano de 8 Octavas con Sustain y Pitch Bend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(Color.Black, RoundedCornerShape(6.dp))
                .border(1.dp, LightPanel, RoundedCornerShape(6.dp))
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.fillMaxHeight().width(80.dp).padding(4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onSustainToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sustainActive) BrightOrange else LightPanel
                    ),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Text("SUSTAIN", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                PitchBendWheel(
                    value = pitchBend.value,
                    onValueChange = {
                        coroutineScope.launch {
                            pitchBend.snapTo(it)
                        }
                    },
                    onRelease = {
                        coroutineScope.launch {
                            pitchBend.animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                        }
                    },
                    modifier = Modifier.weight(1f).width(44.dp).padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            val keyboardScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                ScrollablePianoKeyboard(
                    scrollState = keyboardScrollState,
                    activeNote = activeNote,
                    onNoteDown = onNoteDown,
                    onNoteUp = onNoteUp
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Column(
                modifier = Modifier.fillMaxHeight().width(64.dp).padding(4.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val current = keyboardScrollState.value
                            val keyWidth = 32
                            keyboardScrollState.animateScrollTo((current - 7 * keyWidth).coerceAtLeast(0))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LightPanel),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("OCT -", color = TextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val current = keyboardScrollState.value
                            val keyWidth = 32
                            keyboardScrollState.animateScrollTo((current + 7 * keyWidth).coerceAtMost(keyboardScrollState.maxValue))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LightPanel),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("OCT +", color = TextLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChannelStripItem(
    state: ChannelStripState,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    onGearClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(LightPanel.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .border(1.dp, LightPanel, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.sf2Name.substringBefore(".sf2").uppercase(),
                color = parseColorHex(state.colorHex),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onGearClick,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Channel options", tint = TextDark, modifier = Modifier.size(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VolumeFader(
                value = state.volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxHeight().width(24.dp)
            )

            LevelMeter(
                level = level,
                modifier = Modifier.fillMaxHeight().width(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // MUTE & SOLO VIBRANT COLOR TOGGLES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Mute Button: Glow bright red if muted
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state.isMuted) Color(0xFFFF2A2A) else Color(0xFF1E1E24))
                    .border(1.dp, if (state.isMuted) Color.White else Color(0xFF33333C), RoundedCornerShape(3.dp))
                    .clickable { onMuteToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "M",
                    color = if (state.isMuted) Color.White else Color(0xFF757580),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Solo Button: Glow bright yellow if active
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (state.isSoloed) Color(0xFFFFD600) else Color(0xFF1E1E24))
                    .border(1.dp, if (state.isSoloed) Color.Black else Color(0xFF33333C), RoundedCornerShape(3.dp))
                    .clickable { onSoloToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "S",
                    color = if (state.isSoloed) Color.Black else Color(0xFF757580),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddChannelButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(LightPanel.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, LightPanel.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Add, contentDescription = "Add Channel", tint = NeonGreen, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text("AÑADIR CANAL", color = TextDark, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MasterOutputChannelItem(
    volume: Float,
    level: Float,
    onVolumeChange: (Float) -> Unit,
    onMidiMapClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(DarkBackground, RoundedCornerShape(4.dp))
            .border(2.dp, MainstageBlue, RoundedCornerShape(4.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MASTER OUT",
                color = MainstageBlue,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onMidiMapClick,
                modifier = Modifier.size(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "MIDI Map Master", tint = MainstageBlue, modifier = Modifier.size(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VolumeFader(
                value = volume,
                onValueChange = onVolumeChange,
                modifier = Modifier.fillMaxHeight().width(24.dp)
            )
            LevelMeter(
                level = level,
                modifier = Modifier.fillMaxHeight().width(10.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(MainstageBlue.copy(alpha = 0.1f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("LR OUT", color = MainstageBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun EmptyChannelPlaceholder(id: Int) {
    Box(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight()
            .background(Color(0xFF131317), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, Color(0xFF1C1C22)), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CANAL $id", color = TextDark.copy(alpha = 0.5f), fontSize = 10.sp)
            Text("VACIO", color = TextDark.copy(alpha = 0.3f), fontSize = 8.sp)
        }
    }
}

@Composable
fun PitchBendWheel(
    value: Float,
    onValueChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
            .border(1.dp, LightPanel, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        val height = size.height.toFloat()
                        val dragFactor = 1.5f
                        val newValue = (value - dragAmount.y / height * dragFactor).coerceIn(-1f, 1f)
                        onValueChange(newValue)
                    },
                    onDragEnd = { onRelease() },
                    onDragCancel = { onRelease() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val totalHeight = maxHeight
        val handleHeight = 14.dp
        val usableHeight = totalHeight - handleHeight
        val normalized = (value + 1f) / 2f
        val handleOffset = usableHeight * (1f - normalized)

        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.DarkGray)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(handleHeight)
                .align(Alignment.TopCenter)
                .offset(y = handleOffset)
                .background(Color.LightGray, RoundedCornerShape(2.dp))
                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
fun ScrollablePianoKeyboard(
    scrollState: ScrollState,
    activeNote: Int?,
    onNoteDown: (Int) -> Unit,
    onNoteUp: (Int) -> Unit
) {
    val totalWhiteKeys = 52
    val whiteNotes = remember {
        listOf(
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
    }

    val blackNotesMap = remember {
        mapOf(
            21 to 22, 24 to 25, 26 to 27, 29 to 30, 31 to 32, 33 to 34, 36 to 37, 38 to 39,
            41 to 42, 43 to 44, 45 to 46, 48 to 49, 50 to 51, 53 to 54, 55 to 56, 57 to 58,
            60 to 61, 62 to 63, 65 to 66, 67 to 68, 69 to 70, 72 to 73, 74 to 75, 77 to 78,
            79 to 80, 81 to 82, 84 to 85, 86 to 87, 89 to 90, 91 to 92, 93 to 94, 96 to 97,
            98 to 99, 101 to 102, 103 to 104, 105 to 106
        )
    }

    val keyWidth = 32.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            whiteNotes.forEach { note ->
                val isPressed = activeNote == note
                Box(
                    modifier = Modifier
                        .width(keyWidth)
                        .fillMaxHeight()
                        .background(
                            if (isPressed) MainstageBlue.copy(alpha = 0.6f) else Color.White,
                            RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                        )
                        .border(1.dp, Color.Black)
                        .pointerInput(note) {
                            detectDragGestures(
                                onDragStart = { onNoteDown(note) },
                                onDragEnd = { onNoteUp(note) },
                                onDragCancel = { onNoteUp(note) },
                                onDrag = { _, _ -> }
                            )
                        }
                        .clickable { onNoteDown(note); onNoteUp(note) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxHeight()) {
            whiteNotes.forEachIndexed { idx, note ->
                val hasBlack = blackNotesMap.containsKey(note) && idx < totalWhiteKeys - 1
                
                Spacer(modifier = Modifier.width(keyWidth / 2))

                if (hasBlack) {
                    val blackNote = blackNotesMap[note]!!
                    val isPressed = activeNote == blackNote
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(0.6f)
                            .background(
                                if (isPressed) MainstageBlue else Color(0xFF1E1E1E),
                                RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)
                            )
                            .border(1.dp, Color.Black)
                            .pointerInput(blackNote) {
                                detectDragGestures(
                                    onDragStart = { onNoteDown(blackNote) },
                                    onDragEnd = { onNoteUp(blackNote) },
                                    onDragCancel = { onNoteUp(blackNote) },
                                    onDrag = { _, _ -> }
                                )
                            }
                            .clickable { onNoteDown(blackNote); onNoteUp(blackNote) }
                    )
                } else {
                    Spacer(modifier = Modifier.width(18.dp))
                }

                Spacer(modifier = Modifier.width(keyWidth / 2 - 18.dp))
            }
        }
    }
}

// --- SETTINGS TABS COMPOSABLES ---

@Composable
fun MidiMappingSettingsScreen(
    mappings: Map<Int, String>,
    mappingTarget: String?,
    onStartMapping: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("ASIGNACION DE CONTROLADORES MIDI CC (MIDI LEARN)", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Haz clic en \"Mapear\" al lado del control correspondiente y mueve el potenciómetro o fader de tu teclado físico para enlazarlo.",
            color = TextDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val controllers = listOf("Volume", "Filter Cutoff", "Reverb Mix", "Master Volume")
        controllers.forEach { target ->
            val mappedCc = mappings.entries.find { it.value == target }?.key
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(LightPanel, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(target.uppercase(), color = TextLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        text = if (mappedCc != null) "Mapeado a MIDI CC $mappedCc" else "Sin mapear",
                        color = if (mappedCc != null) NeonGreen else TextDark,
                        fontSize = 11.sp
                    )
                }
                
                Button(
                    onClick = { onStartMapping(target) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mappingTarget == target) BrightOrange else MainstageBlue
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (mappingTarget == target) "ESCUCHANDO..." else "MAPEAR",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SplitKeyboardSettingsScreen(
    concert: Concert,
    onUpdateRange: (Int, Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("RANGOS DE TECLADO Y SPLITS (OCTAVAS A0 - C8)", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(
            "Visualiza y modifica las zonas de las teclas activas para cada archivo SF2 cargado.",
            color = TextDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(Color.Black, RoundedCornerShape(4.dp))
                .border(1.dp, LightPanel, RoundedCornerShape(4.dp))
        ) {
            concert.channels.forEach { ch ->
                val startPercent = ch.keyRangeStart / 127f
                val endPercent = ch.keyRangeEnd / 127f
                val widthPercent = endPercent - startPercent
                
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.3f)
                        .fillMaxWidth(widthPercent)
                        .align(Alignment.CenterStart)
                        .offset(x = (startPercent * 280).dp)
                        .background(parseColorHex(ch.colorHex), RoundedCornerShape(1.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        concert.channels.forEach { ch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(LightPanel, RoundedCornerShape(4.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ch.sf2Name.substringBefore(".sf2").uppercase(),
                    color = parseColorHex(ch.colorHex),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.width(100.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nota Min: ", color = TextDark, fontSize = 11.sp)
                    Slider(
                        value = ch.keyRangeStart.toFloat(),
                        onValueChange = { onUpdateRange(ch.id, it.toInt(), ch.keyRangeEnd) },
                        valueRange = 0f..ch.keyRangeEnd.toFloat(),
                        modifier = Modifier.width(80.dp)
                    )
                    Text(ch.keyRangeStart.toString(), color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nota Max: ", color = TextDark, fontSize = 11.sp)
                    Slider(
                        value = ch.keyRangeEnd.toFloat(),
                        onValueChange = { onUpdateRange(ch.id, ch.keyRangeStart, it.toInt()) },
                        valueRange = ch.keyRangeStart.toFloat()..127f,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(ch.keyRangeEnd.toString(), color = TextLight, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(30.dp))
                }
            }
        }
    }
}

@Composable
fun AudioSettingsTabScreen(
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    selectedOutput: String,
    onOutputChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("INTERFACES DE AUDIO Y LATENCIA", color = TextLight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(16.dp))

        Text("DISPOSITIVO DE SALIDA:", color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(LightPanel, RoundedCornerShape(4.dp))
                .clickable {
                    val outputs = listOf(
                        "Salida Estéreo Principal (System Default)",
                        "ASIO: Focusrite USB Audio Driver",
                        "ASIO: Behringer U-Phoria Driver",
                        "DirectSound: Altavoces de Computadora"
                    )
                    val nextIdx = (outputs.indexOf(selectedOutput) + 1) % outputs.size
                    onOutputChange(outputs[nextIdx])
                }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedOutput, color = TextLight, fontSize = 13.sp)
            Text("Cambiar ➔", color = MainstageBlue, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("VELOCIDAD DE MUESTREO (BITRATE/SAMPLE RATE):", color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(44100, 48000, 96000).forEach { rate ->
                val isSelected = sampleRate == rate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) MainstageBlue.copy(alpha = 0.15f) else LightPanel)
                        .border(1.dp, if (isSelected) MainstageBlue else Color.Transparent, RoundedCornerShape(4.dp))
                        .clickable { onSampleRateChange(rate) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rate Hz", color = if (isSelected) TextLight else TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F1512), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF1B2C21), RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESTADO DEL DRIVER:", color = TextDark, fontSize = 11.sp)
                Text("ACTIVO (LOW LATENCY)", color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun VolumeFader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .background(Color(0xFF0F0F0F), RoundedCornerShape(4.dp))
            .border(1.dp, LightPanel, RoundedCornerShape(4.dp))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val height = size.height
                    val newY = change.position.y
                    val newValue = 1f - (newY / height).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val totalHeight = maxHeight
        val handleHeight = 20.dp
        val usableHeight = totalHeight - handleHeight
        val handleOffset = usableHeight * (1f - value)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.height / 10f
            for (i in 1..9) {
                val y = i * step
                val lineWidth = if (i % 2 == 0) 12f else 6f
                drawLine(
                    color = Color.DarkGray,
                    start = Offset((size.width - lineWidth) / 2f, y),
                    end = Offset((size.width + lineWidth) / 2f, y),
                    strokeWidth = 2f
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(value.coerceIn(0.01f, 1f))
                .background(NeonGreen.copy(alpha = 0.2f), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .border(
                    BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(handleHeight)
                .align(Alignment.TopCenter)
                .offset(y = handleOffset)
                .background(Color.Gray, RoundedCornerShape(2.dp))
                .border(1.dp, Color.White, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.DarkGray)
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F0F0F), RoundedCornerShape(2.dp))
            .border(1.dp, LightPanel, RoundedCornerShape(2.dp))
            .padding(1.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(level.coerceIn(0f, 1f))
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Red)
            )
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth()
                    .background(Color.Yellow)
            )
            Box(
                modifier = Modifier
                    .weight(7f)
                    .fillMaxWidth()
                    .background(NeonGreen)
            )
        }
    }
}

fun parseColorHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    if (clean.length == 6) {
        val r = clean.substring(0, 2).toInt(16)
        val g = clean.substring(2, 4).toInt(16)
        val b = clean.substring(4, 6).toInt(16)
        return Color(r, g, b)
    }
    return Color.White
}
