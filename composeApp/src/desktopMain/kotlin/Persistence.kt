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

actual fun deleteLocalFile(path: String) {
    try {
        val file = java.io.File(path)
        if (file.exists()) {
            file.delete()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
