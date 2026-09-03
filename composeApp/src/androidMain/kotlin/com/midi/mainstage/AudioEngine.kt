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
        internal var audioDeviceManager: AndroidAudioDeviceManager? = null
        internal var perfMonitor = AndroidPerformanceMonitor()
        var globalPrefs: android.content.SharedPreferences? = null
        var optimalSampleRate: Int = 48000
        var optimalBufferFrames: Int = 256
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
            // nativeInit() is now called explicitly via initializeEngine() from a background thread
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

    actual fun setPan(channel: Int, pan: Float) {
        nativeSetPan(channel, pan)
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

    // --- Audio Device Management ---
    actual fun getAudioDevices(): List<AudioOutputDeviceInfo> {
        // Since we are synchronous here, let's just trigger a refresh so the callback fires
        audioDeviceManager?.refreshDevices()
        return emptyList() // The actual list is delivered via the callback
    }

    actual fun selectAudioDevice(deviceId: Int) {
        audioDeviceManager?.selectDevice(deviceId)
    }

    actual fun setAudioDeviceListener(onDeviceListChanged: (List<AudioOutputDeviceInfo>) -> Unit) {
        audioDeviceManager?.onDeviceListChanged = onDeviceListChanged
    }

    actual fun refreshAudioDevices() {
        audioDeviceManager?.refreshDevices()
    }

    // --- Dynamic Engine Config ---
    actual fun initializeEngine(sampleRate: Int) {
        globalPrefs?.edit()?.putInt("sampleRate", sampleRate)?.apply()
        nativeInit(sampleRate, optimalBufferFrames)
    }

    actual fun getAudioDiagnostics(): String {
        return nativeGetAudioDiagnostics()
    }

    actual fun padSetEnabled(enabled: Boolean) {
        nativePadSetEnabled(enabled)
    }

    actual fun padSetVolume(volume: Float) {
        nativePadSetVolume(volume)
    }

    actual fun padSetPan(pan: Float) {
        nativePadSetPan(pan)
    }

    actual fun padSetBank(bankName: String) {
        nativePadSetBank(bankName)
    }

    actual fun padNoteOn(pitchClass: Int) {
        nativePadNoteOn(pitchClass)
    }

    actual fun padNoteOff() {
        nativePadNoteOff()
    }

    actual fun padHardKillAll() {
        nativePadHardKillAll()
    }

    actual fun previewSoundFont(path: String, note: Int, velocity: Int, durationMs: Int) {
        nativePreviewSoundFont(path, note, velocity, durationMs)
    }

    actual fun stopPreview() {
        nativeStopPreview()
    }

    // Native JNI bindings to C++ Audio/FluidSynth engine
    private external fun nativeInit(sampleRate: Int, bufferFrames: Int)
    private external fun nativeClose()
    private external fun nativeNoteOn(note: Int, velocity: Int, channel: Int)
    private external fun nativeNoteOff(note: Int, channel: Int)
    private external fun nativeSetVolume(volume: Float)
    private external fun nativeSetChannelVolume(volume: Float, channel: Int)
    private external fun nativeSetPan(channel: Int, pan: Float)
    private external fun nativeSetReverb(reverb: Float)
    private external fun nativeSetFilterCutoff(cutoff: Float, channel: Int)
    private external fun nativeSetPatch(programNumber: Int, channel: Int)
    private external fun nativeLoadSoundFont(path: String, channel: Int): Boolean
    private external fun nativeIsAudioReady(): Boolean
    private external fun nativeAllNotesOff()
    private external fun nativeSetModulation(value: Float, channel: Int)
    private external fun nativeGetAudioDiagnostics(): String
    
    // Pad Engine
    private external fun nativePadSetEnabled(enabled: Boolean)
    private external fun nativePadSetVolume(volume: Float)
    private external fun nativePadSetPan(pan: Float)
    private external fun nativePadSetBank(bankName: String)
    private external fun nativePadNoteOn(pitchClass: Int)
    private external fun nativePadNoteOff()
    private external fun nativePadHardKillAll()

    // SoundFont Preview
    private external fun nativePreviewSoundFont(path: String, note: Int, velocity: Int, durationMs: Int)
    private external fun nativeStopPreview()
    
    fun setAssetManager(am: android.content.res.AssetManager) {
        nativeSetAssetManager(am)
    }
    
    actual fun startPerformanceMonitor() {
        perfMonitor.start()
    }
    
    actual fun stopPerformanceMonitor() {
        perfMonitor.stop()
    }
    
    actual fun setPerformanceListener(onStats: (PerformanceStats) -> Unit) {
        perfMonitor.setListener(onStats)
    }

    private external fun nativeSetAssetManager(am: android.content.res.AssetManager)
}
