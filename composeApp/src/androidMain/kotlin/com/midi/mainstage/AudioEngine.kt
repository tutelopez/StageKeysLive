package com.midi.mainstage

import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "StageKeysSynth"

// Timeout (ms) before MIDI Learn auto-cancels if no CC is received
private const val LEARN_TIMEOUT_MS = 7000L

actual class PlatformAudioSynth actual constructor() {

    // [POINT 2 FIX] Companion object holds a weak reference to the AndroidMidiManager
    // set by MainActivity after both objects are created.  Using companion so that
    // App.kt (commonMain) can call startMidiLearn() without knowing about Android specifics.
    companion object {
        internal var midiManager: AndroidMidiManager? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var learnTimeoutRunnable: Runnable? = null

    init {
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("oboe")
            System.loadLibrary("FLAC")
            System.loadLibrary("ogg")
            System.loadLibrary("opus")
            System.loadLibrary("vorbis")
            System.loadLibrary("vorbisenc")
            System.loadLibrary("vorbisfile")
            System.loadLibrary("sndfile")
            System.loadLibrary("fluidsynth")
            System.loadLibrary("fluidsynth-assetloader")
            System.loadLibrary("mainstage_audio")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native audio library: ${e.message}", e)
        }
    }

    actual fun noteOn(note: Int, velocity: Int, channel: Int) {
        nativeNoteOn(note, velocity, channel)
    }

    actual fun noteOff(note: Int, channel: Int) {
        nativeNoteOff(note, channel)
    }

    actual fun setVolume(volume: Float) {
        nativeSetVolume(volume)
    }

    actual fun setChannelVolume(volume: Float, channel: Int) {
        nativeSetChannelVolume(volume, channel)
    }

    actual fun setReverb(reverb: Float) {
        nativeSetReverb(reverb)
    }

    actual fun setFilterCutoff(cutoff: Float, channel: Int) {
        nativeSetFilterCutoff(cutoff, channel)
    }

    actual fun setPatch(programNumber: Int, channel: Int) {
        nativeSetPatch(programNumber, channel)
    }

    actual fun loadSoundFont(path: String, channel: Int): Boolean {
        return nativeLoadSoundFont(path, channel)
    }

    actual fun allNotesOff() {
        nativeAllNotesOff()
    }

    actual fun setModulation(value: Float, channel: Int) {
        nativeSetModulation(value, channel)
    }

    actual fun close() {
        cancelMidiLearn()
        nativeClose()
    }

    // [POINT 3 FIX] Exposes Oboe stream health to the UI layer
    actual fun isAudioReady(): Boolean = nativeIsAudioReady()

    // [POINT 2 FIX] Starts MIDI Learn mode:
    // - Sets the learn callback on AndroidMidiManager so the next physical CC
    //   from a connected keyboard/controller gets captured and returned via onCaptured.
    // - Starts a 7-second timeout; if no CC arrives, calls onTimeout and clears the callback.
    actual fun startMidiLearn(target: MidiTarget, onCaptured: (cc: Int) -> Unit, onTimeout: () -> Unit) {
        val manager = midiManager
        if (manager == null) {
            Log.w(TAG, "startMidiLearn: no MIDI manager available (no hardware connected?)")
            // On desktop or if no manager is wired, immediately time out so the UI
            // doesn't get stuck in "ESCUCHANDO..." state forever.
            mainHandler.post { onTimeout() }
            return
        }

        Log.i(TAG, "MIDI Learn started — waiting for CC for target: '$target'")

        // Cancel any previous learn session
        cancelMidiLearn()

        // Wire the one-shot learn callback
        manager.onLearnModeCcReceived = { cc ->
            Log.i(TAG, "MIDI Learn captured CC $cc for '$target'")
            cancelLearnTimeout()
            onCaptured(cc)
        }

        // Start timeout
        val timeoutRunnable = Runnable {
            if (manager.onLearnModeCcReceived != null) {
                Log.w(TAG, "MIDI Learn timed out for target: '$target'")
                manager.onLearnModeCcReceived = null
                onTimeout()
            }
        }
        learnTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, LEARN_TIMEOUT_MS)
    }

    // [POINT 2 FIX] Cancels an in-progress learn session (e.g. user dismissed dialog)
    actual fun cancelMidiLearn() {
        cancelLearnTimeout()
        midiManager?.onLearnModeCcReceived = null
    }

    actual fun syncMidiMappings(mappings: Map<Int, MidiTarget>) {
        midiManager?.ccMappings = mappings.toMap()
    }
    
    actual fun setMidiListener(
        onMappedCc: (target: MidiTarget, floatValue: Float) -> Unit,
        onNote: (note: Int, velocity: Int, isNoteOn: Boolean) -> Unit,
        onPitchBend: (pitchBend: Float) -> Unit,
        onDeviceConnectionChanged: (deviceNames: List<String>) -> Unit
    ) {
        midiManager?.onMappedCcReceived = { _, target, floatValue -> onMappedCc(target, floatValue) }
        midiManager?.onNoteReceived = onNote
        midiManager?.onPitchBendReceived = onPitchBend
        midiManager?.onDeviceConnectionChanged = onDeviceConnectionChanged
    }

    private fun cancelLearnTimeout() {
        learnTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        learnTimeoutRunnable = null
    }

    // Native JNI bindings to C++ Audio/FluidSynth engine
    private external fun nativeInit()
    private external fun nativeClose()
    private external fun nativeNoteOn(note: Int, velocity: Int, channel: Int)
    private external fun nativeNoteOff(note: Int, channel: Int)
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetChannelVolume(volume: Float, channel: Int)
    private external fun nativeSetReverb(reverb: Float)
    private external fun nativeSetFilterCutoff(cutoff: Float, channel: Int)
    private external fun nativeSetPatch(programNumber: Int, channel: Int)
    private external fun nativeLoadSoundFont(path: String, channel: Int): Boolean
    private external fun nativeIsAudioReady(): Boolean
    private external fun nativeAllNotesOff()
    private external fun nativeSetModulation(value: Float, channel: Int)
}
