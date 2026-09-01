package com.midi.mainstage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MidiSMFTest {

    @Test
    fun testEscribeSMFStructure() {
        val events = listOf(
            RecordingEvent(deltaMs = 0L, note = 60, velocity = 90, isNoteOn = true),
            RecordingEvent(deltaMs = 500L, note = 60, velocity = 0, isNoteOn = false),
            RecordingEvent(deltaMs = 1000L, note = 64, velocity = 100, isNoteOn = true),
            RecordingEvent(deltaMs = 1500L, note = 64, velocity = 0, isNoteOn = false)
        )

        val bytes = escribeSMF(events, division = 480, bpm = 120)

        // 1. Verify Header Chunk "MThd"
        assertTrue(bytes.size > 22)
        assertEquals('M'.code.toByte(), bytes[0])
        assertEquals('T'.code.toByte(), bytes[1])
        assertEquals('h'.code.toByte(), bytes[2])
        assertEquals('d'.code.toByte(), bytes[3])

        // Header length: 6
        assertEquals(0, bytes[4].toInt())
        assertEquals(0, bytes[5].toInt())
        assertEquals(0, bytes[6].toInt())
        assertEquals(6, bytes[7].toInt())

        // Format 0 (single track)
        assertEquals(0, bytes[8].toInt())
        assertEquals(0, bytes[9].toInt())

        // Tracks count = 1
        assertEquals(0, bytes[10].toInt())
        assertEquals(1, bytes[11].toInt())

        // Division = 480 (0x01E0)
        assertEquals(0x01, bytes[12].toInt() and 0xFF)
        assertEquals(0xE0, bytes[13].toInt() and 0xFF)

        // 2. Verify Track Chunk "MTrk"
        assertEquals('M'.code.toByte(), bytes[14])
        assertEquals('T'.code.toByte(), bytes[15])
        assertEquals('r'.code.toByte(), bytes[16])
        assertEquals('k'.code.toByte(), bytes[17])

        // Track length
        val trackLen = ((bytes[18].toInt() and 0xFF) shl 24) or
                ((bytes[19].toInt() and 0xFF) shl 16) or
                ((bytes[20].toInt() and 0xFF) shl 8) or
                (bytes[21].toInt() and 0xFF)
        assertEquals(bytes.size - 22, trackLen)

        // 3. Verify End of Track marker at the very end (FF 2F 00)
        val endIdx = bytes.size - 1
        assertEquals(0x00.toByte(), bytes[endIdx])
        assertEquals(0x2F.toByte(), bytes[endIdx - 1])
        assertEquals(0xFF.toByte(), bytes[endIdx - 2])
    }
}
