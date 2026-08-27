package com.midi.mainstage

data class AudioOutputDeviceInfo(
    val id: Int,
    val name: String,
    val type: String,
    val isCurrentlySelected: Boolean
)
