package com.midi.mainstage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoBackupTest {

    @Test
    fun testMultiConcertSerializationAndDeserialization() {
        val ch1 = ChannelStripState(
            id = 1,
            name = "Piano",
            sf2Name = "YamahaGrand.sf2",
            sf2Path = "soundfonts/YamahaGrand.sf2",
            volume = 0.85f,
            isMuted = false,
            isSoloed = false,
            keyRangeStart = 21,
            keyRangeEnd = 108,
            colorHex = "#38BDF8",
            velocityCurve = "SOFT"
        )
        val snap1 = PatchChannelSnapshot(
            channelId = 1,
            name = "Piano",
            sf2Name = "YamahaGrand.sf2",
            sf2Path = "soundfonts/YamahaGrand.sf2",
            volume = 0.85f,
            isMuted = false,
            isSoloed = false,
            keyRangeStart = 21,
            keyRangeEnd = 108,
            colorHex = "#38BDF8",
            velocityCurve = "SOFT"
        )
        val patch1 = PatchState(
            id = "patch_101",
            name = "Acoustic Piano",
            category = "Piano",
            programNumber = 0,
            description = "Main acoustic piano",
            channelsSnapshot = listOf(snap1)
        )
        val concert1 = Concert(
            id = "concert_1",
            name = "Sunday Worship Concert",
            channels = listOf(ch1),
            patches = listOf(patch1),
            lastModified = 1725200000000L
        )

        val ch2 = ChannelStripState(
            id = 2,
            name = "Warm Pad",
            sf2Name = "AnalogPad.sf2",
            sf2Path = "soundfonts/AnalogPad.sf2",
            volume = 0.70f,
            isMuted = false,
            isSoloed = false,
            keyRangeStart = 36,
            keyRangeEnd = 96,
            colorHex = "#9D4EDD",
            velocityCurve = "HARD"
        )
        val snap2 = PatchChannelSnapshot(
            channelId = 2,
            name = "Warm Pad",
            sf2Name = "AnalogPad.sf2",
            sf2Path = "soundfonts/AnalogPad.sf2",
            volume = 0.70f,
            isMuted = false,
            isSoloed = false,
            keyRangeStart = 36,
            keyRangeEnd = 96,
            colorHex = "#9D4EDD",
            velocityCurve = "HARD"
        )
        val patch2 = PatchState(
            id = "patch_201",
            name = "Pad Atmosphere",
            category = "Pad",
            programNumber = 1,
            description = "Atmospheric synth pad",
            channelsSnapshot = listOf(snap2)
        )
        val concert2 = Concert(
            id = "concert_2",
            name = "Live Set Night",
            channels = listOf(ch2),
            patches = listOf(patch2),
            lastModified = 1725200100000L
        )

        val originalList = listOf(concert1, concert2)

        // Serialize all concerts (multi-concert backup format)
        val jsonStr = ConcertSerializer.serialize(originalList)
        assertTrue(jsonStr.isNotEmpty(), "Serialized JSON should not be empty")

        // Deserialize back
        val restoredList = ConcertSerializer.deserialize(jsonStr)
        assertEquals(2, restoredList.size, "Should restore exactly 2 concerts")

        // Validate Concert 1
        assertEquals("Sunday Worship Concert", restoredList[0].name)
        assertEquals(1, restoredList[0].channels.size)
        assertEquals("soundfonts/YamahaGrand.sf2", restoredList[0].channels[0].sf2Path)
        assertEquals("SOFT", restoredList[0].channels[0].velocityCurve)
        assertEquals(1, restoredList[0].patches.size)
        assertEquals("Acoustic Piano", restoredList[0].patches[0].name)
        assertEquals("SOFT", restoredList[0].patches[0].channelsSnapshot[0].velocityCurve)

        // Validate Concert 2
        assertEquals("Live Set Night", restoredList[1].name)
        assertEquals(1, restoredList[1].channels.size)
        assertEquals("soundfonts/AnalogPad.sf2", restoredList[1].channels[0].sf2Path)
        assertEquals("HARD", restoredList[1].channels[0].velocityCurve)
        assertEquals(1, restoredList[1].patches.size)
        assertEquals("Pad Atmosphere", restoredList[1].patches[0].name)
        assertEquals("HARD", restoredList[1].patches[0].channelsSnapshot[0].velocityCurve)
    }
}
