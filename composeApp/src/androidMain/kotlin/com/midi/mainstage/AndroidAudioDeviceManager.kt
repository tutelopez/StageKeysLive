package com.midi.mainstage

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

class AndroidAudioDeviceManager(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    var onDeviceListChanged: ((devices: List<AudioOutputDeviceInfo>) -> Unit)? = null
    var selectedDeviceId: Int = -1

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }
    }

    fun startListening() {
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        refreshDevices()
    }

    fun stopListening() {
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    fun selectDevice(deviceId: Int) {
        selectedDeviceId = deviceId
        refreshDevices() // Trigger UI update to show the new selection
    }

    fun refreshDevices() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val filtered = outputs.filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }

        val mapped = filtered.map { info ->
            val typeStr = when (info.type) {
                AudioDeviceInfo.TYPE_USB_DEVICE -> "Interfaz USB"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "Auriculares USB"
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Accesorio USB"
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con micro (Cable)"
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Auriculares (Cable)"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Altavoz Interno"
                else -> "Desconocido"
            }
            
            // Just picking a fallback string if name is empty
            val nameStr = info.productName?.toString()?.takeIf { it.isNotBlank() } ?: typeStr
            
            AudioOutputDeviceInfo(
                id = info.id,
                name = nameStr,
                type = typeStr,
                isCurrentlySelected = (info.id == selectedDeviceId)
            )
        }

        handler.post {
            onDeviceListChanged?.invoke(mapped)
        }
    }
}
