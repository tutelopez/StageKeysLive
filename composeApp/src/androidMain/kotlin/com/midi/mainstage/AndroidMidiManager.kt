package com.midi.mainstage

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper

class AndroidMidiManager(
    private val context: Context,
    private val synth: PlatformAudioSynth
) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val handler = Handler(Looper.getMainLooper())
    private val openDevices = mutableListOf<MidiDevice>()

    fun startListening() {
        if (midiManager == null) return

        // Register for real-time device connection updates
        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(deviceInfo: MidiDeviceInfo) {
                openMidiDevice(deviceInfo)
            }

            override fun onDeviceRemoved(deviceInfo: MidiDeviceInfo) {
                // Device unplugged, handled natively by closing streams
            }
        }, handler)

        // Initial scan of already plugged devices
        for (deviceInfo in midiManager.devices) {
            openMidiDevice(deviceInfo)
        }
    }

    private fun openMidiDevice(deviceInfo: MidiDeviceInfo) {
        midiManager?.openDevice(deviceInfo, { device ->
            if (device != null) {
                openDevices.add(device)
                
                // Open output ports of the device to receive MIDI messages
                for (portInfo in deviceInfo.ports) {
                    if (portInfo.type == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                        val outputPort = device.openOutputPort(portInfo.portNumber)
                        outputPort?.connect(MidiInputReceiver())
                    }
                }
            }
        }, handler)
    }

    fun stopListening() {
        for (device in openDevices) {
            try {
                device.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        openDevices.clear()
    }

    // Custom MidiReceiver parsing binary MIDI streams
    inner class MidiInputReceiver : MidiReceiver() {
        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val currentByte = data[i].toInt() and 0xFF
                
                // Status bytes start at 0x80 (128)
                if (currentByte >= 0x80) {
                    val status = currentByte and 0xF0
                    
                    if (status == 0x90 && i + 2 < end) { // Note On
                        val note = data[i + 1].toInt() and 0x7F
                        val velocity = data[i + 2].toInt() and 0x7F
                        if (velocity > 0) {
                            synth.noteOn(note, velocity)
                        } else {
                            synth.noteOff(note)
                        }
                        i += 3
                    } else if (status == 0x80 && i + 2 < end) { // Note Off
                        val note = data[i + 1].toInt() and 0x7F
                        synth.noteOff(note)
                        i += 3
                    } else if (status == 0xB0 && i + 2 < end) { // Control Change (CC)
                        val controller = data[i + 1].toInt() and 0x7F
                        val value = data[i + 2].toInt() and 0x7F
                        val floatValue = value / 127.0f
                        
                        when (controller) {
                            7 -> synth.setVolume(floatValue)       // CC 7: Volume fader
                            74 -> synth.setFilterCutoff(floatValue) // CC 74: Filter cutoff knob
                            91 -> synth.setReverb(floatValue)       // CC 91: Reverb mix knob
                        }
                        i += 3
                    } else {
                        i++
                    }
                } else {
                    i++
                }
            }
        }
    }
}
