package com.midi.mainstage

import java.io.File

private var androidBaseDir: File? = null

fun setAndroidBaseDir(dir: File) {
    androidBaseDir = dir
}

actual fun saveTextToFile(filename: String, text: String) {
    try {
        val dir = androidBaseDir ?: File("/sdcard")
        val file = File(dir, filename)
        file.writeText(text)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun readTextFromFile(filename: String): String? {
    try {
        val dir = androidBaseDir ?: File("/sdcard")
        val file = File(dir, filename)
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
