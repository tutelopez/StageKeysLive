package com.tutelopezmusic.stagekeyslive

import java.io.File

actual object CrashReporter {
    private val logFile: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val dir = File(userHome, ".stagekeys")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "telemetry.log")
    }

    actual fun log(message: String) {
        val line = "[INFO ${System.currentTimeMillis()}] $message"
        println(line)
        try {
            logFile.appendText("$line\n")
        } catch (_: Throwable) {}
    }

    actual fun recordException(throwable: Throwable, tag: String?) {
        val line = "[ERROR ${System.currentTimeMillis()}] [${tag ?: "General"}] ${throwable.message}"
        System.err.println(line)
        throwable.printStackTrace()
        try {
            logFile.appendText("$line\n${throwable.stackTraceToString()}\n")
        } catch (_: Throwable) {}
    }

    actual fun setCustomKey(key: String, value: String) {
        log("KEY: $key = $value")
    }

    actual fun setCustomKey(key: String, value: Boolean) {
        log("KEY: $key = $value")
    }

    actual fun setCustomKey(key: String, value: Int) {
        log("KEY: $key = $value")
    }

    actual fun setUserId(userId: String) {
        log("USER_ID: $userId")
    }
}
