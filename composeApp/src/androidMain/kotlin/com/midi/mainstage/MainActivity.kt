package com.midi.mainstage

import com.midi.mainstage.App
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

private const val TAG = "StageKeysMain"

class MainActivity : ComponentActivity() {
    private lateinit var midiManager: AndroidMidiManager
    private val synth = PlatformAudioSynth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Android base folder for file persistence
        setAndroidBaseDir(filesDir)

        // Setup native audio engine and physical MIDI keyboard listeners
        midiManager = AndroidMidiManager(this, synth)

        // [POINT 2 FIX] Wire the midiManager reference into PlatformAudioSynth's companion
        // so that App.kt can call synth.startMidiLearn() without knowing about Android.
        PlatformAudioSynth.midiManager = midiManager

        // Load default SoundFont from assets so that FluidSynth can produce sound!
        Thread {
            try {
                val sf2Name = "PianoDefault.sf2"
                val outFile = java.io.File(cacheDir, sf2Name)
                if (!outFile.exists()) {
                    assets.open(sf2Name).use { input ->
                        java.io.FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                synth.loadSoundFont(outFile.absolutePath)
                Log.i(TAG, "Successfully loaded SoundFont: ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load SoundFont from assets", e)
            }
        }.start()

        // [POINT 2 FIX] Wire the dynamic CC→target mapping callback.
        // When a mapped CC arrives during performance, apply it to the correct synth parameter.
        // Targets follow the naming convention used in App.kt's midiCcMappings state.
        midiManager.onMappedCcReceived = { cc, target, floatValue ->
            Log.d(TAG, "Applying mapped CC $cc → '$target' = $floatValue")
            when {
                target.contains("Volume", ignoreCase = true) -> synth.setVolume(floatValue)
                target.contains("Filter", ignoreCase = true) -> synth.setFilterCutoff(floatValue)
                target.contains("Reverb", ignoreCase = true) -> synth.setReverb(floatValue)
                // Future: route "volume_ch1", "volume_ch2", etc. to per-channel gain
            }
        }

        midiManager.startListening()

        // [POINT 3 FIX] Log audio readiness after a brief delay so the Oboe stream
        // has time to open before we check.
        android.os.Handler(mainLooper).postDelayed({
            val ready = synth.isAudioReady()
            if (ready) {
                Log.i(TAG, "Audio engine is ready ✓")
            } else {
                Log.e(TAG, "Audio engine FAILED to start — check logcat for Oboe errors")
            }
        }, 500)

        setContent {
            App(synth)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release hardware and native engine resources
        PlatformAudioSynth.midiManager = null
        midiManager.stopListening()
        synth.close()
    }
}
