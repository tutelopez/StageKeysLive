package com.midi.mainstage

actual class PlatformAudioSynth actual constructor() {
    init {
        try {
            System.loadLibrary("mainstage_audio")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    actual fun noteOn(note: Int, velocity: Int) {
        nativeNoteOn(note, velocity)
    }

    actual fun noteOff(note: Int) {
        nativeNoteOff(note)
    }

    actual fun setVolume(volume: Float) {
        nativeSetVolume(volume)
    }

    actual fun setReverb(reverb: Float) {
        nativeSetReverb(reverb)
    }

    actual fun setFilterCutoff(cutoff: Float) {
        nativeSetFilterCutoff(cutoff)
    }

    actual fun setPatch(programNumber: Int) {
        nativeSetPatch(programNumber)
    }

    actual fun loadSoundFont(path: String) {
        nativeLoadSoundFont(path)
    }

    actual fun close() {
        nativeClose()
    }

    // Native JNI bindings to C++ Audio/FluidSynth engine
    private external fun nativeInit()
    private external fun nativeClose()
    private external fun nativeNoteOn(note: Int, velocity: Int)
    private external fun nativeNoteOff(note: Int)
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetReverb(reverb: Float)
    private external fun nativeSetFilterCutoff(cutoff: Float)
    private external fun nativeSetPatch(programNumber: Int)
    private external fun nativeLoadSoundFont(path: String)
}
