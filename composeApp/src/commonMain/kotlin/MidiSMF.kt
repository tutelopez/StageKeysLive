package com.midi.mainstage

/**
 * Standard MIDI File (SMF Format 0) encoder.
 * Converts a recorded list of events (with absolute millisecond timestamps)
 * into a valid, playable .mid file.
 */
fun escribeSMF(events: List<RecordingEvent>, division: Int = 480, bpm: Int = 120): ByteArray {
    val trackOut = mutableListOf<Byte>()

    fun writeVarLen(value: Long) {
        var v = value
        val bytes = mutableListOf<Byte>()
        bytes.add((v and 0x7F).toByte())
        v = v ushr 7
        while (v > 0) {
            bytes.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        for (i in bytes.size - 1 downTo 0) {
            trackOut.add(bytes[i])
        }
    }

    // 1. Set Tempo Meta-event (delta-time 0): FF 51 03 tt tt tt (microseconds per quarter note)
    val mpqn = (60_000_000L / bpm.coerceAtLeast(1)).toInt()
    trackOut.add(0x00) // delta-time 0
    trackOut.add(0xFF.toByte())
    trackOut.add(0x51.toByte())
    trackOut.add(0x03.toByte())
    trackOut.add(((mpqn ushr 16) and 0xFF).toByte())
    trackOut.add(((mpqn ushr 8) and 0xFF).toByte())
    trackOut.add((mpqn and 0xFF).toByte())

    // 2. Note On / Note Off events in delta-ticks
    var lastTick = 0L
    events.sortedBy { it.deltaMs }.forEach { ev ->
        val currentTick = (ev.deltaMs * division.toLong() * bpm.toLong()) / 60000L
        val deltaTick = (currentTick - lastTick).coerceAtLeast(0L)
        lastTick = currentTick

        writeVarLen(deltaTick)
        if (ev.isNoteOn && ev.velocity > 0) {
            trackOut.add(0x90.toByte()) // Note On, Channel 0
            trackOut.add(ev.note.coerceIn(0, 127).toByte())
            trackOut.add(ev.velocity.coerceIn(1, 127).toByte())
        } else {
            trackOut.add(0x80.toByte()) // Note Off, Channel 0
            trackOut.add(ev.note.coerceIn(0, 127).toByte())
            trackOut.add(0x00)
        }
    }

    // 3. End of Track Meta-event: delta-time 0, FF 2F 00
    writeVarLen(0L)
    trackOut.add(0xFF.toByte())
    trackOut.add(0x2F.toByte())
    trackOut.add(0x00.toByte())

    val trackBytes = trackOut.toByteArray()

    // 4. Build complete SMF Format 0 file: Header Chunk + Track Chunk
    val fileOut = mutableListOf<Byte>()

    // Header Chunk 'MThd'
    fileOut.add('M'.code.toByte())
    fileOut.add('T'.code.toByte())
    fileOut.add('h'.code.toByte())
    fileOut.add('d'.code.toByte())
    // Header data length = 6
    fileOut.add(0x00)
    fileOut.add(0x00)
    fileOut.add(0x00)
    fileOut.add(0x06)
    // Format 0 (single track)
    fileOut.add(0x00)
    fileOut.add(0x00)
    // Number of tracks = 1
    fileOut.add(0x00)
    fileOut.add(0x01)
    // Division (ticks per quarter note)
    fileOut.add(((division ushr 8) and 0xFF).toByte())
    fileOut.add((division and 0xFF).toByte())

    // Track Chunk 'MTrk'
    fileOut.add('M'.code.toByte())
    fileOut.add('T'.code.toByte())
    fileOut.add('r'.code.toByte())
    fileOut.add('k'.code.toByte())
    // Track data length
    val trackLen = trackBytes.size
    fileOut.add(((trackLen ushr 24) and 0xFF).toByte())
    fileOut.add(((trackLen ushr 16) and 0xFF).toByte())
    fileOut.add(((trackLen ushr 8) and 0xFF).toByte())
    fileOut.add((trackLen and 0xFF).toByte())
    // Track data bytes
    trackBytes.forEach { fileOut.add(it) }

    return fileOut.toByteArray()
}
