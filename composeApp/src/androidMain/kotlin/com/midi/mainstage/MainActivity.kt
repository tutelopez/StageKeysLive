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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private const val TAG = "StageKeysMain"

class MainActivity : ComponentActivity() {
    companion object {
        var instance: MainActivity? = null
    }
    private lateinit var midiManager: AndroidMidiManager
    private lateinit var audioDeviceManager: AndroidAudioDeviceManager
    private val synth = PlatformAudioSynth()
    
    @Volatile
    private var isAppReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isAppReady }

        super.onCreate(savedInstanceState)
        instance = this
        
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
        val savedBufferOption = prefs.getInt("bufferOption", 0)
        
        PlatformAudioSynth.optimalSampleRate = if (savedSampleRate != -1) savedSampleRate else (sampleRateStr?.toIntOrNull() ?: 48000)
        PlatformAudioSynth.optimalBufferFrames = framesStr?.toIntOrNull() ?: 256
        
        PlatformAudioSynth.globalPrefs = prefs
        
        synth.setAssetManager(assets)
        
        // Hide system bars (Full Screen Immersive Mode) & keep screen awake
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Initialize Android base folder for file persistence
        setAndroidBaseDir(filesDir)

        // Initialize MIDI and audio managers
        midiManager = AndroidMidiManager(this, synth, notifier)
        audioDeviceManager = AndroidAudioDeviceManager(this, notifier)

        PlatformAudioSynth.midiManager = midiManager
        PlatformAudioSynth.audioDeviceManager = audioDeviceManager

        midiManager.startListening()
        audioDeviceManager.startListening()

        // Real asynchronous initialization: start audio engine + load default SF2
        Thread {
            try {
                synth.initializeEngine(PlatformAudioSynth.optimalSampleRate, savedBufferOption)
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
                Log.i(TAG, "Audio engine and SoundFont initialized ✓")

                // Handle benchmark intent if requested on startup
                handleBenchmarkIntent(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Initialization error", e)
            } finally {
                isAppReady = true
            }
        }.start()

        setContent {
            StageKeysTheme {
                App(synth)
            }
        }
    }

    private var benchmarkRunner: BenchmarkRunner? = null

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        handleBenchmarkIntent(intent)
    }

    private fun handleBenchmarkIntent(intent: android.content.Intent?) {
        val benchCmd = intent?.getStringExtra("benchmark") ?: return
        val duration = intent.getLongExtra("duration", 900L) // default 15 min (900s)
        if (benchCmd.equals("START", ignoreCase = true)) {
            Log.i(TAG, "Lanzando BenchmarkRunner por intent con duración: ${duration}s")
            if (benchmarkRunner == null) {
                benchmarkRunner = BenchmarkRunner(this, synth)
            }
            benchmarkRunner?.startBenchmark(durationSeconds = duration)
        } else if (benchCmd.equals("STOP", ignoreCase = true)) {
            Log.i(TAG, "Deteniendo BenchmarkRunner por intent")
            benchmarkRunner?.stopBenchmark()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        // Release hardware and native engine resources
        PlatformAudioSynth.midiManager = null
        PlatformAudioSynth.audioDeviceManager = null
        midiManager.stopListening()
        audioDeviceManager.stopListening()
        synth.close()
    }
}
