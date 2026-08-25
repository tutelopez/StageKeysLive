package com.midi.mainstage

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    private lateinit var midiManager: AndroidMidiManager
    private val synth = PlatformAudioSynth()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState: Bundle?)
        
        // Initialize Android base folder for file persistence
        setAndroidBaseDir(filesDir)

        // Setup native audio engine and physical MIDI keyboard listeners
        midiManager = AndroidMidiManager(this, synth)
        midiManager.startListening()

        setContent {
            App(synth)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release hardware and native engine resources
        midiManager.stopListening()
        synth.close()
    }
}
