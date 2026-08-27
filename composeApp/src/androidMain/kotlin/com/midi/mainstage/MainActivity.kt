package com.midi.mainstage

import com.midi.mainstage.App
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private const val TAG = "StageKeysMain"

class MainActivity : ComponentActivity() {
    private lateinit var midiManager: AndroidMidiManager
    private val synth = PlatformAudioSynth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system bars (Full Screen Immersive Mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

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

        // Handled by App.kt via setMidiListener

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
