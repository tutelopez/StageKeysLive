package com.midi.mainstage

import com.midi.mainstage.App
import android.os.Bundle
import android.media.AudioManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val TAG = "StageKeysMain"

class MainActivity : ComponentActivity() {
    private lateinit var midiManager: AndroidMidiManager
    private lateinit var audioDeviceManager: AndroidAudioDeviceManager
    private val synth = PlatformAudioSynth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        val notifier = DeviceStatusNotifier(this)

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val framesStr = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        
        val prefs = getSharedPreferences("AudioSettings", Context.MODE_PRIVATE)
        val savedSampleRate = prefs.getInt("sampleRate", -1)
        
        PlatformAudioSynth.optimalSampleRate = if (savedSampleRate != -1) savedSampleRate else (sampleRateStr?.toIntOrNull() ?: 48000)
        PlatformAudioSynth.optimalBufferFrames = framesStr?.toIntOrNull() ?: 256
        
        PlatformAudioSynth.globalPrefs = prefs
        
        synth.setAssetManager(assets)
        
        Thread {
            synth.initializeEngine(PlatformAudioSynth.optimalSampleRate)
        }.start()
        
        // Hide system bars (Full Screen Immersive Mode)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Initialize Android base folder for file persistence
        setAndroidBaseDir(filesDir)

        // Initialize MIDI manager AFTER synth is ready (so listeners can be wired)
        // Pass the actual instance of PlatformAudioSynth to the AndroidMidiManager
        midiManager = AndroidMidiManager(this, synth, notifier)
        audioDeviceManager = AndroidAudioDeviceManager(this, notifier)

        // [POINT 2 FIX] Wire the managers into PlatformAudioSynth's companion
        // so that App.kt can call them without knowing about Android.
        PlatformAudioSynth.midiManager = midiManager
        PlatformAudioSynth.audioDeviceManager = audioDeviceManager
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
        audioDeviceManager.startListening()

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
            StageKeysTheme {
                App(synth)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release hardware and native engine resources
        PlatformAudioSynth.midiManager = null
        PlatformAudioSynth.audioDeviceManager = null
        midiManager.stopListening()
        audioDeviceManager.stopListening()
        synth.close()
    }
}
