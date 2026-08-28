package com.midi.mainstage

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

class DeviceStatusNotifier(private val context: Context) {
    private val channelId = "device_status"
    private var notificationId = 1000

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Estado de Dispositivos"
            val descriptionText = "Notificaciones sobre conexiones de interfaces MIDI y de Audio"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isAppInForeground(): Boolean {
        return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    private fun postNotification(title: String, message: String) {
        if (isAppInForeground()) {
            return // Use snackbar in the foreground, do not show system notification
        }

        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return // Permission not granted
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync) // Generic icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(notificationId++, builder.build())
        }
    }

    fun notifyDeviceConnected(name: String, isAudio: Boolean) {
        val type = if (isAudio) "Interfaz de Audio" else "Teclado MIDI"
        postNotification("$type conectado", name)
    }

    fun notifyDeviceDisconnected(name: String, isAudio: Boolean) {
        val type = if (isAudio) "Interfaz de Audio" else "Teclado MIDI"
        postNotification("$type desconectado", name)
    }
}
