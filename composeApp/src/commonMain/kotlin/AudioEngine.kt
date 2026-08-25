package com.midi.mainstage

expect class PlatformAudioSynth() {
    fun noteOn(note: Int, velocity: Int)
    fun noteOff(note: Int)
    fun setVolume(volume: Float)
    fun setReverb(reverb: Float)
    fun setFilterCutoff(cutoff: Float)
    fun setPatch(programNumber: Int)
    fun loadSoundFont(path: String)
    fun close()
}
