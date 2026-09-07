package com.midi.mainstage

import android.os.Build

actual fun getPlatformDiagnosticInfo(): String {
    return "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
}

actual fun getAppVersionInfo(): String {
    return "StageKeysLive v1.0 (Build 1)"
}
