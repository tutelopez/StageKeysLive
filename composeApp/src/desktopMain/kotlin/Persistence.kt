package com.midi.mainstage

import java.io.File

actual fun saveTextToFile(filename: String, text: String) {
    try {
        val file = File(filename)
        file.writeText(text)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readTextFromFile(filename: String): String? {
    try {
        val file = File(filename)
        if (file.exists()) {
            return file.readText()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
