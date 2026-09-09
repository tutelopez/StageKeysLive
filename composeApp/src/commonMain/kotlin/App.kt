package com.midi.mainstage

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import compose.icons.TablerIcons
import compose.icons.tablericons.*
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
enum class SettingsTab { MIDI_MAP, SPLIT_ZONES, AUDIO, SF2_FOLDER, MASTER_FX, BACKUP, SUPPORT }

const val CURRENT_APP_VERSION_CODE = 1
const val CURRENT_APP_VERSION_NAME = "1.0"

data class RecordingEvent(
    val deltaMs: Long,
    val note: Int,
    val velocity: Int,
    val isNoteOn: Boolean
)

@Composable
fun App(synth: PlatformAudioSynth = remember { PlatformAudioSynth() }) {
    val coroutineScope = rememberCoroutineScope()
    val synthThrottler = rememberEngineCcThrottler()

    // Navigation and Concert State
    var currentScreen by remember { mutableStateOf(ScreenState.DASHBOARD) }
    var concerts by remember { mutableStateOf<List<Concert>>(emptyList()) }
    var activeConcert by remember { mutableStateOf<Concert?>(null) }
    
    // Master FX Configuration State
    var masterFxSettings by remember { mutableStateOf(MasterFxSettings()) }
    LaunchedEffect(Unit) {
        val json = readTextFromFile("master_fx_settings.json")
        val loaded = MasterFxSerializer.deserialize(json)
        masterFxSettings = loaded
        synth.setMasterReverbParams(loaded.reverbRoomSize, loaded.reverbDamping, loaded.reverbWidth, loaded.reverbLevel)
        synth.setMasterChorusParams(loaded.chorusNr, loaded.chorusLevel, loaded.chorusSpeed, loaded.chorusDepth)
    }

    val updateMasterFxSettings: (MasterFxSettings) -> Unit = { newSettings ->
        masterFxSettings = newSettings
        saveTextToFile("master_fx_settings.json", MasterFxSerializer.serialize(newSettings))
        synth.setMasterReverbParams(newSettings.reverbRoomSize, newSettings.reverbDamping, newSettings.reverbWidth, newSettings.reverbLevel)
        synth.setMasterChorusParams(newSettings.chorusNr, newSettings.chorusLevel, newSettings.chorusSpeed, newSettings.chorusDepth)
    }
    
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
    var showWhatsNewDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val lastSeen = readTextFromFile("last_seen_version.txt")?.trim()?.toIntOrNull() ?: 0
        if (CURRENT_APP_VERSION_CODE > lastSeen) {
            showWhatsNewDialog = true
        }
    }
    
    val autoBackupController = rememberAutoBackupController()
    val googleDriveService = rememberGoogleDriveService()
    val sf2ExplorerController = rememberSf2ExplorerController()
    var showSf2ExplorerDialog by remember { mutableStateOf(false) }
    var autoBackupJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var cloudBackupJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val scheduleAutoBackup: (List<Concert>) -> Unit = { list ->
        if (autoBackupController.state.isConfigured) {
            autoBackupJob?.cancel()
            autoBackupJob = coroutineScope.launch {
                delay(30_000L) // 30-second debounce
                val result = autoBackupController.backupToFolder(list)
                result.onFailure { error ->
                    snackbarHostState.showSnackbar(
                        "El respaldo automático falló: ${error.message ?: "Permiso revocado"}. Por favor reconfigura la carpeta."
                    )
                }
            }
        }
        if (googleDriveService.state.isSignedIn) {
            cloudBackupJob?.cancel()
            cloudBackupJob = coroutineScope.launch {
                delay(30_000L) // 30-second debounce
                googleDriveService.backupNow(list)
            }
        }
    }

    // Periodic Cloud Backup (every 30 mins) if user is signed in
    LaunchedEffect(googleDriveService.state.isSignedIn) {
        if (googleDriveService.state.isSignedIn) {
            while (isActive) {
                delay(30 * 60 * 1000L) // 30 minutes
                if (concerts.isNotEmpty()) {
                    googleDriveService.backupNow(concerts)
                }
            }
        }
    }

    val saveConcertsList: (List<Concert>) -> Unit = { list ->
        concerts = list
        saveTextToFile("concerts.json", ConcertSerializer.serialize(list))
        scheduleAutoBackup(list)
    }

    // Import / Export states
    var showPackagePicker by remember { mutableStateOf(false) }
    var concertToExport by remember { mutableStateOf<Concert?>(null) }
    var patchToExport by remember { mutableStateOf<PatchState?>(null) }
    var midiEventsToExport by remember { mutableStateOf<List<RecordingEvent>?>(null) }

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

    MidiFileExporter(
        eventsToExport = midiEventsToExport,
        onExportComplete = {
            midiEventsToExport = null
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

    // Master Output volume, pan, limiter and Level meters
    var masterVolume by remember { mutableStateOf(0.8f) }
    var masterPan by remember { mutableStateOf(0.5f) }
    var masterLimiterEnabled by remember { mutableStateOf(true) }
    var isMasterLimiterActive by remember { mutableStateOf(false) }
    val masterVuLevel = remember { Animatable(0f) }

    LaunchedEffect(masterLimiterEnabled) {
        synth.setMasterLimiterEnabled(masterLimiterEnabled)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60)
            if (masterLimiterEnabled && synth.isMasterLimiterActive()) {
                isMasterLimiterActive = true
                delay(120)
                isMasterLimiterActive = false
            }
        }
    }

    // Audio Interfaces & Settings state
    var selectedSampleRate by remember { mutableStateOf(48000) }
    var pendingSampleRate by remember { mutableStateOf<Int?>(null) }
    var selectedBufferSizeOption by remember { mutableStateOf(0) } // 0: Auto, 1: Low (128), 2: High (512)
    var pendingBufferSizeOption by remember { mutableStateOf<Int?>(null) }
    var isRestartingAudio by remember { mutableStateOf(false) }
    var selectedAudioOutput by remember { mutableStateOf("Salida Estéreo Principal (System Default)") }

    // Live performance controls state
    var activeNote by remember { mutableStateOf<Int?>(null) }
    val pitchBend = remember { Animatable(0f) }
    var sustainActive by remember { mutableStateOf(false) }

    // Key states tracker for sustain mapping
    val heldKeys = remember { mutableStateMapOf<Int, Boolean>() }
    val sustainedKeys = remember { mutableStateMapOf<Int, Boolean>() }

    // VU meter level animations per channel strip (up to 16 channels)
    val vuLevels = remember { List(16) { Animatable(0f) } }

    DisposableEffect(synth) {
        synth.onBenchmarkVuUpdate = { ch, level ->
            if (ch in vuLevels.indices) {
                coroutineScope.launch {
                    vuLevels[ch].animateTo(level, tween(50))
                }
            }
        }
        synth.onBenchmarkStarted = { chCount ->
            coroutineScope.launch {
                val dummyChannels = (1..chCount).map { id ->
                    ChannelStripState(
                        id = id,
                        name = "Canal $id",
                        sf2Name = "PianoDefault.sf2",
                        sf2Path = null,
                        volume = 0.85f,
                        isMuted = false,
                        isSoloed = false,
                        keyRangeStart = 0,
                        keyRangeEnd = 127,
                        colorHex = when (id % 4) {
                            1 -> "#00D2FF"
                            2 -> "#FFFF8C00"
                            3 -> "#FF39FF14"
                            else -> "#FFFF0055"
                        }
                    )
                }
                activeConcert = Concert(
                    id = "bench_concert",
                    name = "Benchmark Concert ($chCount Ch)",
                    lastModified = System.currentTimeMillis(),
                    patches = listOf(PatchState("Bench Patch", "Benchmark", 0, "Bench")),
                    channels = dummyChannels
                )
                currentScreen = ScreenState.CONCERT
            }
        }
        onDispose {
            synth.onBenchmarkVuUpdate = null
            synth.onBenchmarkStarted = null
        }
    }

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
                val effectivePan = ((ch.pan - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
                synth.setPan(ch.id, effectivePan)
            }
            synth.padSetPan(masterPan)
        }
    }

    val saveActiveSession: (Boolean) -> Unit = { isSessionActive ->
        try {
            val concert = activeConcert
            if (concert != null && isSessionActive) {
                val snapshot = ActiveSessionSnapshot(
                    isSessionActive = true,
                    concertId = concert.id,
                    selectedPatchIndex = selectedPatchIndex,
                    timestamp = System.currentTimeMillis(),
                    masterVolume = masterVolume,
                    masterPan = masterPan,
                    channels = concert.channels
                )
                saveTextToFile("active_session.json", SessionSnapshotSerializer.serialize(snapshot))
            } else {
                val snapshot = ActiveSessionSnapshot(isSessionActive = false, timestamp = System.currentTimeMillis())
                saveTextToFile("active_session.json", SessionSnapshotSerializer.serialize(snapshot))
            }
        } catch (e: Throwable) {
            CrashReporter.recordException(e, "saveActiveSession")
        }
    }

    // Continuous autosave & instant update when active concert session state changes
    LaunchedEffect(currentScreen, activeConcert?.id, selectedPatchIndex, activeConcert?.channels, masterVolume, masterPan) {
        if (currentScreen == ScreenState.CONCERT && activeConcert != null) {
            saveActiveSession(true)
            while (isActive) {
                delay(12000L) // Periodic autosave every 12s
                saveActiveSession(true)
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
        println("[COLOR_DEBUG] updateChannelsAndPatchSnapshotOnlyState: newChannels=${newChannels.map { "Ch${it.id}:${it.colorHex}" }} (activeConcert was ${concert?.channels?.map { "Ch${it.id}:${it.colorHex}" }})")
        if (concert != null) {
            val updatedPatches = if (selectedPatchIndex in concert.patches.indices) {
                concert.patches.mapIndexed { idx, patch ->
                    if (idx == selectedPatchIndex) {
                        val newSnapshot = newChannels.map { ch ->
                            PatchChannelSnapshot(
                                channelId = ch.id, name = ch.name, sf2Name = ch.sf2Name, sf2Path = ch.sf2Path,
                                volume = ch.volume, isMuted = ch.isMuted, isSoloed = ch.isSoloed,
                                keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd,
                                colorHex = ch.colorHex,
                                velocityCurve = ch.velocityCurve,
                                pan = ch.pan
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
            println("[COLOR_DEBUG] activeConcert set to: ${updatedConcert.channels.map { "Ch${it.id}:${it.colorHex}" }}")
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
                    keyRangeStart = ch.keyRangeStart, keyRangeEnd = ch.keyRangeEnd, colorHex = ch.colorHex,
                    velocityCurve = ch.velocityCurve,
                    pan = ch.pan,
                    reverbSend = ch.reverbSend,
                    chorusSend = ch.chorusSend,
                    filterCutoff = ch.filterCutoff
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
                        colorHex = snap.colorHex,
                        velocityCurve = snap.velocityCurve,
                        pan = snap.pan,
                        reverbSend = snap.reverbSend,
                        chorusSend = snap.chorusSend,
                        filterCutoff = snap.filterCutoff
                    )
                }
            } else {
                // Patch has no snapshot yet start with a single clean channel
                listOf(
                    ChannelStripState(
                        id = 1, name = "Canal 1", sf2Name = "Sin Asignar", sf2Path = null,
                        volume = 0.8f, isMuted = false, isSoloed = false,
                        keyRangeStart = 0, keyRangeEnd = 127, colorHex = "#00D2FF",
                        velocityCurve = "LINEAR",
                        pan = 0.5f,
                        reverbSend = 0.2f,
                        chorusSend = 0.0f,
                        filterCutoff = 1.0f
                    )
                )
            }

            // Load SoundFonts, apply effective pan, and apply FX sends for the restored channels
            restoredChannels.forEach { ch ->
                if (ch.sf2Path != null) {
                    synth.loadSoundFont(ch.sf2Path, ch.id)
                }
                val effectivePan = ((ch.pan - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
                synth.setPan(ch.id, effectivePan)
                synth.setChannelReverbSend(ch.id, ch.reverbSend)
                synth.setChannelChorusSend(ch.id, ch.chorusSend)
                synth.setFilterCutoff(ch.filterCutoff, ch.id)
            }
            synth.padSetPan(masterPan)

            val updatedConcert = concert.copy(
                channels = restoredChannels,
                patches = patchesWithCurrentSaved,
                lastModified = System.currentTimeMillis()
            )
            saveConcertsList(concerts.map { if (it.id == concert.id) updatedConcert else it })
            activeConcert = updatedConcert
            selectedPatchIndex = patchIndex
            saveActiveSession(true)
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
                    val curvedVelocity = applyCurve(velocity, ch.velocityCurve)
                    val effectiveVelocity = (ch.volume * curvedVelocity).toInt().coerceIn(0, 127)
                    println("[VELOCITY_DEBUG] Note=$playedNote rawVel=$velocity curve=${ch.velocityCurve} -> curvedVel=$curvedVelocity effectiveVel=$effectiveVelocity (vol=${ch.volume})")
                    synth.noteOn(playedNote, effectiveVelocity, ch.id)
                    noteTriggered = true
                    
                    coroutineScope.launch {
                        vuLevels[idx].animateTo(ch.volume * (curvedVelocity / 127f), tween(50))
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
                println("[REC_DEBUG] Recorded NoteOn: note=$note vel=$velocity elapsed=$elapsed. Total=${recordedEvents.size}")
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
                    is MidiTarget.ChannelReverb -> {
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(reverbSend = floatValue)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                synthThrottler.sendThrottled(coroutineScope, floatValue) { v ->
                                    synth.setChannelReverbSend(ch.id, v)
                                }
                            }
                        }
                    }
                    is MidiTarget.ChannelChorus -> {
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(chorusSend = floatValue)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                synthThrottler.sendThrottled(coroutineScope, floatValue) { v ->
                                    synth.setChannelChorusSend(ch.id, v)
                                }
                            }
                        }
                    }
                    is MidiTarget.ChannelCutoff -> {
                        activeConcert?.let { concert ->
                            if (target.channelIndex < concert.channels.size) {
                                val updatedChannels = concert.channels.toMutableList()
                                val ch = updatedChannels[target.channelIndex]
                                updatedChannels[target.channelIndex] = ch.copy(filterCutoff = floatValue)
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                synthThrottler.sendThrottled(coroutineScope, floatValue) { v ->
                                    synth.setFilterCutoff(v, ch.id)
                                }
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
                    is MidiTarget.PadNoteToggle -> {
                        if (floatValue > 0f) {
                            if (!padEnabled) {
                                padEnabled = true
                            }
                            if (activePadNote == target.pitchClass) {
                                synth.padNoteOff()
                                activePadNote = null
                            } else {
                                synth.padNoteOn(target.pitchClass)
                                activePadNote = target.pitchClass
                            }
                        }
                    }
                    is MidiTarget.PadEnable -> {
                        if (floatValue > 0f) {
                            padEnabled = !padEnabled
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
                    is MidiTarget.SelectPatch -> {
                        if (floatValue > 0f) {
                            applyPatch(target.patchIndex)
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
            },
            onProgramChange = { program ->
                triggerMidiFlash()
                val mappedTarget = midiCcMappings[program]
                if (mappedTarget is MidiTarget.SelectPatch) {
                    applyPatch(mappedTarget.patchIndex)
                } else {
                    val concert = activeConcert
                    if (concert != null && program in concert.patches.indices) {
                        applyPatch(program)
                    }
                }
            },
            onMidiActivity = {
                triggerMidiFlash()
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
        val loadedConcerts = if (json != null) {
            try {
                ConcertSerializer.deserialize(json)
            } catch (e: Throwable) {
                CrashReporter.recordException(e, "AppStartup.loadConcerts")
                emptyList()
            }
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
            saveTextToFile("concerts.json", ConcertSerializer.serialize(defaultConcerts))
            defaultConcerts
        }
        concerts = loadedConcerts

        // Check for interrupted active session to automatically recover
        try {
            val sessionJson = readTextFromFile("active_session.json")
            val session = SessionSnapshotSerializer.deserialize(sessionJson)
            if (session != null && session.isSessionActive && session.concertId.isNotBlank()) {
                val matchedConcert = loadedConcerts.find { it.id == session.concertId }
                if (matchedConcert != null) {
                    masterVolume = session.masterVolume
                    masterPan = session.masterPan
                    val restoredChannels = if (session.channels.isNotEmpty()) session.channels else matchedConcert.channels
                    val restoredConcert = matchedConcert.copy(channels = restoredChannels)
                    activeConcert = restoredConcert
                    val safePatchIdx = session.selectedPatchIndex.coerceIn(0, (restoredConcert.patches.size - 1).coerceAtLeast(0))
                    selectedPatchIndex = safePatchIdx
                    currentScreen = ScreenState.CONCERT

                    // Configure SoundFonts & FX for restored channels
                    restoredChannels.forEach { ch ->
                        if (ch.sf2Path != null) {
                            synth.loadSoundFont(ch.sf2Path, ch.id)
                        }
                        val effectivePan = ((ch.pan - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
                        synth.setPan(ch.id, effectivePan)
                        synth.setChannelReverbSend(ch.id, ch.reverbSend)
                        synth.setChannelChorusSend(ch.id, ch.chorusSend)
                        synth.setFilterCutoff(ch.filterCutoff, ch.id)
                    }
                    synth.padSetPan(masterPan)

                    CrashReporter.log("Live session recovered: Concert '${restoredConcert.name}' (ID=${session.concertId}), Patch index=$safePatchIdx")
                    coroutineScope.launch {
                        delay(600)
                        snackbarHostState.showSnackbar("Sesión en vivo restaurada automáticamente")
                    }
                }
            }
        } catch (e: Throwable) {
            CrashReporter.recordException(e, "AppStartup.sessionRecovery")
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

    // Keep Master Pan & Channel Effective Pan updated on Audio Synth and Pad Engine
    LaunchedEffect(masterPan, activeConcert?.channels) {
        activeConcert?.channels?.forEach { ch ->
            val effectivePan = ((ch.pan - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
            synth.setPan(ch.id, effectivePan)
        }
        synth.padSetPan(masterPan)
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
                },
                userProfile = googleDriveService.state.user
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
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedPatches = active.patches.filter { it.id != patch.id }
                        val updatedConcert = active.copy(patches = updatedPatches, lastModified = System.currentTimeMillis())
                        val newList = concerts.map { if (it.id == active.id) updatedConcert else it }
                        saveConcertsList(newList)
                        activeConcert = updatedConcert
                        if (selectedPatchIndex >= updatedPatches.size) {
                            selectedPatchIndex = (updatedPatches.size - 1).coerceAtLeast(0)
                        }
                    },
                    onExportPatchClick = { patchToExport = it },
                    onImportPatchClick = { showPackagePicker = true },
                    onToggleFavorite = { patch ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedPatches = active.patches.map {
                            if (it.id == patch.id) it.copy(isFavorite = !it.isFavorite) else it
                        }
                        val updatedConcert = active.copy(patches = updatedPatches, lastModified = System.currentTimeMillis())
                        saveConcertsList(concerts.map { if (it.id == active.id) updatedConcert else it })
                        activeConcert = updatedConcert
                    },
                    onMovePatch = { fromIdx, toIdx ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        if (fromIdx in active.patches.indices && toIdx in active.patches.indices && fromIdx != toIdx) {
                            val mutable = active.patches.toMutableList()
                            val item = mutable.removeAt(fromIdx)
                            mutable.add(toIdx, item)
                            val updatedConcert = active.copy(patches = mutable, lastModified = System.currentTimeMillis())
                            saveConcertsList(concerts.map { if (it.id == active.id) updatedConcert else it })
                            activeConcert = updatedConcert
                            if (selectedPatchIndex == fromIdx) {
                                selectedPatchIndex = toIdx
                            } else if (fromIdx < toIdx && selectedPatchIndex in (fromIdx + 1)..toIdx) {
                                selectedPatchIndex--
                            } else if (fromIdx > toIdx && selectedPatchIndex in toIdx until fromIdx) {
                                selectedPatchIndex++
                            }
                        }
                    },
                    onBackClick = { 
                        stopConcert()
                        saveActiveSession(false)
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
                            println("[REC_DEBUG] Stopped recording. Total events: ${recordedEvents.size}")
                        } else {
                            recordedEvents.clear()
                            recordingStartTimestamp = System.currentTimeMillis()
                            isRecording = true
                            println("[REC_DEBUG] Started recording.")
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
                    hasRecording = recordedEvents.isNotEmpty(),
                    onExportMidiClick = {
                        println("[REC_DEBUG] onExportMidiClick: recordedEvents=${recordedEvents.size}")
                        if (recordedEvents.isNotEmpty()) {
                            midiEventsToExport = recordedEvents.toList()
                        }
                    },

                    // Channel strip callback triggers dialog and additions
                    onVolumeChange = { chId, vol ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        println("[COLOR_DEBUG] onVolumeChange: chId=$chId, vol=$vol. Current activeConcert colors: ${active.channels.map { "Ch${it.id}:${it.colorHex}" }}")
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(volume = vol) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onMuteToggle = { chId ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(isMuted = !it.isMuted) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onSoloToggle = { chId ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(isSoloed = !it.isSoloed) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                    },
                    onAddChannelClick = {
                        val active = activeConcert ?: return@ConcertViewScreen
                        if (active.channels.size < 8) {
                            val nextId = (active.channels.maxOfOrNull { it.id } ?: 0) + 1
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
                            val updatedChannels = active.channels + newChannel
                            updateChannelsAndPatchSnapshot(updatedChannels)
                        }
                    },
                    onChannelGearClick = { showChannelSettingsDialog = it },
                    onReverbChange = { chId, value ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(reverbSend = value) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                        synthThrottler.sendThrottled(coroutineScope, value) { v ->
                            synth.setChannelReverbSend(chId, v)
                        }
                    },
                    onChorusChange = { chId, value ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(chorusSend = value) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                        synthThrottler.sendThrottled(coroutineScope, value) { v ->
                            synth.setChannelChorusSend(chId, v)
                        }
                    },
                    onCutoffChange = { chId, value ->
                        val active = activeConcert ?: return@ConcertViewScreen
                        val updatedChannels = active.channels.map {
                            if (it.id == chId) it.copy(filterCutoff = value) else it
                        }
                        updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                        synthThrottler.sendThrottled(coroutineScope, value) { v ->
                            synth.setFilterCutoff(v, chId)
                        }
                    },
                    midiMappings = midiCcMappings,

                    // Master output configuration mapping
                    masterVolume = masterVolume,
                    masterPan = masterPan,
                    isMasterLimiterActive = isMasterLimiterActive,
                    onMasterVolumeChange = { masterVolume = it },
                    onMasterPanChange = { masterPan = it },
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
        
        val handleSoundFontSelected: (path: String, displayName: String) -> Unit = { path, displayName ->
            val channel = showChannelSettingsDialog
            val active = activeConcert
            if (channel != null && active != null) {
                isLoadingSf2 = true
                coroutineScope.launch {
                    val success = synth.loadSoundFont(path, channel.id)
                    if (success) {
                        val sf2Name = displayName.ifBlank { path.substringAfterLast("/") }
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

        Sf2FilePicker(showSf2Picker) { path, displayName ->
            showSf2Picker = false
            if (path != null) {
                handleSoundFontSelected(path, displayName ?: path.substringAfterLast("/"))
            }
        }

        Sf2ExplorerDialog(
            show = showSf2ExplorerDialog,
            channelName = showChannelSettingsDialog?.name ?: "Canal",
            synth = synth,
            controller = sf2ExplorerController,
            onDismiss = { showSf2ExplorerDialog = false },
            onSelectSoundFont = { path, displayName ->
                handleSoundFontSelected(path, displayName)
            }
        )
    }

    // --- DIALOGS ---

    // 1. Create Concert Dialog
    // Audio Engine Restart Dialog (Sample Rate)
    if (pendingSampleRate != null) {
        val newRate = pendingSampleRate!!
        AlertDialog(
            onDismissRequest = { pendingSampleRate = null },
            title = { Text("Cambiar Sample Rate", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Cambiar la frecuencia de muestreo a $newRate Hz reiniciará brevemente el motor de audio. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingSampleRate = null
                    isRestartingAudio = true
                    selectedSampleRate = newRate
                    
                    // Controlled restart
                    synth.allNotesOff()
                    coroutineScope.launch(Dispatchers.Default) {
                        synth.initializeEngine(newRate, selectedBufferSizeOption)
                        
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

    // Audio Engine Restart Dialog (Buffer Size)
    if (pendingBufferSizeOption != null) {
        val newBufOption = pendingBufferSizeOption!!
        val bufLabel = when (newBufOption) {
            1 -> "Bajo (128 frames - menor latencia)"
            2 -> "Alto (512 frames - mayor estabilidad)"
            else -> "Automático (Recomendado)"
        }
        AlertDialog(
            onDismissRequest = { pendingBufferSizeOption = null },
            title = { Text("Cambiar Tamaño de Buffer", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Cambiar el tamaño de buffer a \"$bufLabel\" reiniciará brevemente el motor de audio. ¿Deseas continuar?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingBufferSizeOption = null
                    isRestartingAudio = true
                    selectedBufferSizeOption = newBufOption
                    
                    // Controlled restart
                    synth.allNotesOff()
                    coroutineScope.launch(Dispatchers.Default) {
                        synth.initializeEngine(selectedSampleRate, newBufOption)
                        
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
                TextButton(onClick = { pendingBufferSizeOption = null }) {
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

    if (showWhatsNewDialog) {
        WhatsNewDialog(
            versionName = CURRENT_APP_VERSION_NAME,
            notes = CURRENT_RELEASE_NOTES,
            onDismiss = {
                saveTextToFile("last_seen_version.txt", CURRENT_APP_VERSION_CODE.toString())
                showWhatsNewDialog = false
            }
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
                        // Options List: Explorador SF2 (Carpeta) or Importar Archivo
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    showSf2ExplorerDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(TablerIcons.Folders, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Explorar SF2", color = Color.Black, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    showSf2Picker = true
                                },
                                shape = AppShapes.medium,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(TablerIcons.FileImport, contentDescription = null, tint = TextLight, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importar .sf2", color = TextLight, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
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

                    // Panorama (Pan) Slider: 0 = Izquierda, 0.5 = Centro, 1 = Derecha
                    val panPercent = ((chState.pan - 0.5f) * 200).toInt()
                    val panLabel = when {
                        panPercent == 0 -> "Centro (0)"
                        panPercent < 0 -> "L ${-panPercent}%"
                        else -> "R ${panPercent}%"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "PANORAMA (PAN):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkBackground)
                                .clickable {
                                    // Reset to center 0.5f on click
                                    val effectivePan = ((0.5f - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
                                    synth.setPan(chState.id, effectivePan)
                                    val active = activeConcert
                                    if (active != null) {
                                        val updatedChannels = active.channels.map {
                                            if (it.id == chState.id) it.copy(pan = 0.5f) else it
                                        }
                                        updateChannelsAndPatchSnapshot(updatedChannels)
                                        showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = panLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = parseColorHex(chState.colorHex)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("L", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.padding(end = 4.dp))
                        Slider(
                            value = chState.pan,
                            onValueChange = { newPan ->
                                val effectivePan = ((newPan - 0.5f) + (masterPan - 0.5f) + 0.5f).coerceIn(0f, 1f)
                                synth.setPan(chState.id, effectivePan)
                                val active = activeConcert
                                if (active != null) {
                                    val updatedChannels = active.channels.map {
                                        if (it.id == chState.id) it.copy(pan = newPan) else it
                                    }
                                    updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                    showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                                }
                            },
                            onValueChangeFinished = {
                                val active = activeConcert
                                if (active != null) {
                                    saveConcertsList(concerts.map { if (it.id == active.id) active else it })
                                }
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = parseColorHex(chState.colorHex),
                                activeTrackColor = parseColorHex(chState.colorHex),
                                inactiveTrackColor = DarkBackground
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Text("R", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark, modifier = Modifier.padding(start = 4.dp))
                    }

                    // Velocity Curve Selector (Suave / Normal / Dura)
                    Text("CURVA DE SENSIBILIDAD (VELOCITY):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))

                    val curves = listOf(
                        "SOFT" to "Suave",
                        "LINEAR" to "Normal",
                        "HARD" to "Dura"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        curves.forEach { (curveKey, label) ->
                            val isSelected = (chState.velocityCurve == curveKey) || (curveKey == "LINEAR" && chState.velocityCurve.isBlank())
                            val accentColor = parseColorHex(chState.colorHex)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) accentColor.copy(alpha = 0.22f) else DarkBackground)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) accentColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        val active = activeConcert
                                        if (active != null) {
                                            val updatedChannels = active.channels.map {
                                                if (it.id == chState.id) it.copy(velocityCurve = curveKey) else it
                                            }
                                            updateChannelsAndPatchSnapshot(updatedChannels)
                                            showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TextDark,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    // FX Sends Section (Reverb Send, Chorus Send, Filter Cutoff)
                    Text("EFECTOS DE CANAL (FX SENDS):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 6.dp))

                    // Reverb Send Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reverb Send (Ambiente)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("${(chState.reverbSend * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Slider(
                        value = chState.reverbSend,
                        onValueChange = { newVal ->
                            synthThrottler.sendThrottled(coroutineScope, newVal) { v ->
                                synth.setChannelReverbSend(chState.id, v)
                            }
                            val active = activeConcert
                            if (active != null) {
                                val updatedChannels = active.channels.map {
                                    if (it.id == chState.id) it.copy(reverbSend = newVal) else it
                                }
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA855F7),
                            activeTrackColor = Color(0xFFA855F7),
                            inactiveTrackColor = DarkBackground
                        ),
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )

                    // Chorus Send Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Chorus Send (Cuerpo / Modulación)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2DD4BF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("${(chState.chorusSend * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2DD4BF), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Slider(
                        value = chState.chorusSend,
                        onValueChange = { newVal ->
                            synthThrottler.sendThrottled(coroutineScope, newVal) { v ->
                                synth.setChannelChorusSend(chState.id, v)
                            }
                            val active = activeConcert
                            if (active != null) {
                                val updatedChannels = active.channels.map {
                                    if (it.id == chState.id) it.copy(chorusSend = newVal) else it
                                }
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF2DD4BF),
                            activeTrackColor = Color(0xFF2DD4BF),
                            inactiveTrackColor = DarkBackground
                        ),
                        modifier = Modifier.fillMaxWidth().height(26.dp)
                    )

                    // Filter Cutoff Slider
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tone / Cutoff (Brillo)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFB7185), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("${(chState.filterCutoff * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFB7185), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Slider(
                        value = chState.filterCutoff,
                        onValueChange = { newVal ->
                            synthThrottler.sendThrottled(coroutineScope, newVal) { v ->
                                synth.setFilterCutoff(v, chState.id)
                            }
                            val active = activeConcert
                            if (active != null) {
                                val updatedChannels = active.channels.map {
                                    if (it.id == chState.id) it.copy(filterCutoff = newVal) else it
                                }
                                updateChannelsAndPatchSnapshotOnlyState(updatedChannels)
                                showChannelSettingsDialog = activeConcert?.channels?.find { it.id == chState.id }
                            }
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFB7185),
                            activeTrackColor = Color(0xFFFB7185),
                            inactiveTrackColor = DarkBackground
                        ),
                        modifier = Modifier.fillMaxWidth().height(26.dp).padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

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
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(DarkPanel, RoundedCornerShape(8.dp))
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp)
                        ) {
                            val tabs = if (settingsOpenedFromConcert) {
                                listOf(
                                    SettingsTab.MIDI_MAP to "Mapear MIDI",
                                    SettingsTab.SPLIT_ZONES to " Keyboard Zones",
                                    SettingsTab.AUDIO to "Interfaces de Audio",
                                    SettingsTab.SF2_FOLDER to "Carpeta SF2",
                                    SettingsTab.MASTER_FX to "Master FX",
                                    SettingsTab.BACKUP to "Respaldo Automático",
                                    SettingsTab.SUPPORT to "Soporte y Ayuda"
                                )
                            } else {
                                listOf(
                                    SettingsTab.MIDI_MAP to "Mapear MIDI",
                                    SettingsTab.AUDIO to "Interfaces de Audio",
                                    SettingsTab.SF2_FOLDER to "Carpeta SF2",
                                    SettingsTab.MASTER_FX to "Master FX",
                                    SettingsTab.BACKUP to "Respaldo Automático",
                                    SettingsTab.SUPPORT to "Soporte y Ayuda"
                                )
                            }
                            tabs.forEach { (tab, label) ->
                                val isSelected = activeSettingsTab == tab
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isSelected) AccentSky.copy(alpha = 0.18f) else Color.Transparent
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) AccentSky else Color.Transparent,
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { activeSettingsTab = tab }
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) TextLight else TextDark,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
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
                                        activeConcert = activeConcert,
                                        onStartMapping = { target ->
                                            mappingTarget = target
                                            synth.startMidiLearn(
                                                target = target,
                                                onCaptured = { cc ->
                                                    midiCcMappings[cc] = target
                                                    synth.syncMidiMappings(midiCcMappings)
                                                    mappingTarget = null
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
                                        bufferOption = selectedBufferSizeOption,
                                        onBufferOptionChange = { pendingBufferSizeOption = it },
                                        audioDevices = currentAudioDevices,
                                        onSelectDevice = { synth.selectAudioDevice(it) },
                                        onRefreshDevices = { synth.refreshAudioDevices() },
                                        audioDiagnostics = audioDiagnostics,
                                        isRestartingAudio = isRestartingAudio
                                    )
                                }
                                SettingsTab.SF2_FOLDER -> {
                                    Sf2FolderSettingsScreen(
                                        controller = sf2ExplorerController,
                                        synth = synth
                                    )
                                }
                                SettingsTab.MASTER_FX -> {
                                    MasterFxSettingsScreen(
                                        settings = masterFxSettings,
                                        onSettingsChange = updateMasterFxSettings,
                                        masterLimiterEnabled = masterLimiterEnabled,
                                        onMasterLimiterToggle = { masterLimiterEnabled = it }
                                    )
                                }
                                SettingsTab.BACKUP -> {
                                    AutoBackupSettingsScreen(
                                        controller = autoBackupController,
                                        googleDriveService = googleDriveService,
                                        concerts = concerts,
                                        onRestoreConcerts = { restoredList ->
                                            saveConcertsList(restoredList)
                                            activeConcert = restoredList.firstOrNull()
                                        },
                                        onShowSnackbar = { msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    )
                                }
                                SettingsTab.SUPPORT -> {
                                    SupportSettingsScreen(
                                        googleUserEmail = googleDriveService.state.user?.email,
                                        connectedMidiDevices = currentConnectedDevices,
                                        sampleRate = selectedSampleRate,
                                        bufferSizeOption = selectedBufferSizeOption,
                                        onShowSnackbar = { msg ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
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
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                onNoteDown(note)
                                waitForUpOrCancellation()
                                onNoteUp(note)
                            }
                        }
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
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    onNoteDown(blackNote)
                                    waitForUpOrCancellation()
                                    onNoteUp(blackNote)
                                }
                            }
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
    activeConcert: Concert? = null,
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

        Text("ASIGNACION DE CONTROLADORES MIDI CC Y PROGRAM CHANGE", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Haz clic en \"Mapear\" al lado del control correspondiente y mueve el potenciómetro/fader o presiona un footswitch/pad de tu controlador MIDI para enlazarlo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        val noteNames = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        val patchTargets = activeConcert?.patches?.indices?.map { MidiTarget.SelectPatch(it) } ?: emptyList()

        val controllers = listOf(
            MidiTarget.MasterVolume,
            MidiTarget.FilterCutoff,
            MidiTarget.ReverbMix,
            MidiTarget.Sustain,
            MidiTarget.Modulation,
            MidiTarget.OctaveUp,
            MidiTarget.OctaveDown,
            MidiTarget.NextPatch,
            MidiTarget.PreviousPatch,
            MidiTarget.PadEnable
        ) + patchTargets +
            (0 until 8).map { MidiTarget.ChannelVolume(it) } +
            (0 until 8).map { MidiTarget.ChannelReverb(it) } +
            (0 until 8).map { MidiTarget.ChannelChorus(it) } +
            (0 until 8).map { MidiTarget.ChannelCutoff(it) } +
            (0 until 12).map { MidiTarget.PadNoteToggle(it) }

        controllers.forEach { target ->
            val mappedCc = mappings.entries.find { it.value == target }?.key
            
            val targetName = when (target) {
                is MidiTarget.ChannelVolume -> "Volumen Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelMute -> "Mute Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelSolo -> "Solo Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelReverb -> "Reverb Send Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelChorus -> "Chorus Send Canal ${target.channelIndex + 1}"
                is MidiTarget.ChannelCutoff -> "Tone / Cutoff Canal ${target.channelIndex + 1}"
                is MidiTarget.Pad -> "Pad ${target.padIndex + 1}"
                is MidiTarget.Pot -> "Perilla ${target.potIndex + 1}"
                is MidiTarget.PadNoteToggle -> {
                    val noteName = noteNames.getOrElse(target.pitchClass) { "${target.pitchClass}" }
                    "Pad Nota: $noteName"
                }
                is MidiTarget.PadEnable -> "Pads Continuos (ON/OFF)"
                is MidiTarget.MasterVolume -> "Volumen Maestro"
                is MidiTarget.FilterCutoff -> "Filtro (Cutoff)"
                is MidiTarget.ReverbMix -> "Mezcla de Reverb"
                is MidiTarget.Sustain -> "Pedal Sustain"
                is MidiTarget.Modulation -> "Rueda de Modulación"
                is MidiTarget.OctaveUp -> "Octava Arriba"
                is MidiTarget.OctaveDown -> "Octava Abajo"
                is MidiTarget.SelectPatch -> {
                    val pName = activeConcert?.patches?.getOrNull(target.patchIndex)?.name
                    if (pName != null) "Patch ${target.patchIndex + 1}: $pName (PC ${target.patchIndex})"
                    else "Seleccionar Patch ${target.patchIndex + 1} (PC ${target.patchIndex})"
                }
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
                        text = if (mappedCc != null) {
                            if (target is MidiTarget.SelectPatch) "Mapeado a PC/CC $mappedCc" else "Mapeado a MIDI CC $mappedCc"
                        } else {
                            if (target is MidiTarget.SelectPatch) "Por defecto: Program Change ${target.patchIndex}" else "Sin mapear"
                        },
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
    bufferOption: Int,
    onBufferOptionChange: (Int) -> Unit,
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

        Text("VELOCIDAD DE MUESTREO (SAMPLE RATE):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

        Spacer(modifier = Modifier.height(12.dp))

        Text("TAMAÑO DE BUFFER (LATENCIA):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(
                0 to "Automático\n(recomendado)",
                1 to "Bajo\n(128 frames)",
                2 to "Alto\n(512 frames)"
            ).forEach { (opt, label) ->
                val isSelected = bufferOption == opt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(AppShapes.small)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, AppShapes.small)
                        .clickable { onBufferOptionChange(opt) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

@Composable
fun MasterFxSettingsScreen(
    settings: MasterFxSettings,
    onSettingsChange: (MasterFxSettings) -> Unit,
    masterLimiterEnabled: Boolean = true,
    onMasterLimiterToggle: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "CONFIGURACIÓN DE EFECTOS GLOBALES (MASTER FX)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentSky
        )
        Text(
            text = "Estos controles definen el comportamiento global de los procesadores de Reverb y Chorus de FluidSynth, y la protección de salida contra distorsión digital.",
            style = MaterialTheme.typography.bodySmall,
            color = TextDark
        )

        // 1. Master Bus Limiter (Anti-Clipping)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            shape = AppShapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                        Spacer(Modifier.width(8.dp))
                        Text("LIMITADOR MASTER (SOFT-CLIP)", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
                    }
                    Switch(
                        checked = masterLimiterEnabled,
                        onCheckedChange = onMasterLimiterToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFF9800),
                            checkedTrackColor = Color(0xFFFF9800).copy(alpha = 0.3f),
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E222D)
                        )
                    )
                }
                Text(
                    text = "Aplica saturación suave de codo blando (tanh soft-knee a partir de 0.85) en la mezcla final antes de enviar a los altavoces/auriculares para evitar el desagradable 'clipping' digital cuando suenan múltiples capas a alto volumen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLight,
                    fontSize = 12.sp
                )
            }
        }

        // 2. Reverb Section (Freeverb)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            shape = AppShapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFA855F7)))
                    Spacer(Modifier.width(8.dp))
                    Text("REVERB MASTER (FREEVERB)", style = MaterialTheme.typography.labelLarge, color = Color(0xFFA855F7), fontWeight = FontWeight.Bold)
                }

                // Room Size
                FxSliderRow(
                    label = "Room Size (Tamaño de Sala)",
                    value = settings.reverbRoomSize,
                    valueText = "${(settings.reverbRoomSize * 100).toInt()}%",
                    accentColor = Color(0xFFA855F7),
                    onValueChange = { onSettingsChange(settings.copy(reverbRoomSize = it)) }
                )

                // Damping
                FxSliderRow(
                    label = "Damping (Amortiguación de Agudos)",
                    value = settings.reverbDamping,
                    valueText = "${(settings.reverbDamping * 100).toInt()}%",
                    accentColor = Color(0xFFA855F7),
                    onValueChange = { onSettingsChange(settings.copy(reverbDamping = it)) }
                )

                // Width
                FxSliderRow(
                    label = "Width (Anchura Estéreo)",
                    value = settings.reverbWidth,
                    valueText = "${(settings.reverbWidth * 100).toInt()}%",
                    accentColor = Color(0xFFA855F7),
                    onValueChange = { onSettingsChange(settings.copy(reverbWidth = it)) }
                )

                // Reverb Output Level
                FxSliderRow(
                    label = "Master Reverb Level",
                    value = settings.reverbLevel,
                    valueText = "${(settings.reverbLevel * 100).toInt()}%",
                    accentColor = Color(0xFFA855F7),
                    onValueChange = { onSettingsChange(settings.copy(reverbLevel = it)) }
                )
            }
        }

        // 3. Chorus Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
            shape = AppShapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF2DD4BF)))
                    Spacer(Modifier.width(8.dp))
                    Text("CHORUS MASTER", style = MaterialTheme.typography.labelLarge, color = Color(0xFF2DD4BF), fontWeight = FontWeight.Bold)
                }

                // Voices / Nr
                FxSliderRow(
                    label = "Voces de Modulación (Nr)",
                    value = settings.chorusNr / 10f,
                    valueText = "${settings.chorusNr} voces",
                    accentColor = Color(0xFF2DD4BF),
                    onValueChange = { onSettingsChange(settings.copy(chorusNr = (it * 10).toInt().coerceIn(0, 10))) }
                )

                // Depth
                FxSliderRow(
                    label = "Depth (Profundidad)",
                    value = settings.chorusDepth / 30f,
                    valueText = "${settings.chorusDepth.toInt()} ms",
                    accentColor = Color(0xFF2DD4BF),
                    onValueChange = { onSettingsChange(settings.copy(chorusDepth = (it * 30f).coerceIn(0f, 30f))) }
                )

                // Speed
                FxSliderRow(
                    label = "Speed (Velocidad LFO)",
                    value = (settings.chorusSpeed - 0.1f) / 4.9f,
                    valueText = "${((settings.chorusSpeed * 10).toInt() / 10.0)} Hz",
                    accentColor = Color(0xFF2DD4BF),
                    onValueChange = { onSettingsChange(settings.copy(chorusSpeed = (0.1f + it * 4.9f).coerceIn(0.1f, 5.0f))) }
                )

                // Chorus Level
                FxSliderRow(
                    label = "Master Chorus Level",
                    value = settings.chorusLevel / 2f,
                    valueText = "${(settings.chorusLevel * 50).toInt()}%",
                    accentColor = Color(0xFF2DD4BF),
                    onValueChange = { onSettingsChange(settings.copy(chorusLevel = (it * 2f).coerceIn(0f, 2f))) }
                )
            }
        }
    }
}

@Composable
private fun FxSliderRow(
    label: String,
    value: Float,
    valueText: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextLight, fontSize = 12.sp)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Color(0xFF2A2D3A)
            ),
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}

// --- NOVEDADES / WHAT'S NEW SYSTEM ---

data class ReleaseNoteItem(
    val title: String,
    val description: String,
    val tag: String = "NUEVO"
)

val CURRENT_RELEASE_NOTES = listOf(
    ReleaseNoteItem(
        title = "Splash Screen Nativo de Alto Rendimiento",
        description = "Inicio instantáneo y fluido con la Splash Screen API de Android 12+. La pantalla se mantiene exactamente el tiempo necesario para inicializar el motor de audio y MIDI sin demoras artificiales."
    ),
    ReleaseNoteItem(
        title = "Exportación e Importación de Patches Individuales (.skpatch)",
        description = "Ahora puedes compartir y transferir patches específicos directamente por WhatsApp, Telegram o Drive e importarlos a cualquier concierto activo sin reemplazar tu configuración."
    ),
    ReleaseNoteItem(
        title = "Soporte Directo y Diagnóstico Integrado",
        description = "Nueva pestaña de Soporte en Ajustes para contactar asistencia por Telegram (@tutelopez7) con reporte diagnóstico automático de dispositivo, MIDI y audio."
    ),
    ReleaseNoteItem(
        title = "Respaldo en la Nube con Google Drive",
        description = "Sincronización automática de tus conciertos y configuraciones a tu cuenta de Google Drive con restauración de 1 toque."
    )
)

@Composable
fun WhatsNewDialog(
    versionName: String,
    notes: List<ReleaseNoteItem>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBackground),
            border = BorderStroke(1.dp, Color(0xFF2A2D3A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(AccentSky.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🚀", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Novedades en StageKeysLive",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = AccentSky,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "v$versionName",
                                    color = Color.Black,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            "Descubre las mejoras agregadas para tus presentaciones en vivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDark,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2A2D3A), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Notes List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkPanel, RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFF252836), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(8.dp)
                                    .background(AccentSky, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextLight,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Surface(
                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            item.tag,
                                            color = Color(0xFF10B981),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextDark,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "¡Entendido, Continuar!",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}

// --- SOPORTE & DIAGNÓSTICO SCREEN ---

@Composable
fun SupportSettingsScreen(
    googleUserEmail: String?,
    connectedMidiDevices: List<String>,
    sampleRate: Int,
    bufferSizeOption: Int,
    onShowSnackbar: (String) -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    val appVersion = getAppVersionInfo()
    val platformInfo = getPlatformDiagnosticInfo()
    val midiInfo = if (connectedMidiDevices.isEmpty()) "Ninguno" else connectedMidiDevices.joinToString(", ")
    val audioInfo = "$sampleRate Hz | Buffer: $bufferSizeOption frames"
    val accountInfo = googleUserEmail ?: "Sin cuenta vinculada"

    val diagnosticReport = buildString {
        appendLine("--- REPORTE DE DIAGNÓSTICO STAGEKEYSLIVE ---")
        appendLine("Versión: $appVersion")
        appendLine(platformInfo)
        appendLine("Audio: $audioInfo")
        appendLine("Dispositivos MIDI: $midiInfo")
        appendLine("Cuenta Google: $accountInfo")
        appendLine("Timestamp: ${System.currentTimeMillis()}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Support Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2330)),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2E3446))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(Color(0xFF229ED9).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("💬", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Soporte Directo y Contacto",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "¿Tienes dudas, problemas técnicos o sugerencias de mejoras? Contáctame directamente por Telegram.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDark,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // Action Buttons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkPanel),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2D3A))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Canal Oficial de Atención",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentSky,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Button(
                    onClick = {
                        // Copy report to clipboard first so it's guaranteed safe
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(diagnosticReport))
                        
                        // Open Telegram URL with encoded text
                        val encodedText = diagnosticReport
                            .replace(" ", "%20")
                            .replace("\n", "%0A")
                            .replace(":", "%3A")
                            .replace("|", "%7C")
                            .replace("-", "%2D")
                        val telegramUrl = "https://t.me/tutelopez7?text=$encodedText"
                        try {
                            uriHandler.openUri(telegramUrl)
                            onShowSnackbar("Abriendo Telegram (@tutelopez7)... Diagnóstico copiado al portapapeles.")
                        } catch (e: Exception) {
                            try {
                                uriHandler.openUri("https://t.me/tutelopez7")
                            } catch (_: Exception) {}
                            onShowSnackbar("Diagnóstico copiado. Pégalo en Telegram: @tutelopez7")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("🚀 Abrir Telegram (@tutelopez7)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(diagnosticReport))
                        onShowSnackbar("¡Datos de diagnóstico copiados al portapapeles!")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color(0xFF384055)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📋 Copiar Diagnóstico Técnico", color = TextLight, fontSize = 12.5.sp)
                }
            }
        }

        // Diagnostics Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkPanel),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2D3A))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Información del Sistema & Dispositivo",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccentSky,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                DiagnosticRow("Versión de App", appVersion)
                DiagnosticRow("Dispositivo y SO", platformInfo)
                DiagnosticRow("Configuración de Audio", audioInfo)
                DiagnosticRow("Dispositivos MIDI", midiInfo)
                DiagnosticRow("Cuenta Vinculada", accountInfo)
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF13161F), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 11.sp)
        Text(value, color = TextLight, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}






