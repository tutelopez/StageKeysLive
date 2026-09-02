package com.midi.mainstage

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

actual fun getCurrentHourOfDay(): Int {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

actual fun formatTimestamp(timestampMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestampMs))
}
