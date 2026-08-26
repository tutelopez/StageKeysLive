package com.midi.mainstage

// [POINT 2 FIX] Extend the expect interface with:
// - isAudioReady(): reports whether the Oboe stream is running (Point 3 expose)
// - startMidiLearn(target, onCaptured, onTimeout): triggers real MIDI Learn on Android,
//   no-op on Desktop (which has no hardware MIDI in this build)
// - cancelMidiLearn(): cancels an in-progress learn session
// - applyMappedCc(): called when a mapped CC arrives, routes to correct synth parameter

expect class PlatformAudioSynth() {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun setVolume(volume: Float)
    fun setReverb(reverb: Float)
    fun setFilterCutoff(cutoff: Float)
    fun setPatch(programNumber: Int)
    fun loadSoundFont(path: String)
    fun close()

    // [POINT 3 FIX] Returns true only when the Oboe audio stream opened successfully
    fun isAudioReady(): Boolean

    // [POINT 2 FIX] MIDI Learn bridge — implemented by Android actual only
    // Desktop actual is a no-op since there is no hardware MIDI in that target
    fun startMidiLearn(target: String, onCaptured: (cc: Int) -> Unit, onTimeout: () -> Unit)
    fun cancelMidiLearn()
    fun syncMidiMappings(mappings: Map<Int, String>)
}
