package com.midi.mainstage

import javax.sound.midi.MidiSystem
import javax.sound.midi.Synthesizer
import javax.sound.midi.MidiChannel

actual class PlatformAudioSynth actual constructor() {
    private var synthesizer: Synthesizer? = null
    private var channel: MidiChannel? = null
    private var currentProgram = 0

    init {
        Thread {
            try {
                synthesizer = MidiSystem.getSynthesizer()
                synthesizer?.open()
                if (synthesizer?.channels != null && synthesizer!!.channels.isNotEmpty()) {
                    channel = synthesizer!!.channels[0]
                    channel?.programChange(currentProgram)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    actual fun noteOn(note: Int, velocity: Int) {
        channel?.noteOn(note, velocity)
    }

    actual fun noteOff(note: Int) {
        channel?.noteOff(note)
    }

    actual fun setVolume(volume: Float) {
        channel?.controlChange(7, (volume * 127).toInt())
    }

    actual fun setReverb(reverb: Float) {
        channel?.controlChange(91, (reverb * 127).toInt())
    }

    actual fun setFilterCutoff(cutoff: Float) {
        channel?.controlChange(74, (cutoff * 127).toInt())
    }

    actual fun setPatch(programNumber: Int) {
        currentProgram = programNumber
        channel?.programChange(programNumber)
    }

    actual fun loadSoundFont(path: String) {
        // Desktop uses system default wavetable synth; SF2 loading not applicable here
    }

    actual fun close() {
        try {
            synthesizer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // [POINT 3 FIX] Desktop always reports audio as ready (javax.sound.midi opens synchronously)
    actual fun isAudioReady(): Boolean = synthesizer?.isOpen == true

    // [POINT 2 FIX] Desktop has no physical MIDI Learn (no hardware MIDI in this target).
    // Immediately invoke onTimeout so the UI exits "ESCUCHANDO..." without hanging.
    actual fun startMidiLearn(target: String, onCaptured: (cc: Int) -> Unit, onTimeout: () -> Unit) {
        onTimeout()
    }

    actual fun cancelMidiLearn() {
        // No-op on Desktop
    }
}
