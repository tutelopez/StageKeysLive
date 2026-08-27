package com.midi.mainstage

import java.util.Calendar

actual fun getCurrentHourOfDay(): Int {
    return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}
