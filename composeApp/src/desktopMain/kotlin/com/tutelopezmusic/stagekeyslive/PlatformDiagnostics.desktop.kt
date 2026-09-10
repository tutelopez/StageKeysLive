package com.tutelopezmusic.stagekeyslive

actual fun getPlatformDiagnosticInfo(): String {
    return "Desktop: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
}

actual fun getAppVersionInfo(): String {
    return "StageKeysLive v1.0-Desktop"
}
