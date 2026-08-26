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
    var onLearnModeCcReceived: ((cc: Int) -> Unit)? = null

    // ---------------------------------------------------------------
    // [POINT 2 FIX] Dynamic CC → target mapping.
    var ccMappings: Map<Int, MidiTarget> = emptyMap()

    // Called on main thread when a mapped CC arrives during normal play.
    var onMappedCcReceived: ((cc: Int, target: MidiTarget, floatValue: Float) -> Unit)? = null

    // [POINT 2 FIX] Routing note events back to Compose UI
    var onNoteReceived: ((note: Int, velocity: Int, isNoteOn: Boolean) -> Unit)? = null

    // [POINT 4 FIX] Pitch Bend event routing
    var onPitchBendReceived: ((pitchBend: Float) -> Unit)? = null

    // Tracking currently connected device name for mappings
    var currentDeviceName: String? = null
    var onDeviceConnectionChanged: ((deviceName: String?) -> Unit)? = null

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
                val toClose = openDevices.filter { it.info == deviceInfo }
                toClose.forEach { device ->
                    try { device.close() } catch (e: Exception) { /* ignore */ }
                    openDevices.remove(device)
                }
                if (openDevices.isEmpty()) {
                    currentDeviceName = null
                    handler.post { onDeviceConnectionChanged?.invoke(null) }
                } else {
                    currentDeviceName = openDevices.first().info.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                    handler.post { onDeviceConnectionChanged?.invoke(currentDeviceName) }
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
                if (currentDeviceName == null) {
                    currentDeviceName = deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME)
                    handler.post { onDeviceConnectionChanged?.invoke(currentDeviceName) }
                }
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
        currentDeviceName = null
        onDeviceConnectionChanged?.invoke(null)
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

                    val channel = currentByte and 0x0F

                    if (status == 0x90 && i + 2 < end) { // Note On
                        val note = data[i + 1].toInt() and 0x7F
                        val velocity = data[i + 2].toInt() and 0x7F
                        
                        handler.post {
                            if (velocity > 0) {
                                onNoteReceived?.invoke(note, velocity, true)
                            } else {
                                onNoteReceived?.invoke(note, 0, false)
                            }
                        }
                        i += 3

                    } else if (status == 0x80 && i + 2 < end) { // Note Off
                        val note = data[i + 1].toInt() and 0x7F
                        handler.post {
                            onNoteReceived?.invoke(note, 0, false)
                        }
                        i += 3

                    } else if (status == 0xE0 && i + 2 < end) { // Pitch Bend
                        val lsb = data[i + 1].toInt() and 0x7F
                        val msb = data[i + 2].toInt() and 0x7F
                        val pitchVal = (msb shl 7) or lsb
                        // Normalize 0..16383 to -1.0..1.0 (center is 8192)
                        val floatVal = (pitchVal - 8192) / 8192.0f
                        handler.post {
                            onPitchBendReceived?.invoke(floatVal)
                        }
                        i += 3

                    } else if (status == 0xB0 && i + 2 < end) { // Control Change (CC)
                        val controller = data[i + 1].toInt() and 0x7F
                        val value = data[i + 2].toInt() and 0x7F
                        val floatValue = value / 127.0f

                        // [POINT 2 FIX] — MIDI Learn intercept:
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
                        val mappedTarget = ccMappings[controller]
                        if (mappedTarget != null) {
                            Log.d(TAG, "Mapped CC $controller → '$mappedTarget' = $floatValue")
                            handler.post {
                                onMappedCcReceived?.invoke(controller, mappedTarget, floatValue)
                            }
                            i += 3
                            continue
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
