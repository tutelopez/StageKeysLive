package com.midi.mainstage

sealed class MidiTarget {
    data class ChannelVolume(val channelIndex: Int) : MidiTarget()
    data class ChannelMute(val channelIndex: Int) : MidiTarget()
    data class ChannelSolo(val channelIndex: Int) : MidiTarget()
    data class Pad(val padIndex: Int) : MidiTarget()
    data class Pot(val potIndex: Int) : MidiTarget()
    
    object MasterVolume : MidiTarget()
    object FilterCutoff : MidiTarget()
    object ReverbMix : MidiTarget()
    object Sustain : MidiTarget()
    object Modulation : MidiTarget()
    object OctaveUp : MidiTarget()
    object OctaveDown : MidiTarget()
}
