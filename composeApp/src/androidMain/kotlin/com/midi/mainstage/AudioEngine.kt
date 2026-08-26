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
            System.loadLibrary("mainstage_audio")
            nativeInit()
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native audio library: ${e.message}")
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
        cancelMidiLearn()
        nativeClose()
    }

    // [POINT 3 FIX] Exposes Oboe stream health to the UI layer
    actual fun isAudioReady(): Boolean = nativeIsAudioReady()

    // [POINT 2 FIX] Starts MIDI Learn mode:
    // - Sets the learn callback on AndroidMidiManager so the next physical CC
    //   from a connected keyboard/controller gets captured and returned via onCaptured.
    // - Starts a 7-second timeout; if no CC arrives, calls onTimeout and clears the callback.
    actual fun startMidiLearn(target: String, onCaptured: (cc: Int) -> Unit, onTimeout: () -> Unit) {
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

    private fun cancelLearnTimeout() {
        learnTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        learnTimeoutRunnable = null
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
    private external fun nativeIsAudioReady(): Boolean
}
