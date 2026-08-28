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
    private val synth: PlatformAudioSynth,
    private val notifier: DeviceStatusNotifier
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
    var onDeviceConnectionChanged: ((deviceNames: List<String>) -> Unit)? = null

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
                val name = deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Dispositivo MIDI"
                notifier.notifyDeviceDisconnected(name, isAudio = false)
                Log.i(TAG, "MIDI device removed: ${deviceInfo.properties}")
                val toClose = openDevices.filter { it.info == deviceInfo }
                toClose.forEach { device ->
                    try { device.close() } catch (e: Exception) { /* ignore */ }
                    openDevices.remove(device)
                }
                if (openDevices.isEmpty()) {
                    currentDeviceName = null
                    handler.post { onDeviceConnectionChanged?.invoke(emptyList()) }
                } else {
                    val deviceNames = openDevices.map { it.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device" }
                    currentDeviceName = deviceNames.firstOrNull()
                    handler.post { onDeviceConnectionChanged?.invoke(deviceNames) }
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
                }
                val name = deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Device"
                notifier.notifyDeviceConnected(name, isAudio = false)
                
                val deviceNames = openDevices.map { it.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Unknown Device" }
                handler.post { onDeviceConnectionChanged?.invoke(deviceNames) }
                
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
        onDeviceConnectionChanged?.invoke(emptyList())
    }

    // Custom MidiReceiver parsing binary MIDI streams
    inner class MidiInputReceiver : MidiReceiver() {
        private var runningStatus = 0

        override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
            var i = offset
            val end = offset + count
            while (i < end) {
                val currentByte = data[i].toInt() and 0xFF

                var status = 0
                var isRunningStatus = false

                // Status bytes start at 0x80 (128)
                if (currentByte >= 0x80) {
                    status = currentByte and 0xF0
                    runningStatus = status
                } else {
                    if (runningStatus >= 0x80) {
                        status = runningStatus
                        isRunningStatus = true
                    } else {
                        // No valid status, skip byte
                        i++
                        continue
                    }
                }

                val dataIndex = if (isRunningStatus) i else i + 1

                if (status == 0x90 && dataIndex + 1 < end) { // Note On
                    val note = data[dataIndex].toInt() and 0x7F
                    val velocity = data[dataIndex + 1].toInt() and 0x7F
                    
                    handler.post {
                        if (velocity > 0) {
                            onNoteReceived?.invoke(note, velocity, true)
                        } else {
                            onNoteReceived?.invoke(note, 0, false)
                        }
                    }
                    i = dataIndex + 2

                } else if (status == 0x80 && dataIndex + 1 < end) { // Note Off
                    val note = data[dataIndex].toInt() and 0x7F
                    handler.post {
                        onNoteReceived?.invoke(note, 0, false)
                    }
                    i = dataIndex + 2

                } else if (status == 0xE0 && dataIndex + 1 < end) { // Pitch Bend
                    val lsb = data[dataIndex].toInt() and 0x7F
                    val msb = data[dataIndex + 1].toInt() and 0x7F
                    val pitchVal = (msb shl 7) or lsb
                    // Normalize 0..16383 to -1.0..1.0 (center is 8192)
                    val floatVal = (pitchVal - 8192) / 8192.0f
                    handler.post {
                        onPitchBendReceived?.invoke(floatVal)
                    }
                    i = dataIndex + 2

                } else if (status == 0xB0 && dataIndex + 1 < end) { // Control Change
                    val controller = data[dataIndex].toInt() and 0x7F
                    val value = data[dataIndex + 1].toInt() and 0x7F
                    val floatValue = value / 127f

                    // [POINT 2 FIX] ✨ MIDI Learn intercept:
                    val learnCallback = onLearnModeCcReceived
                    if (learnCallback != null) {
                        Log.i(TAG, "MIDI Learn captured CC $controller → mapping target")
                        handler.post {
                            learnCallback(controller)
                            onLearnModeCcReceived = null // auto-clear after capture
                        }
                        i = dataIndex + 2
                        continue
                    }

                    // [POINT 2 FIX] ✨ Dynamic mapping lookup:
                    val mappedTarget = ccMappings[controller]
                    if (mappedTarget != null) {
                        Log.d(TAG, "Mapped CC $controller → '$mappedTarget' = $floatValue")
                        handler.post {
                            onMappedCcReceived?.invoke(controller, mappedTarget, floatValue)
                        }
                        i = dataIndex + 2
                        continue
                    }

                    i = dataIndex + 2
                } else {
                    // System messages or unrecognized, skip 1 byte and reset running status if it was a status byte
                    if (!isRunningStatus) {
                        if (currentByte >= 0xF0) runningStatus = 0
                        i++
                    } else {
                        // In running status but not enough data? Break and wait for next chunk, or skip
                        // Usually Oboe/MIDI delivers whole messages. We just increment to avoid infinite loop.
                        i++
                    }
                }
            }
        }
    }
}
