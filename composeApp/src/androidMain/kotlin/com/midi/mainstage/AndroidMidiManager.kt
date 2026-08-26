package com.midi.mainstage

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log

private const val TAG = "StageKeysMIDI"

class AndroidMidiManager(
    private val context: Context,
    private val synth: PlatformAudioSynth
) {
    private val midiManager: MidiManager? = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val handler = Handler(Looper.getMainLooper())
    private val openDevices = mutableListOf<MidiDevice>()

    // ---------------------------------------------------------------
    // [POINT 2 FIX] MIDI Learn callback.
    // When non-null, the next CC message received will be delivered here
    // instead of being processed as a normal control change.
    // Set to null after the first CC is captured or when the timeout fires.
    // ---------------------------------------------------------------
    var onLearnModeCcReceived: ((cc: Int) -> Unit)? = null

    // ---------------------------------------------------------------
    // [POINT 2 FIX] Dynamic CC → target mapping.
    // Maps a CC number (0-127) to a named target string (e.g. "volume_ch1",
    // "reverb", "master"). Applied during normal CC processing when Learn
    // mode is off, and fed into the Kotlin state through onMappedCcReceived.
    // ---------------------------------------------------------------
    var ccMappings: Map<Int, String> = emptyMap()

    // Called on main thread when a mapped CC arrives during normal play.
    // Kotlin UI (App.kt) subscribes here to apply the change to the correct state.
    var onMappedCcReceived: ((cc: Int, target: String, floatValue: Float) -> Unit)? = null

    fun startListening() {
        if (midiManager == null) {
            Log.w(TAG, "MIDI service not available on this device")
            return
        }

        // Register for real-time device connection updates
        midiManager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(deviceInfo: MidiDeviceInfo) {
                Log.i(TAG, "MIDI device added: ${deviceInfo.properties}")
                openMidiDevice(deviceInfo)
            }

            override fun onDeviceRemoved(deviceInfo: MidiDeviceInfo) {
                Log.i(TAG, "MIDI device removed: ${deviceInfo.properties}")
                // Remove all open handles for this specific device so we don't
                // leak stale MidiDevice references after hot-unplug.
                val toClose = openDevices.filter { it.info == deviceInfo }
                toClose.forEach { device ->
                    try { device.close() } catch (e: Exception) { /* ignore */ }
                    openDevices.remove(device)
                }
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

                        // [POINT 2 FIX] — MIDI Learn intercept:
                        // If Learn mode is active, capture this CC and deliver it
                        // to the UI instead of processing it as a normal control.
                        val learnCallback = onLearnModeCcReceived
                        if (learnCallback != null) {
                            Log.i(TAG, "MIDI Learn captured CC $controller → mapping target")
                            handler.post {
                                learnCallback(controller)
                                onLearnModeCcReceived = null // auto-clear after capture
                            }
                            i += 3
                            continue
                        }

                        // [POINT 2 FIX] — Dynamic mapping lookup:
                        // If this CC has a user-defined mapping, notify App.kt to apply
                        // the change to the correct channel/parameter state.
                        val mappedTarget = ccMappings[controller]
                        if (mappedTarget != null) {
                            Log.d(TAG, "Mapped CC $controller → '$mappedTarget' = $floatValue")
                            handler.post {
                                onMappedCcReceived?.invoke(controller, mappedTarget, floatValue)
                            }
                            i += 3
                            continue
                        }

                        // Default built-in CC handling (unmapped CCs)
                        when (controller) {
                            7  -> synth.setVolume(floatValue)       // CC 7:  Volume fader
                            74 -> synth.setFilterCutoff(floatValue) // CC 74: Filter cutoff
                            91 -> synth.setReverb(floatValue)       // CC 91: Reverb mix
                            else -> Log.v(TAG, "Unhandled CC $controller = $value")
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
