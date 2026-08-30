package com.midi.mainstage

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex

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

import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Theme Colors now defined in Theme.kt

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
    
    var performanceStats by remember { mutableStateOf<PerformanceStats?>(null) }
    var batteryLevel by remember { mutableStateOf(100) }
    var batteryCharging by remember { mutableStateOf(false) }
    var audioDiagnostics by remember { mutableStateOf("INICIALIZANDO...") }

    LaunchedEffect(currentScreen) {
        if (currentScreen == ScreenState.CONCERT) {
            setKeepScreenOn(true)
            synth.setPerformanceListener { stats ->
                performanceStats = stats
            }
            synth.startPerformanceMonitor()
            while (isActive) {
                batteryLevel = getBatteryLevel()
                batteryCharging = isBatteryCharging()
                audioDiagnostics = synth.getAudioDiagnostics()
                delay(30000)
            }
        } else {
            setKeepScreenOn(false)
            synth.stopPerformanceMonitor()
        }
    }
    var selectedPatchIndex by remember { mutableStateOf(0) }
    var currentConnectedDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentAudioDevices by remember { mutableStateOf<List<AudioOutputDeviceInfo>>(emptyList()) }

    // File Picker and Snackbar State
    var showSf2Picker by remember { mutableStateOf(false) }
    var isLoadingSf2 by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var prevMidiDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(currentConnectedDevices) {
        if (prevMidiDevices.isNotEmpty() || currentConnectedDevices.isNotEmpty()) {
            val added = currentConnectedDevices - prevMidiDevices.toSet()
            val removed = prevMidiDevices - currentConnectedDevices.toSet()
            added.forEach { launch { snackbarHostState.showSnackbar("Teclado MIDI conectado: $it") } }
            removed.forEach { launch { snackbarHostState.showSnackbar("Teclado MIDI desconectado: $it") } }
        }
        prevMidiDevices = currentConnectedDevices
    }

    var prevAudioDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(currentAudioDevices) {
        if (prevAudioDevices.isNotEmpty() || currentAudioDevices.isNotEmpty()) {
            val currentNames = currentAudioDevices.map { it.name }
            val added = currentNames - prevAudioDevices.toSet()
            val removed = prevAudioDevices - currentNames.toSet()
            added.forEach { launch { snackbarHostState.showSnackbar("Interfaz de audio conectada: $it") } }
            removed.forEach { launch { snackbarHostState.showSnackbar("Interfaz de audio desconectada: $it") } }
            prevAudioDevices = currentNames
        }
    }

    // Dialog flags
    var showCreateConcertDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Concert?>(null) }
    var concertToEdit by remember { mutableStateOf<Concert?>(null) }
    var newConcertName by remember { mutableStateOf("") }
    
    val saveConcertsList: (List<Concert>) -> Unit = { list ->
        concerts = list
        saveTextToFile("concerts.json", ConcertSerializer.serialize(list))
    }

    // Import / Export states
    var showPackagePicker by remember { mutableStateOf(false) }
    var concertToExport by remember { mutableStateOf<Concert?>(null) }
    var patchToExport by remember { mutableStateOf<PatchState?>(null) }

    SkPackagePicker(
        show = showPackagePicker,
        onPackageSelected = { concert, patch ->
            showPackagePicker = false
            if (concert != null) {
                val newList = concerts + concert
                saveConcertsList(newList)
            } else if (patch != null && activeConcert != null) {
                val updatedConcert = activeConcert!!.copy(
                    patches = activeConcert!!.patches + patch,
                    lastModified = System.currentTimeMillis()
                )
                val newList = concerts.map { if (it.id == updatedConcert.id) updatedConcert else it }
                saveConcertsList(newList)
                activeConcert = updatedConcert
            }
        }
    )

    PackageExporter(
        concertToExport = concertToExport,
        patchToExport = patchToExport,
        onExportComplete = {
            concertToExport = null
            patchToExport = null
        }
    )
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsOpenedFromConcert by remember { mutableStateOf(false) }
    var activeSettingsTab by remember { mutableStateOf(SettingsTab.MIDI_MAP) }
    var showChannelSettingsDialog by remember { mutableStateOf<ChannelStripState?>(null) }
    var showAddPatchDialog by remember { mutableStateOf(false) }
    var patchToEdit by remember { mutableStateOf<PatchState?>(null) }

    // New Patch Form States
    var newPatchName by remember { mutableStateOf("") }
    var newPatchCategory by remember { mutableStateOf("Keyboards") }
    var newPatchProgram by remember { mutableStateOf("0") }
    var newPatchDescription by remember { mutableStateOf("") }
    var newPatchTranspose by remember { mutableStateOf(0) }

    // Audio & Metronome State
    var metronomeOn by remember { mutableStateOf(false) }
    var metronomeBpm by remember { mutableStateOf(120) }
    var metronomeBpmEffective by remember { mutableStateOf(120) }
    var metronomeVolume by remember { mutableStateOf(0.7f) }
    val tapTimestamps = remember { androidx.compose.runtime.mutableStateListOf<Long>() }

    val onTapTempo = {
        val now = System.currentTimeMillis()
        if (tapTimestamps.isNotEmpty() && now - tapTimestamps.last() > 2000) {
            tapTimestamps.clear()
        }
        tapTimestamps.add(now)
        if (tapTimestamps.size > 8) {
            tapTimestamps.removeAt(0)
        }
        if (tapTimestamps.size >= 2) {
            val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
            val avgInterval = intervals.average()
            if (avgInterval > 0) {
                var bpm = (60000 / avgInterval).toInt()
                bpm = bpm.coerceIn(40, 240)
                metronomeBpm = bpm
                metronomeBpmEffective = bpm
            }
        }
    }

    // Continuous Pad Engine State
    var padEnabled by remember { mutableStateOf(false) }
    var padVolume by remember { mutableStateOf(0.7f) }
    var padBank by remember { mutableStateOf("") }
    var activePadNote by remember { mutableStateOf<Int?>(null) }
    val availablePadBanks = listOf("stage_abba_pad", "stage_dark_pad", "stage_pad_reverse", "stage_pad_shimmer", "stage_pad_synth", "stage_shimmer_2", "stage_warm_pad", "stage_worship")

    // Sync pad state with engine when it changes
    LaunchedEffect(padEnabled) { 
        synth.padSetEnabled(padEnabled) 
        if (!padEnabled) {
            synth.padNoteOff()
            activePadNote = null
        }
    }
    LaunchedEffect(padVolume) { synth.padSetVolume(padVolume) }
    LaunchedEffect(padBank) { if (padBank.isNotEmpty()) synth.padSetBank(padBank) }
    var metronomeTickLight by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isPlayingRecording by remember { mutableStateOf(false) }
    val recordedEvents = remember { mutableStateListOf<RecordingEvent>() }
    var recordingStartTimestamp by remember { mutableStateOf(0L) }

    // Master Output volume and Level meters
    var masterVolume by remember { mutableStateOf(0.8f) }
    val masterVuLevel = remember { Animatable(0f) }

    // Audio Interfaces & Settings state
    var selectedSampleRate by remember { mutableStateOf(48000) }
    var pendingSampleRate by remember { mutableStateOf<Int?>(null) }
    var isRestartingAudio by remember { mutableStateOf(false) }
    var selectedAudioOutput by remember { mutableStateOf("Salida EstÃƒÆ’Ã‚Â©reo Principal (System Default)") }

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
    val midiCcMappings = remember { mutableStateMapOf<Int, MidiTarget>(
        7 to MidiTarget.ChannelVolume(0),
        74 to MidiTarget.FilterCutoff,
        91 to MidiTarget.ReverbMix,
        20 to MidiTarget.MasterVolume,
        64 to MidiTarget.Sustain
    )}
    var mappingTarget by remember { mutableStateOf<MidiTarget?>(null) }
    var octaveShift by remember { mutableStateOf(0) }
    var currentPatchTranspose by remember { mutableStateOf(0) }
    val modulation = remember { Animatable(0f) }

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
    LaunchedEffect(activeConcert?.id) {
        val concert = activeConcert
        if (concert != null) {
            concert.channels.forEach { ch ->
                if (ch.sf2Path != null) {
                    synth.loadSoundFont(ch.sf2Path, ch.id)
                }
            }
        }
    }

    // Auto-save debounce for live mixer adjustments (volume, mute, solo)
    LaunchedEffect(activeConcert?.channels) {
        if (activeConcert != null) {
            delay(3000)
            saveConcertsList(concerts)
        }
    }

    

    val updateChannelsAndPatchSnapshotOnlyState = { newChannels: List<ChannelStripState> ->
        val concert = activeConcert
        if (concert != null) {
            val updatedPatches = if (selectedPatchIndex in concert.patches.indices) {
                concert.patches.mapIndexed { idx, patch ->
                    if (idx == selectedPatchIndex) {
                        val newSnapshot = newChannels.map { ch ->
                            PatchChannelSnapshot(
                                channelId = ch.id, name = ch.name, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                                volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                                keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd,
                                colorHex = ch.colorHex
                            )
                        }
                        patch.copy(channelsSnapshot = newSnapshot)
                    } else patch
                }
            } else concert.patches

            val updatedConcert = concert.copy(channels = newChannels, patches = updatedPatches, lastModified = System.currentTimeMillis())
            val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
            concerts = newList
            activeConcert = updatedConcert
        }
    }

    val updateChannelsAndPatchSnapshot = { newChannels: List<ChannelStripState> ->
        updateChannelsAndPatchSnapshotOnlyState(newChannels)
        saveConcertsList(concerts)
    }

    val applyPatch = { patchIndex: Int ->
        val concert = activeConcert
        if (concert != null && patchIndex in concert.patches.indices) {
            currentPatchTranspose = concert.patches[patchIndex].transposeSemitones
            // WE DO NOT CALL allNotesOff() HERE so that active notes can keep sounding on their old channels (ping-pong shadow channels)
            
            // --- Step 1: Persist the CURRENT patch's live channels into its own snapshot ---
            // This ensures that any unsaved edits to the current patch are not lost when switching.
            val currentLiveChannels = concert.channels
            val currentSnapshot = currentLiveChannels.map { ch ->
                PatchChannelSnapshot(
                    channelId = ch.id, name = ch.name, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                    volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                    keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd, colorHex = ch.colorHex
                )
            }
            val patchesWithCurrentSaved = if (selectedPatchIndex in concert.patches.indices) {
                concert.patches.mapIndexed { idx, p ->
                    if (idx == selectedPatchIndex) p.copy(channelsSnapshot = currentSnapshot) else p
                }
            } else concert.patches

            // --- Step 2: Restore the TARGET patch's own independent snapshot ---
            val targetPatch = patchesWithCurrentSaved[patchIndex]
            val restoredChannels = if (targetPatch.channelsSnapshot.isNotEmpty()) {
                targetPatch.channelsSnapshot.map { snap ->
                    ChannelStripState(
                        id = snap.channelId, name = snap.name, sf2Name = snap.sf2Name, sf2Path = snap.sf2Path,
                        volume = snap.volume, isMuted = snap.isMuted, isSoloed = snap.isSoloed,
                        keyRangeStart = snap.keyRangeStart, keyRangeEnd = snap.keyRangeEnd,
                        colorHex = snap.colorHex
                    )
                }
            } else {
                // Patch has no snapshot yet ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬  start with a single clean channel
                listOf(
                    ChannelStripState(
                        id = 1, name = "Canal 1", sf2Name = "Sin Asignar", sf2Path = null,
                        volume = 0.8f, isMuted = false, isSoloed = false,
                        keyRangeStart = 0, keyRangeEnd = 127, colorHex = "#00D2FF"
                    )
                )
            }

            // Load SoundFonts for the restored channels
            restoredChannels.forEach { ch ->
                if (ch.sf2Path != null) {
                    synth.loadSoundFont(ch.sf2Path, ch.id)
                }
            }

            val updatedConcert = concert.copy(
                channels = restoredChannels,
                patches = patchesWithCurrentSaved,
                lastModified = System.currentTimeMillis()
            )
            saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
            activeConcert = updatedConcert
            selectedPatchIndex = patchIndex
        }
    }

    // Play Note On triggers individual VU meters and Master VU meter
    val playNoteOn: (Int, Int) -> Unit = { note, velocity ->
        activeNote = note
        triggerMidiFlash()
        heldKeys[note] = true

        activeConcert?.let { concert ->
            val pitchOffset = (pitchBend.value * 2).toInt()
            val playedNote = note + pitchOffset + (octaveShift * 12) + currentPatchTranspose
            val anySolo = concert.channels.any { it.isSoloed }

            var noteTriggered = false
            concert.channels.forEachIndexed { idx, ch ->
                val shouldPlay = if (anySolo) ch.isSoloed else !ch.isMuted
                if (shouldPlay && playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                    synth.noteOn(playedNote, (ch.volume * velocity).toInt(), ch.id)
                    noteTriggered = true
                    
                    coroutineScope.launch {
                        vuLevels[idx].animateTo(ch.volume * (velocity / 127f), tween(50))
                    }
                }
            }

            if (noteTriggered) {
                coroutineScope.launch {
                    masterVuLevel.animateTo(masterVolume * (velocity / 127f), tween(50))
                }
            }

            // No pad routing from keyboard/MIDI per user request

            // Record MIDI event
            if (isRecording) {
                val elapsed = System.currentTimeMillis() - recordingStartTimestamp
                recordedEvents.add(RecordingEvent(elapsed, note, velocity, true))
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
                val playedNote = note + pitchOffset + (octaveShift * 12) + currentPatchTranspose
                val anySolo = concert.channels.any { it.isSoloed }

                concert.channels.forEachIndexed { idx, ch ->
                    val shouldPlay = if (anySolo) ch.isSoloed else !ch.isMuted
                    if (shouldPlay && playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                        synth.noteOff(playedNote, ch.id)
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
                val anySolo = concert.channels.any { it.isSoloed }
                
                notesToRelease.forEach { note ->
                    val playedNote = note + pitchOffset + (octaveShift * 12) + currentPatchTranspose
                    concert.channels.forEachIndexed { idx, ch ->
                        val shouldPlay = if (anySolo) ch.isSoloed else !ch.isMuted
                        if (shouldPlay && playedNote >= ch.keyRangeStart && playedNote <= ch.keyRangeEnd) {
                            synth.noteOff(playedNote, ch.id)
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

    val stopConcert = {
        activeConcert?.let { concert ->
            synth.allNotesOff()
            coroutineScope.launch {
                masterVuLevel.animateTo(0f, tween(50))
                vuLevels.forEach { it.animateTo(0f, tween(50)) }
            }
        }
        heldKeys.clear()
        sustainedKeys.clear()
        activeNote = null
        sustainActive = false
        metronomeOn = false
    }

    LaunchedEffect(currentConnectedDevices.firstOrNull()) {
        val deviceName = currentConnectedDevices.firstOrNull()
        val fileName = if (deviceName != null) "mappings_${deviceName}.json" else "mappings_default.json"
        
        val json = readTextFromFile(fileName) ?: readTextFromFile("mappings_default.json")
        if (json != null) {
            val loadedMap = MidiMappingSerializer.deserialize(json)
            if (loadedMap.isNotEmpty()) {
                midiCcMappings.clear()
                midiCcMappings.putAll(loadedMap)
                synth.syncMidiMappings(midiCcMappings)
            }
        }
    }

    LaunchedEffect(Unit) {
        synth.syncMidiMappings(midiCcMappings)
        
        synth.setMidiListener(
            onMappedCc = { target, floatValue ->
                triggerMidiFlash()
                
                val handleNextPatch = {
                    val concert = activeConcert
                    if (concert != null && concert.patches.isNotEmpty()) {
                        val next = (selectedPatchIndex + 1) % concert.patches.size
                        applyPatch(next)
                    }
                }
                
                val handlePreviousPatch = {
                    val concert = activeConcert
                    if (concert != null && concert.patches.isNotEmpty()) {
                        val prev = (selectedPatchIndex - 1 + concert.patches.size) % concert.patches.size
                        applyPatch(prev)
                    }
                }
                
                when (target) {
                    is MidiTarget.ChannelVolume -> {
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(volume = floatValue)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                            }
                        }
                    }
                    is MidiTarget.ChannelMute -> {
                        val toggle = floatValue > 0.5f
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(isMuted = toggle)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                            }
                        }
                    }
                    is MidiTarget.ChannelSolo -> {
                        val toggle = floatValue > 0.5f
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(isSoloed = toggle)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                            }
                        }
                    }
                    is MidiTarget.Pad -> {
                        val padNote = 36 + target.padIndex
                        if (floatValue > 0f) {
                            playNoteOn(padNote, (floatValue * 127).toInt())
                        } else {
                            playNoteOff(padNote)
                        }
                    }
                    is MidiTarget.Pot -> {
                        // Pot implementation
                    }
                    is MidiTarget.MasterVolume -> masterVolume = floatValue
                    is MidiTarget.FilterCutoff -> {
                        activeConcert?.channels?.forEach { ch ->
                            synth.setFilterCutoff(floatValue, ch.id)
                        }
                    }
                    is MidiTarget.ReverbMix -> {
                        synth.setReverb(floatValue)
                    }
                    is MidiTarget.Sustain -> sustainActive = floatValue > 0.5f
                    is MidiTarget.Modulation -> {
                        coroutineScope.launch {
                            modulation.snapTo(floatValue)
                        }
                        activeConcert?.channels?.forEach { ch ->
                            synth.setModulation(floatValue, ch.id)
                        }
                    }
                    is MidiTarget.OctaveUp -> {
                        if (floatValue > 0f) {
                            octaveShift = (octaveShift + 1).coerceAtMost(3)
                        }
                    }
                    is MidiTarget.OctaveDown -> {
                        if (floatValue > 0f) {
                            octaveShift = (octaveShift - 1).coerceAtLeast(-3)
                        }
                    }
                    is MidiTarget.NextPatch -> {
                        if (floatValue > 0f) {
                            handleNextPatch()
                        }
                    }
                    is MidiTarget.PreviousPatch -> {
                        if (floatValue > 0f) {
                            val prev = (selectedPatchIndex - 1).coerceAtLeast(0)
                            if (prev != selectedPatchIndex) applyPatch(prev)
                        }
                    }
                }
            },
            onNote = { note, velocity, isNoteOn ->
                if (isNoteOn) {
                    playNoteOn(note, velocity)
                } else {
                    playNoteOff(note)
                }
            },
            onPitchBend = { bend ->
                triggerMidiFlash()
                coroutineScope.launch {
                    pitchBend.animateTo(bend, tween(20))
                }
            },
            onDeviceConnectionChanged = { names ->
                currentConnectedDevices = names
            }
        )

        synth.setAudioDeviceListener { devices ->
            currentAudioDevices = devices
        }
        synth.refreshAudioDevices()

        // Poll audio diagnostics to reflect real driver status once ready
        coroutineScope.launch {
            while (true) {
                audioDiagnostics = synth.getAudioDiagnostics()
                delay(1500)
            }
        }

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
                        ChannelStripState(1, "Canal 1", "Piano.sf2", null, 0.8f, false, false, 0, 127, "#00D2FF"),
                        ChannelStripState(2, "Canal 2", "RhodesEP.sf2", null, 0.7f, false, false, 0, 127, "#FFFF8C00"),
                        ChannelStripState(3, "Canal 3", "BassSynth.sf2", null, 0.6f, false, false, 0, 59, "#FF39FF14"),
                        ChannelStripState(4, "Canal 4", "BrassPoly.sf2", null, 0.75f, false, false, 60, 127, "#FFFF0055")
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
                        ChannelStripState(1, "Canal 1", "TonewheelOrgan.sf2", null, 0.8f, false, false, 0, 127, "#38BDF8"),
                        ChannelStripState(2, "Canal 2", "VibeMallets.sf2", null, 0.65f, false, false, 0, 127, "#FBBF24"),
                        ChannelStripState(3, "Canal 3", "AmbientStrings.sf2", null, 0.7f, false, false, 0, 127, "#39FF14")
                    )
                )
            )
            concerts = defaultConcerts
            saveTextToFile("concerts.json", ConcertSerializer.serialize(defaultConcerts))
        }
    }



    // Metronome sound click volume control
    LaunchedEffect(metronomeOn, metronomeBpmEffective, metronomeVolume) {
        if (metronomeOn) {
            var nextTickTime = System.currentTimeMillis()
            while (metronomeOn) {
                val now = System.currentTimeMillis()
                if (now >= nextTickTime) {
                    metronomeTickLight = true
                    synth.noteOn(96, (metronomeVolume * 127).toInt(), 0) // Click controlled by volume
                    nextTickTime += (60000L / metronomeBpmEffective)
                    launch {
                        delay(80)
                        synth.noteOff(96)
                        metronomeTickLight = false
                    }
                }
                delay(5)
            }
        }
    }

    // Keep Master Volume updated on Audio Synth
    LaunchedEffect(masterVolume) {
        synth.setVolume(masterVolume)
    }

    // UI SCREEN CONTROLLER
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
        ScreenState.DASHBOARD -> {
            DashboardScreen(
                concerts = concerts,
                onCreateConcertClick = { 
                    concertToEdit = null
                    newConcertName = ""
                    showCreateConcertDialog = true 
                },
                onEditConcertClick = { concert ->
                    concertToEdit = concert
                    newConcertName = concert.name
                    showCreateConcertDialog = true
                },
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
                },
                onDeleteConcert = { concert ->
                    showDeleteConfirmDialog = concert
                },
                onExportConcertClick = { concertToExport = it },
                onImportClick = { showPackagePicker = true },
                onSettingsClick = { 
                    settingsOpenedFromConcert = false
                    activeSettingsTab = SettingsTab.MIDI_MAP
                    showSettingsDialog = true 
                }
            )
        }
        ScreenState.CONCERT -> {
            activeConcert?.let { concert ->
                ConcertViewScreen(
                    concert = concert,
                    onPanicClick = {
                        synth.allNotesOff()
                        synth.padHardKillAll()
                    },
                    performanceStats = performanceStats,
                    batteryLevel = batteryLevel,
                    batteryCharging = batteryCharging,
                    audioDiagnostics = audioDiagnostics,
                    selectedPatchIndex = selectedPatchIndex,
                    onSelectPatch = applyPatch,
                    onAddPatchClick = { 
                        patchToEdit = null
                        newPatchName = ""
                        newPatchCategory = "Keyboards"
                        newPatchProgram = "0"
                        newPatchDescription = ""
                        newPatchTranspose = 0
                        showAddPatchDialog = true 
                    },
                    onEditPatchClick = { patch ->
                        patchToEdit = patch
                        newPatchName = patch.name
                        newPatchCategory = patch.category
                        newPatchProgram = patch.programNumber.toString()
                        newPatchDescription = patch.description
                        newPatchTranspose = patch.transposeSemitones
                        showAddPatchDialog = true
                    },
                    onDeletePatch = { patch ->
                        val updatedPatches = concert.patches.filter { it.id != patch.id }
                        val updatedConcert = concert.copy(patches = updatedPatches, lastModified = System.currentTimeMillis())
                        val newList = concerts.map { if (it.id == concert.id) updatedConcert else it }
                        saveConcertsList(newList)
                        activeConcert = updatedConcert
                        if (selectedPatchIndex >= updatedPatches.size) {
                            selectedPatchIndex = (updatedPatches.size - 1).coerceAtLeast(0)
                        }
                    },
                    onExportPatchClick = { patchToExport = it },
                    onImportPatchClick = { showPackagePicker = true },
                    onBackClick = { 
                        stopConcert()
                        activeConcert = null
                        currentScreen = ScreenState.DASHBOARD 
                    },
                    onSettingsClick = { 
                        settingsOpenedFromConcert = true
                        activeSettingsTab = SettingsTab.SPLIT_ZONES
                        showSettingsDialog = true 
                    },
                    currentConnectedDevices = currentConnectedDevices,
                    midiActivityIndicator = midiActivityIndicator,
                    
                    // Metronome & Recording
                    metronomeOn = metronomeOn,
                    onMetronomeToggle = { metronomeOn = !metronomeOn },
                    metronomeBpm = metronomeBpm,
                    onBpmChange = { metronomeBpm = it },
                    onBpmChangeFinished = { metronomeBpmEffective = metronomeBpm },
                    onTapTempo = onTapTempo,
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
                                        playNoteOn(ev.note, ev.velocity)
                                    } else {
                                        playNoteOff(ev.note)
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
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onMuteToggle = { chId ->
                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isMuted = !it.isMuted) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onSoloToggle = { chId ->
                        val updatedChannels = concert.channels.map {
                            if (it.id == chId) it.copy(isSoloed = !it.isSoloed) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onAddChannelClick = {
                        if (concert.channels.size < 8) {
                            val nextId = (concert.channels.maxOfOrNull { it.id } ?: 0) + 1
                            val newChannel = ChannelStripState(
                                id = nextId,
                                name = "Canal $nextId",
                                sf2Name = "SynthPreset_${nextId}.sf2",
                                sf2Path = null,
                                volume = 0.8f,
                                isMuted = false,
                                isSoloed = false,
                                keyRangeStart = 0,
                                keyRangeEnd = 127,
                                colorHex = listOf("#38BDF8", "#39FF14", "#9D4EDD", "#FB7185", "#2DD4BF", "#F472B6", "#FBBF24").random()
                            )
                            val updatedChannels = concert.channels + newChannel
                            updateChannelsAndPatchSnapshot(updatedChannels)
                        }
                    },
                    onChannelGearClick = { showChannelSettingsDialog = it },

                    // Master output configuration mapping
                    masterVolume = masterVolume,
                    onMasterVolumeChange = { masterVolume = it },
                    masterVuLevel = masterVuLevel.value,

                    // Pad Engine
                    padEnabled = padEnabled,
                    onPadEnabledChange = { padEnabled = it },
                    padVolume = padVolume,
                    onPadVolumeChange = { padVolume = it },
                    padBank = padBank,
                    onPadBankChange = { padBank = it },
                    availablePadBanks = availablePadBanks,
                    activePadNote = activePadNote,
                    onPadNoteToggle = { note ->
                        if (activePadNote == note) {
                            synth.padNoteOff()
                            activePadNote = null
                        } else {
                            synth.padNoteOn(note)
                            activePadNote = note
                        }
                    },

                    // Key events
                    activeNote = activeNote,
                    onNoteDown = { playNoteOn(it, 90) },
                    onNoteUp = playNoteOff,
                    pitchBend = pitchBend,
                    modulation = modulation,
                    onModulationChange = { value ->
                        coroutineScope.launch { modulation.snapTo(value) }
                        activeConcert?.channels?.forEach { ch ->
                            synth.setModulation(value, ch.id)
                        }
                    },
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
        
        Sf2FilePicker(showSf2Picker) { path ->
            showSf2Picker = false
            if (path != null) {
                val channel = showChannelSettingsDialog
                val active = activeConcert
                if (channel != null && active != null) {
                    isLoadingSf2 = true
                    coroutineScope.launch {
                        val success = synth.loadSoundFont(path, channel.id)
                        if (success) {
                            val sf2Name = path.substringAfterLast("/")
                            if (channel.sf2Path != null && channel.sf2Path != path) {
                                deleteLocalFile(channel.sf2Path)
                            }
                            val updatedChannels = active.channels.map {
                                if (it.id == channel.id) it.copy(sf2Name = sf2Name, sf2Path = path) else it
                            }
                            updateChannelsAndPatchSnapshot(updatedChannels)
                            showChannelSettingsDialog = activeConcert?.channels?.find { it.id == channel.id }
                        } else {
                            deleteLocalFile(path)
                            snackbarHostState.showSnackbar("Error al cargar el archivo .sf2")
                        }
                        isLoadingSf2 = false
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Create Concert Dialog
    // Audio Engine Restart Dialog
    if (pendingSampleRate != null) {
        val newRate = pendingSampleRate!!
        AlertDialog(
            onDismissRequest = { pendingSampleRate = null },
            title = { Text("Cambiar Sample Rate", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Cambiar la frecuencia de muestreo a $newRate Hz cortarÃƒÆ’Ã‚Â¡ brevemente el audio mientras se reinicia el motor. Ãƒâ€šÃ‚Â¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSampleRate = null
                    isRestartingAudio = true
                    selectedSampleRate = newRate
                    
                    // Controlled restart
                    synth.allNotesOff()
                    coroutineScope.launch(Dispatchers.Default) {
                        synth.initializeEngine(newRate)
                        
                        // Back to Main to update UI and reload SoundFonts
                        withContext(Dispatchers.Main) {
                            activeConcert?.channels?.forEach { ch ->
                                if (ch.sf2Path != null) {
                                    synth.loadSoundFont(ch.sf2Path, ch.id)
                                }
                            }
                            audioDiagnostics = synth.getAudioDiagnostics()
                            isRestartingAudio = false
                        }
                    }
                }) {
                    Text("REINICIAR MOTOR")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSampleRate = null }) {
                    Text("CANCELAR")
                }
            }
        )
    }

    if (showCreateConcertDialog) {
        AlertDialog(
            onDismissRequest = { showCreateConcertDialog = false },
            title = { Text(if (concertToEdit != null) "EDITAR CONCIERTO" else "CREAR NUEVO CONCIERTO", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
            text = {
                OutlinedTextField(
                    value = newConcertName,
                    onValueChange = { newConcertName = it },
                    label = { Text("Nombre del Concierto") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newConcertName.isNotBlank()) {
                            if (concertToEdit != null) {
                                val updated = concertToEdit!!.copy(name = newConcertName, lastModified = System.currentTimeMillis())
                                val newList = concerts.map { if (it.id == updated.id) updated else it }
                                saveConcertsList(newList)
                                if (activeConcert?.id == updated.id) activeConcert = updated
                            } else {
                                val newConcert = Concert(
                                    id = "concert_${System.currentTimeMillis()}",
                                    name = newConcertName,
                                    lastModified = System.currentTimeMillis(),
                                    patches = listOf(
                                        PatchState("Default Piano", "Keyboards", 0, "Acoustic Grand Piano")
                                    ),
                                    channels = listOf(
                                        ChannelStripState(1, "Canal 1", "PianoDefault.sf2", null, 0.8f, false, false, 0, 127, "#38BDF8")
                                    )
                                )
                                val newList = concerts + newConcert
                                saveConcertsList(newList)
                                activeConcert = newConcert
                                selectedPatchIndex = 0
                                currentScreen = ScreenState.CONCERT
                            }
                            newConcertName = ""
                            showCreateConcertDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = AppShapes.medium
                ) {
                    Text(if (concertToEdit != null) "Guardar" else "Crear", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateConcertDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = AppShapes.large
        )
    }

    // 2. Add Patch Dialog
    if (showAddPatchDialog) {
        AlertDialog(
            onDismissRequest = { showAddPatchDialog = false },
            title = { Text(if (patchToEdit != null) "EDITAR PATCH" else "AÃƒÆ’Ã¢â‚¬ËœADIR NUEVO PATCH", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    val tfColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    OutlinedTextField(
                        value = newPatchName,
                        onValueChange = { newPatchName = it },
                        label = { Text("Nombre") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchCategory,
                        onValueChange = { newPatchCategory = it },
                        label = { Text("CategorÃƒÆ’Ã‚Â­a") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchProgram,
                        onValueChange = { newPatchProgram = it },
                        label = { Text("NÃƒÆ’Ã‚Âºmero de Programa MIDI (0-127)") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newPatchDescription,
                        onValueChange = { newPatchDescription = it },
                        label = { Text("DescripciÃƒÆ’Ã‚Â³n") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transpose (Semitonos):", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (newPatchTranspose > -12) newPatchTranspose-- }) {
                                Text("-", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("${if (newPatchTranspose > 0) "+" else ""}$newPatchTranspose", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { if (newPatchTranspose < 12) newPatchTranspose++ }) {
                                Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val active = activeConcert
                        if (active != null && newPatchName.isNotBlank()) {
                            val program = newPatchProgram.toIntOrNull() ?: 0
                            val updatedPatches = if (patchToEdit != null) {
                                active.patches.map { if (it.id == patchToEdit!!.id) it.copy(name = newPatchName, category = newPatchCategory, programNumber = program, description = newPatchDescription, transposeSemitones = newPatchTranspose) else it }
                            } else {
                                val defaultSnapshot = listOf(
                                    PatchChannelSnapshot(
                                        channelId = 1,
                                        name = "Canal 1",
                                        sf2Name = "Sin Asignar",
                                        sf2Path = null,
                                        volume = 0.8f,
                                        isMuted = false,
                                        isSoloed = false,
                                        keyRangeStart = 0,
                                        keyRangeEnd = 127,
                                        colorHex = "#38BDF8"
                                    )
                                )
                                val newPatch = PatchState(newPatchName, newPatchCategory, program, newPatchDescription, transposeSemitones = newPatchTranspose, channelsSnapshot = defaultSnapshot)
                                active.patches + newPatch
                            }
                            
                            val updatedConcert = active.copy(
                                patches = updatedPatches,
                                lastModified = System.currentTimeMillis()
                            )
                            val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                            saveConcertsList(newList)
                            activeConcert = updatedConcert
                            if (patchToEdit == null) selectedPatchIndex = updatedPatches.lastIndex
                            
                            // Reset inputs
                            newPatchName = ""
                            newPatchCategory = "Keyboards"
                            newPatchProgram = "0"
                            newPatchDescription = ""
                            showAddPatchDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                    shape = AppShapes.medium
                ) {
                    Text(if (patchToEdit != null) "Guardar" else "AÃƒÆ’Ã‚Â±adir", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPatchDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = AppShapes.large
        )
    }

    // Delete Concert Confirmation Dialog
    showDeleteConfirmDialog?.let { concert ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = {
                Text(
                    "¿Eliminar concierto?",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar \"${concert.name}\"? Esta acción no se puede deshacer.",
                    color = TextDark,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newList = concerts.filter { it.id != concert.id }
                        saveConcertsList(newList)
                        if (activeConcert?.id == concert.id) {
                            stopConcert()
                            activeConcert = null
                        }
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusError,
                        contentColor = Color.White
                    ),
                    shape = AppShapes.medium
                ) {
                    Text("Eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar", color = TextDark)
                }
            },
            containerColor = DarkPanel,
            shape = AppShapes.large
        )
    }

    // 3. Channel Settings Dialog (Gear Menu on Channel Strip)
    showChannelSettingsDialog?.let { chState ->
        AlertDialog(
            onDismissRequest = { showChannelSettingsDialog = null },
            title = { Text("CONFIGURAR CANAL ${chState.id}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = chState.name,
                        onValueChange = { newValue ->
                            val active = activeConcert
                            if (active != null) {
                                val updatedChannels = active.channels.map {
                                    if (it.id == chState.id) it.copy(name = newValue) else it
                                }
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                            }
                        },
                        label = { Text("Nombre del canal") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Text("SoundFont Actual: ${chState.sf2Name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                    
                    if (isLoadingSf2) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp).align(Alignment.CenterHorizontally).size(32.dp)
                        )
                    } else {
                        // Options List
                        Button(
                            onClick = {
                                showSf2Picker = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = AppShapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text("Cambiar SF2 Preset", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Dynamic Vibrant Color grid
                    Text("CAMBIAR COLOR DE CANAL:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
                    
                    val vibrantColors = listOf(
                        "#38BDF8", // Sky
                        "#39FF14", // Neon Green
                        "#9D4EDD", // Purple
                        "#FB7185", // Coral
                        "#2DD4BF", // Mint
                        "#F472B6", // Pink
                        "#FBBF24"  // Warm Yellow
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
                                            updateChannelsAndPatchSnapshot(updatedChannels)
                                            showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
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
                                updateChannelsAndPatchSnapshot(updatedChannels)
                                showChannelSettingsDialog = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusError.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Eliminar Canal", color = Color.White)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChannelSettingsDialog = null }) {
                    Text("Cerrar", color = AccentSky)
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
                            text = "Configuración de Concierto y Patches",
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
                            val tabs = if (settingsOpenedFromConcert) {
                                listOf(
                                    SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            } else {
                                listOf(
                                    SettingsTab.MIDI_MAP to "Mapear MIDI",
                                    SettingsTab.AUDIO to "Interfaces de Audio"
                                )
                            }
                            tabs.forEach { (tab, label) ->
                                val isSelected = activeSettingsTab == tab
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(
                                            if (isSelected) AccentSky.copy(alpha = 0.15f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) AccentSky else Color.Transparent,
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
                                        connectedDevices = currentConnectedDevices,
                                        onStartMapping = { target ->
                                            mappingTarget = target
                                            // [POINT 2 FIX] Real MIDI Learn ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â listens for the
                                            // next CC from a physical controller (7-second window)
                                            synth.startMidiLearn(
                                                target = target,
                                                onCaptured = { cc ->
                                                    midiCcMappings[cc] = target
                                                    synth.syncMidiMappings(midiCcMappings)
                                                    mappingTarget = null
                                                    // Save mappings to disk for current device
                                                    try {
                                                        val jsonStr = MidiMappingSerializer.serialize(midiCcMappings.toMap())
                                                        val fileName = currentConnectedDevices.firstOrNull()?.let { "mappings_$it.json" } ?: "mappings_default.json"
                                                        saveTextToFile(fileName, jsonStr)
                                                    } catch (e: Exception) {
                                                    }
                                                },
                                                onTimeout = {
                                                    mappingTarget = null
                                                }
                                            )
                                        }
                                    )
                                }
                                SettingsTab.SPLIT_ZONES -> {
                                    activeConcert?.let { concert ->
                                        val currentPatch = concert.patches.getOrNull(selectedPatchIndex)
                                        SplitKeyboardSettingsScreen(
                                            concert = concert,
                                            selectedPatchName = currentPatch?.name,
                                            onUpdateRange = { channelId, start, end ->
                                                val updatedChannels = concert.channels.map {
                                                    if (it.id == channelId) it.copy(keyRangeStart = start, keyRangeEnd = end) else it
                                                }
                                                updateChannelsAndPatchSnapshot(updatedChannels)
                                            }
                                        )
                                    }
                                }
                                SettingsTab.AUDIO -> {
                                    AudioSettingsTabScreen(
                                        sampleRate = selectedSampleRate,
                                        onSampleRateChange = { pendingSampleRate = it },
                                        audioDevices = currentAudioDevices,
                                        onSelectDevice = { synth.selectAudioDevice(it) },
                                        onRefreshDevices = { synth.refreshAudioDevices() },
                                        audioDiagnostics = audioDiagnostics,
                                        isRestartingAudio = isRestartingAudio
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pad off logic tied to keys has been removed per user request
        }
    }
}

// --- SUB-SCREENS AND TAB RENDERING ---



















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
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            if (isPressed)
                                Brush.verticalGradient(listOf(Color.White, AccentSky.copy(alpha = 0.9f)))
                            else
                                Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F4F8)))
                        )
                        .border(1.dp, Color(0xFF12141A), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
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
                            .fillMaxHeight(0.62f)
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                if (isPressed)
                                    Brush.verticalGradient(listOf(AccentSky, AccentSky.copy(alpha = 0.8f)))
                                else
                                    Brush.verticalGradient(listOf(Color(0xFF282C37), Color(0xFF151820)))
                            )
                            .border(1.dp, Color(0xFF0D0F14), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
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
    mappings: Map<Int, MidiTarget>,
    mappingTarget: MidiTarget?,
    connectedDevices: List<String> = emptyList(),
    onStartMapping: (MidiTarget) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (connectedDevices.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dispositivos MIDI conectados: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(connectedDevices.joinToString(", "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dispositivos MIDI conectados: ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                Text("Ninguno", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }

        Text("ASIGNACION DE CONTROLADORES MIDI CC (MIDI LEARN)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Haz clic en \"Mapear\" al lado del control correspondiente y mueve el potenciÃƒÆ’Ã‚Â³metro o fader de tu teclado fÃƒÆ’Ã‚Â­sico para enlazarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val controllers = listOf(
            MidiTarget.MasterVolume,
            MidiTarget.FilterCutoff,
            MidiTarget.ReverbMix,
            MidiTarget.Sustain,
            MidiTarget.Modulation,
            MidiTarget.OctaveUp,
            MidiTarget.OctaveDown
        ) + (0 until 8).map { MidiTarget.ChannelVolume(it) }

        controllers.forEach { target ->
            val mappedCc = mappings.entries.find { it.value == target }?.key
            
            val targetName = when (target) {
                is MidiTarget.ChannelVolume -> "Volumen Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelMute -> "Mute Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelSolo -> "Solo Canal ${target.channelIndex + 1}"
                is MidiTarget.Pad -> "Pad ${target.padIndex + 1}"
                is MidiTarget.Pot -> "Perilla ${target.potIndex + 1}"
                is MidiTarget.MasterVolume -> "Volumen Maestro"
                is MidiTarget.FilterCutoff -> "Filtro (Cutoff)"
                is MidiTarget.ReverbMix -> "Mezcla de Reverb"
                is MidiTarget.Sustain -> "Pedal Sustain"
                is MidiTarget.Modulation -> "Rueda de ModulaciÃƒÆ’Ã‚Â³n"
                is MidiTarget.OctaveUp -> "Octava Arriba"
                is MidiTarget.OctaveDown -> "Octava Abajo"
                is MidiTarget.NextPatch -> "Siguiente Patch"
                is MidiTarget.PreviousPatch -> "Patch Anterior"
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, AppShapes.small)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(targetName.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (mappedCc != null) "Mapeado a MIDI CC $mappedCc" else "Sin mapear",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (mappedCc != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Button(
                    onClick = { onStartMapping(target) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (mappingTarget == target) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (mappingTarget == target) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = AppShapes.small,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = if (mappingTarget == target) "ESCUCHANDO..." else "MAPEAR",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private val splitWhitePianoNotes = listOf(
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

private val splitBlackPianoNotesMap = mapOf(
    21 to 22, 24 to 25, 26 to 27, 29 to 30, 31 to 32, 33 to 34, 36 to 37, 38 to 39,
    41 to 42, 43 to 44, 45 to 46, 48 to 49, 50 to 51, 53 to 54, 55 to 56, 57 to 58,
    60 to 61, 62 to 63, 65 to 66, 67 to 68, 69 to 70, 72 to 73, 74 to 75, 77 to 78,
    79 to 80, 81 to 82, 84 to 85, 86 to 87, 89 to 90, 91 to 92, 93 to 94, 96 to 97,
    98 to 99, 101 to 102, 103 to 104, 105 to 106
)

private fun getSplitMidiNoteFractionStart(note: Int): Float {
    if (note <= 21) return 0f
    if (note >= 108) return 1f
    val whiteIdx = splitWhitePianoNotes.indexOf(note)
    return if (whiteIdx >= 0) {
        whiteIdx / 52f
    } else {
        val prevWhite = splitWhitePianoNotes.filter { it < note }.maxOrNull() ?: 21
        val prevIdx = splitWhitePianoNotes.indexOf(prevWhite)
        (prevIdx + 0.45f) / 52f
    }
}

private fun getSplitMidiNoteFractionEnd(note: Int): Float {
    if (note <= 21) return 1f / 52f
    if (note >= 108) return 1f
    val whiteIdx = splitWhitePianoNotes.indexOf(note)
    return if (whiteIdx >= 0) {
        (whiteIdx + 1) / 52f
    } else {
        val prevWhite = splitWhitePianoNotes.filter { it < note }.maxOrNull() ?: 21
        val prevIdx = splitWhitePianoNotes.indexOf(prevWhite)
        (prevIdx + 1.45f) / 52f
    }
}

@Composable
fun SplitKeyboardVisualizer(
    channels: List<ChannelStripState>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F1117))
            .border(1.dp, Color(0xFF232733), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        val totalWidth = maxWidth
        val barHeight = when {
            channels.size <= 2 -> 6.dp
            channels.size <= 4 -> 4.5.dp
            else -> 3.5.dp
        }
        val barSpacing = when {
            channels.size <= 3 -> 2.dp
            else -> 1.5.dp
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. Stacked Channel Layer Bars (MainStage style)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(barSpacing)
            ) {
                channels.forEach { ch ->
                    val accentColor = parseColorHex(ch.colorHex)
                    val alpha = if (ch.isMuted) 0.3f else 0.95f
                    val startFrac = getSplitMidiNoteFractionStart(ch.keyRangeStart)
                    val endFrac = getSplitMidiNoteFractionEnd(ch.keyRangeEnd)
                    val startX = totalWidth * startFrac
                    val barWidth = (totalWidth * (endFrac - startFrac)).coerceAtLeast(6.dp)

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
                                .border(0.5.dp, Color.White.copy(alpha = if (ch.isMuted) 0.1f else 0.4f), RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            // 2. Mini 88-Key Piano Keyboard with Color Overlays for Overlaps
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1115))
                    .border(1.dp, Color(0xFF1E222D), RoundedCornerShape(6.dp))
            ) {
                // White Keys
                Row(modifier = Modifier.fillMaxSize()) {
                    splitWhitePianoNotes.forEach { _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFFE2E8F0))
                                .border(0.5.dp, Color(0xFF0F1115))
                        )
                    }
                }

                // Black Keys
                Row(modifier = Modifier.fillMaxSize()) {
                    splitWhitePianoNotes.forEachIndexed { idx, note ->
                        val hasBlack = splitBlackPianoNotesMap.containsKey(note) && idx < 51
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            if (hasBlack) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp)
                                        .zIndex(2f)
                                        .width(7.dp)
                                        .fillMaxHeight(0.62f)
                                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .background(Color(0xFF1E222D))
                                        .border(0.5.dp, Color.Black, RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                )
                            }
                        }
                    }
                }

                // Semi-transparent Channel Range Overlays (blends overlapping zones automatically)
                channels.forEach { ch ->
                    val accentColor = parseColorHex(ch.colorHex)
                    val alpha = if (ch.isMuted) 0.18f else 0.42f
                    val startFrac = getSplitMidiNoteFractionStart(ch.keyRangeStart)
                    val endFrac = getSplitMidiNoteFractionEnd(ch.keyRangeEnd)
                    val startX = totalWidth * startFrac
                    val barWidth = (totalWidth * (endFrac - startFrac)).coerceAtLeast(4.dp)

                    Box(
                        modifier = Modifier
                            .offset(x = startX)
                            .width(barWidth)
                            .fillMaxHeight()
                            .background(accentColor.copy(alpha = alpha))
                            .border(1.dp, accentColor.copy(alpha = if (ch.isMuted) 0.3f else 0.85f))
                    )
                }

                // Octave C Labels at Bottom
                val cNotes = listOf(24 to "C1", 36 to "C2", 48 to "C3", 60 to "C4", 72 to "C5", 84 to "C6", 96 to "C7", 108 to "C8")
                cNotes.forEach { (note, label) ->
                    val frac = getSplitMidiNoteFractionStart(note)
                    Text(
                        text = label,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = totalWidth * frac + 1.dp, y = (-1).dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SplitKeyboardSettingsScreen(
    concert: Concert,
    selectedPatchName: String? = null,
    onUpdateRange: (Int, Int, Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("RANGOS DE TECLADO Y SPLITS (OCTAVAS A0 - C8)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (selectedPatchName != null) {
            Text(
                "Editando zonas del patch: $selectedPatchName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Text(
            "Visualiza y modifica las zonas de las teclas activas para cada archivo SF2 cargado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Mini Keyboard with MainStage-style Layer Bars and Overlap Tint
        SplitKeyboardVisualizer(
            channels = concert.channels,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        concert.channels.forEach { ch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, AppShapes.small)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ch.sf2Name.substringBefore(".sf2").uppercase(),
                    color = parseColorHex(ch.colorHex),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(100.dp)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nota Min: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = ch.keyRangeStart.toFloat(),
                        onValueChange = { onUpdateRange(ch.id, it.toInt(), ch.keyRangeEnd) },
                        valueRange = 0f..ch.keyRangeEnd.toFloat(),
                        modifier = Modifier.width(80.dp)
                    )
                    Text(ch.keyRangeStart.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(30.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Nota Max: ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = ch.keyRangeEnd.toFloat(),
                        onValueChange = { onUpdateRange(ch.id, ch.keyRangeStart, it.toInt()) },
                        valueRange = ch.keyRangeStart.toFloat()..127f,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(ch.keyRangeEnd.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(30.dp))
                }
            }
        }
    }
}

@Composable
fun AudioSettingsTabScreen(
    sampleRate: Int,
    onSampleRateChange: (Int) -> Unit,
    audioDevices: List<AudioOutputDeviceInfo>,
    onSelectDevice: (Int) -> Unit,
    onRefreshDevices: () -> Unit,
    audioDiagnostics: String,
    isRestartingAudio: Boolean
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("INTERFACES DE AUDIO Y LATENCIA", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = onRefreshDevices,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = AppShapes.small,
                modifier = Modifier.height(32.dp)
            ) {
                Text("Actualizar dispositivos", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Text("DISPOSITIVO DE SALIDA:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        if (audioDevices.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text("Buscando dispositivos...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                audioDevices.forEach { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(if (device.isCurrentlySelected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant, AppShapes.small)
                            .border(1.dp, if (device.isCurrentlySelected) MaterialTheme.colorScheme.secondary else Color.Transparent, AppShapes.small)
                            .clickable { onSelectDevice(device.id) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium, color = if (device.isCurrentlySelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(device.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (device.isCurrentlySelected) {
                            Text("Activo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("VELOCIDAD DE MUESTREO (BITRATE/SAMPLE RATE):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        .clip(AppShapes.small)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, AppShapes.small)
                        .clickable { onSampleRateChange(rate) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$rate Hz", style = MaterialTheme.typography.labelMedium, color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, AppShapes.small).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESTADO DEL DRIVER:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (isRestartingAudio) "REINICIANDO..." else audioDiagnostics,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isRestartingAudio) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

fun parseColorHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    return when (clean.length) {
        6 -> {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Color(r, g, b)
        }
        8 -> {
            val a = clean.substring(0, 2).toInt(16)
            val r = clean.substring(2, 4).toInt(16)
            val g = clean.substring(4, 6).toInt(16)
            val b = clean.substring(6, 8).toInt(16)
            Color(r, g, b, a)
        }
        else -> Color.White
    }
}





