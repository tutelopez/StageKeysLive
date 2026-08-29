package com.midi.mainstage

actual fun setKeepScreenOn(keep: Boolean) {
    // No-op for desktop
}

actual fun getBatteryLevel(): Int {
    return 100 // Desktop stub
}

actual fun isBatteryCharging(): Boolean {
    return true // Desktop stub
}
