package com.midi.mainstage

// [POINT 2 FIX] Extend the expect interface with:
// - isAudioReady(): reports whether the Oboe stream is running (Point 3 expose)
// - startMidiLearn(target, onCaptured, onTimeout): triggers real MIDI Learn on Android,
//   no-op on Desktop (which has no hardware MIDI in this build)
// - cancelMidiLearn(): cancels an in-progress learn session
// - applyMappedCc(): called when a mapped CC arrives, routes to correct synth parameter

data class PerformanceStats(val cpuPercent: Int?, val ramMb: Int)

expect class PlatformAudioSynth() {
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
        onDeviceConnectionChanged: (deviceNames: List<String>) -> Unit
    )

    // --- Audio Device Management ---
    fun getAudioDevices(): List<AudioOutputDeviceInfo>
    fun selectAudioDevice(deviceId: Int)
    fun setAudioDeviceListener(onDeviceListChanged: (List<AudioOutputDeviceInfo>) -> Unit)
    fun refreshAudioDevices()

    // --- Dynamic Engine Config ---
    fun initializeEngine(sampleRate: Int)
    fun getAudioDiagnostics(): String

    // --- Continuous Pad Engine ---
    fun padSetEnabled(enabled: Boolean)
    fun padSetVolume(volume: Float)
    fun padSetBank(bankName: String)
    fun padNoteOn(pitchClass: Int)
    fun padNoteOff()
    fun padHardKillAll()
}
