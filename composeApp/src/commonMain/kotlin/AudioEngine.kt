package com.tutelopezmusic.stagekeyslive

// [POINT 2 FIX] Extend the expect interface with:
// - isAudioReady(): reports whether the Oboe stream is running (Point 3 expose)
// - startMidiLearn(target, onCaptured, onTimeout): triggers real MIDI Learn on Android,
//   no-op on Desktop (which has no hardware MIDI in this build)
// - cancelMidiLearn(): cancels an in-progress learn session
// - applyMappedCc(): called when a mapped CC arrives, routes to correct synth parameter

data class PerformanceStats(val cpuPercent: Int?, val ramMb: Int)

expect class PlatformAudioSynth() {
    var onEngineRestarted: (() -> Unit)?
    fun startPerformanceMonitor()
    fun stopPerformanceMonitor()
    fun setPerformanceListener(onStats: (PerformanceStats) -> Unit)
    fun noteOn(note: Int, velocity: Int, channel: Int = 0)
    fun noteOff(note: Int, channel: Int = 0)
    fun setVolume(volume: Float)
    fun setChannelVolume(volume: Float, channel: Int)
    fun setPan(channel: Int, pan: Float)
    fun setReverb(reverb: Float)
    fun setFilterCutoff(cutoff: Float, channel: Int = 0)
    fun setPatch(programNumber: Int, channel: Int = 0)
    fun loadSoundFont(path: String, channel: Int = 0): Boolean
    fun releaseShadowChannel(logicalChannel: Int, physicalChannel: Int = -1)
    fun allNotesOff()
    fun setModulation(value: Float, channel: Int = 0)
    fun close()

    // [POINT 3 FIX] Returns true only when the Oboe audio stream opened successfully
    fun isAudioReady(): Boolean

    fun startMidiLearn(target: MidiTarget, onCaptured: (cc: Int) -> Unit, onTimeout: () -> Unit)
    fun cancelMidiLearn()
    fun syncMidiMappings(mappings: Map<Int, MidiTarget>)
    
    fun setMidiListener(
        onMappedCc: (target: MidiTarget, floatValue: Float) -> Unit,
        onNote: (note: Int, velocity: Int, isNoteOn: Boolean) -> Unit,
        onPitchBend: (pitchBend: Float) -> Unit,
        onDeviceConnectionChanged: (deviceNames: List<String>) -> Unit,
        onProgramChange: (program: Int) -> Unit = {},
        onMidiActivity: () -> Unit = {}
    )

    // --- Audio Device Management ---
    fun getAudioDevices(): List<AudioOutputDeviceInfo>
    fun selectAudioDevice(deviceId: Int)
    fun setAudioDeviceListener(onDeviceListChanged: (List<AudioOutputDeviceInfo>) -> Unit)
    fun refreshAudioDevices()

    // --- Dynamic Engine Config ---
    fun initializeEngine(sampleRate: Int, bufferOption: Int = 0, isUsbDevice: Boolean = false)
    fun getAudioDiagnostics(): String

    // --- Continuous Pad Engine ---
    fun padSetEnabled(enabled: Boolean)
    fun padSetVolume(volume: Float)
    fun padSetPan(pan: Float)
    fun padSetBank(bankName: String)
    fun padNoteOn(pitchClass: Int)
    fun padNoteOff()
    fun padHardKillAll()

    // --- SoundFont Preview ---
    fun previewSoundFont(path: String, note: Int = 60, velocity: Int = 100, durationMs: Int = 2000)
    fun stopPreview()

    // --- Channel FX Sends & Master FX ---
    fun setChannelReverbSend(channel: Int, value: Float)
    fun setChannelChorusSend(channel: Int, value: Float)
    fun setMasterReverbParams(roomsize: Float, damp: Float, width: Float, level: Float)
    fun setMasterChorusParams(nr: Int, level: Float, speed: Float, depth: Float)

    // --- Master Bus Limiter ---
    fun setMasterLimiterEnabled(enabled: Boolean)
    fun isMasterLimiterActive(): Boolean

    // --- Audio Diagnostics & Telemetry ---
    fun getActiveVoiceCount(): Int
    fun getDspCpuLoad(): Double
    fun getPeakDspCpuLoad(): Double
    fun resetPeakDspCpuLoad()
    fun getXRunCount(): Int
}
