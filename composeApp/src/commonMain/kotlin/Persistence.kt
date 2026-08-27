package com.midi.mainstage

expect fun saveTextToFile(filename: String, text: String)
expect fun readTextFromFile(filename: String): String?
expect fun deleteLocalFile(path: String)

data class ChannelStripState(
    val id: Int,
    val sf2Name: String,
    val sf2Path: String?,
    val volume: Float,
    val isMuted: Boolean,
    val isSoloed: Boolean,
    val keyRangeStart: Int,
    val keyRangeEnd: Int,
    val colorHex: String
)

data class PatchChannelSnapshot(
    val channelId: Int,
    val sf2Name: String,
    val sf2Path: String?,
    val volume: Float,
    val isMuted: Boolean,
    val isSoloed: Boolean,
    val keyRangeStart: Int,
    val keyRangeEnd: Int,
    val colorHex: String
)

data class PatchState(
    val name: String,
    val category: String,
    val programNumber: Int,
    val description: String,
    val id: String = "patch_${System.currentTimeMillis()}_${(0..1000).random()}",
    val channelsSnapshot: List<PatchChannelSnapshot> = emptyList()
)

data class Concert(
    val id: String,
    val name: String,
    val lastModified: Long,
    val patches: List<PatchState>,
    val channels: List<ChannelStripState>
)

// Custom simple JSON serializer to avoid libraries version incompatibilities
object ConcertSerializer {
    fun serialize(concerts: List<Concert>): String {
        val sb = StringBuilder()
        sb.append("[")
        concerts.forEachIndexed { i, concert ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"${concert.id}\",")
            sb.append("\"name\":\"${escape(concert.name)}\",")
            sb.append("\"lastModified\":${concert.lastModified},")
            
            // Serialize patches
            sb.append("\"patches\":[")
            concert.patches.forEachIndexed { j, patch ->
                if (j > 0) sb.append(",")
                sb.append("{")
                sb.append("\"id\":\"${patch.id}\",")
                sb.append("\"name\":\"${escape(patch.name)}\",")
                sb.append("\"category\":\"${escape(patch.category)}\",")
                sb.append("\"programNumber\":${patch.programNumber},")
                sb.append("\"description\":\"${escape(patch.description)}\",")
                sb.append("\"channelsSnapshot\":[")
                patch.channelsSnapshot.forEachIndexed { s, snap ->
                    if (s > 0) sb.append(",")
                    sb.append("{")
                    sb.append("\"channelId\":${snap.channelId},")
                    sb.append("\"sf2Name\":\"${escape(snap.sf2Name)}\",")
                    if (snap.sf2Path != null) {
                        sb.append("\"sf2Path\":\"${escape(snap.sf2Path)}\",")
                    }
                    sb.append("\"volume\":${snap.volume},")
                    sb.append("\"isMuted\":${snap.isMuted},")
                    sb.append("\"isSoloed\":${snap.isSoloed},")
                    sb.append("\"keyRangeStart\":${snap.keyRangeStart},")
                    sb.append("\"keyRangeEnd\":${snap.keyRangeEnd},")
                    sb.append("\"colorHex\":\"${snap.colorHex}\"")
                    sb.append("}")
                }
                sb.append("]")
                sb.append("}")
            }
            sb.append("],")
            
            // Serialize channels
            sb.append("\"channels\":[")
            concert.channels.forEachIndexed { k, ch ->
                if (k > 0) sb.append(",")
                sb.append("{")
                sb.append("\"id\":${ch.id},")
                sb.append("\"sf2Name\":\"${escape(ch.sf2Name)}\",")
                if (ch.sf2Path != null) {
                    sb.append("\"sf2Path\":\"${escape(ch.sf2Path)}\",")
                }
                sb.append("\"volume\":${ch.volume},")
                sb.append("\"isMuted\":${ch.isMuted},")
                sb.append("\"isSoloed\":${ch.isSoloed},")
                sb.append("\"keyRangeStart\":${ch.keyRangeStart},")
                sb.append("\"keyRangeEnd\":${ch.keyRangeEnd},")
                sb.append("\"colorHex\":\"${ch.colorHex}\"")
                sb.append("}")
            }
            sb.append("]")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\"", "\\\"").replace("\n", "\\n")

    fun deserialize(json: String): List<Concert> {
        try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return emptyList()
            val inner = trimmed.substring(1, trimmed.length - 1).trim()
            if (inner.isEmpty()) return emptyList()
            
            val parser = SimpleJsonParser(inner)
            return parser.parseConcerts()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return emptyList()
    }
}

class SimpleJsonParser(private val src: String) {
    private var pos = 0

    fun parseConcerts(): List<Concert> {
        val list = mutableListOf<Concert>()
        while (pos < src.length) {
            skipWhitespace()
            if (pos >= src.length) break
            if (src[pos] == '{') {
                list.add(parseConcert())
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') {
                pos++ // Skip comma
            }
        }
        return list
    }

    private fun parseConcert(): Concert {
        pos++ // skip '{'
        var id = ""
        var name = ""
        var lastModified = 0L
        val patches = mutableListOf<PatchState>()
        val channels = mutableListOf<ChannelStripState>()

        while (pos < src.length) {
            skipWhitespace()
            if (pos >= src.length) break
            if (src[pos] == '}') {
                pos++ // skip '}'
                break
            }
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++ // skip ':'
            skipWhitespace()
            when (key) {
                "id" -> id = parseString()
                "name" -> name = parseString()
                "lastModified" -> lastModified = parseLong()
                "patches" -> {
                    if (pos < src.length && src[pos] == '[') pos++ // skip '['
                    while (pos < src.length) {
                        skipWhitespace()
                        if (src[pos] == ']') {
                            pos++
                            break
                        }
                        if (src[pos] == '{') {
                            patches.add(parsePatch())
                        }
                        skipWhitespace()
                        if (pos < src.length && src[pos] == ',') pos++
                    }
                }
                "channels" -> {
                    if (pos < src.length && src[pos] == '[') pos++ // skip '['
                    while (pos < src.length) {
                        skipWhitespace()
                        if (src[pos] == ']') {
                            pos++
                            break
                        }
                        if (src[pos] == '{') {
                            channels.add(parseChannel())
                        }
                        skipWhitespace()
                        if (pos < src.length && src[pos] == ',') pos++
                    }
                }
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return Concert(id, name, lastModified, patches, channels)
    }

    private fun parsePatch(): PatchState {
        pos++ // skip '{'
        var id = ""
        var name = ""
        var category = ""
        var programNumber = 0
        var description = ""
        val channelsSnapshot = mutableListOf<PatchChannelSnapshot>()

        while (pos < src.length) {
            skipWhitespace()
            if (src[pos] == '}') {
                pos++
                break
            }
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++
            skipWhitespace()
            when (key) {
                "id" -> id = parseString()
                "name" -> name = parseString()
                "category" -> category = parseString()
                "programNumber" -> programNumber = parseInt()
                "description" -> description = parseString()
                "channelsSnapshot" -> {
                    if (pos < src.length && src[pos] == '[') pos++ // skip '['
                    while (pos < src.length) {
                        skipWhitespace()
                        if (src[pos] == ']') {
                            pos++
                            break
                        }
                        if (src[pos] == '{') {
                            channelsSnapshot.add(parsePatchChannelSnapshot())
                        }
                        skipWhitespace()
                        if (pos < src.length && src[pos] == ',') pos++
                    }
                }
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        if (id.isEmpty()) id = "patch_${System.currentTimeMillis()}_${(0..1000).random()}" // Fallback for old saves
        return PatchState(name, category, programNumber, description, id, channelsSnapshot)
    }

    private fun parsePatchChannelSnapshot(): PatchChannelSnapshot {
        pos++ // skip '{'
        var channelId = 0
        var sf2Name = ""
        var sf2Path: String? = null
        var volume = 1f
        var isMuted = false
        var isSoloed = false
        var keyRangeStart = 0
        var keyRangeEnd = 127
        var colorHex = "#00D2FF"

        while (pos < src.length) {
            skipWhitespace()
            if (src[pos] == '}') {
                pos++
                break
            }
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++
            skipWhitespace()
            when (key) {
                "channelId" -> channelId = parseInt()
                "sf2Name" -> sf2Name = parseString()
                "sf2Path" -> sf2Path = parseString()
                "volume" -> volume = parseFloat()
                "isMuted" -> isMuted = parseBoolean()
                "isSoloed" -> isSoloed = parseBoolean()
                "keyRangeStart" -> keyRangeStart = parseInt()
                "keyRangeEnd" -> keyRangeEnd = parseInt()
                "colorHex" -> colorHex = parseString()
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return PatchChannelSnapshot(channelId, sf2Name, sf2Path, volume, isMuted, isSoloed, keyRangeStart, keyRangeEnd, colorHex)
    }

    private fun parseChannel(): ChannelStripState {
        pos++ // skip '{'
        var id = 0
        var sf2Name = ""
        var sf2Path: String? = null
        var volume = 1f
        var isMuted = false
        var isSoloed = false
        var keyRangeStart = 0
        var keyRangeEnd = 127
        var colorHex = "#00D2FF"

        while (pos < src.length) {
            skipWhitespace()
            if (src[pos] == '}') {
                pos++
                break
            }
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++
            skipWhitespace()
            when (key) {
                "id" -> id = parseInt()
                "sf2Name" -> sf2Name = parseString()
                "sf2Path" -> sf2Path = parseString()
                "volume" -> volume = parseFloat()
                "isMuted" -> isMuted = parseBoolean()
                "isSoloed" -> isSoloed = parseBoolean()
                "keyRangeStart" -> keyRangeStart = parseInt()
                "keyRangeEnd" -> keyRangeEnd = parseInt()
                "colorHex" -> colorHex = parseString()
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return ChannelStripState(id, sf2Name, sf2Path, volume, isMuted, isSoloed, keyRangeStart, keyRangeEnd, colorHex)
    }

    private fun parseString(): String {
        skipWhitespace()
        if (pos < src.length && src[pos] == '"') pos++
        val sb = StringBuilder()
        while (pos < src.length) {
            val c = src[pos]
            if (c == '"') {
                pos++
                break
            }
            if (c == '\\' && pos + 1 < src.length) {
                pos++
                val next = src[pos]
                if (next == 'n') sb.append('\n')
                else sb.append(next)
            } else {
                sb.append(c)
            }
            pos++
        }
        return sb.toString()
    }

    private fun parseLong(): Long {
        skipWhitespace()
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '-')) {
            pos++
        }
        return src.substring(start, pos).toLongOrNull() ?: 0L
    }

    private fun parseInt(): Int {
        skipWhitespace()
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '-')) {
            pos++
        }
        return src.substring(start, pos).toIntOrNull() ?: 0
    }

    private fun parseFloat(): Float {
        skipWhitespace()
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '-' || src[pos] == '.')) {
            pos++
        }
        return src.substring(start, pos).toFloatOrNull() ?: 0f
    }

    private fun parseBoolean(): Boolean {
        skipWhitespace()
        if (src.startsWith("true", pos)) {
            pos += 4
            return true
        }
        if (src.startsWith("false", pos)) {
            pos += 5
            return false
        }
        return false
    }

    private fun skipValue() {
        skipWhitespace()
        if (pos >= src.length) return
        val c = src[pos]
        if (c == '"') {
            parseString()
        } else if (c == '{') {
            var braces = 1
            pos++
            while (pos < src.length && braces > 0) {
                if (src[pos] == '{') braces++
                if (src[pos] == '}') braces--
                pos++
            }
        } else if (c == '[') {
            var brackets = 1
            pos++
            while (pos < src.length && brackets > 0) {
                if (src[pos] == '[') brackets++
                if (src[pos] == ']') brackets--
                pos++
            }
        } else {
            while (pos < src.length && src[pos] != ',' && src[pos] != '}' && src[pos] != ']') {
                pos++
            }
        }
    }

    private fun skipWhitespace() {
        while (pos < src.length && src[pos].isWhitespace()) {
            pos++
        }
    }
}

object MidiMappingSerializer {
    fun serialize(mappings: Map<Int, MidiTarget>): String {
        return mappings.entries.joinToString(",") { "${it.key}:${serializeTarget(it.value)}" }
    }
    
    fun deserialize(str: String): Map<Int, MidiTarget> {
        val map = mutableMapOf<Int, MidiTarget>()
        if (str.isBlank()) return map
        str.split(",").forEach { pair ->
            val parts = pair.split(":")
            if (parts.size == 2) {
                val cc = parts[0].toIntOrNull()
                val target = deserializeTarget(parts[1])
                if (cc != null && target != null) {
                    map[cc] = target
                }
            }
        }
        return map
    }
    
    private fun serializeTarget(target: MidiTarget): String {
        return when (target) {
            is MidiTarget.ChannelVolume -> "ChannelVolume-${target.channelIndex}"
            is MidiTarget.ChannelMute -> "ChannelMute-${target.channelIndex}"
            is MidiTarget.ChannelSolo -> "ChannelSolo-${target.channelIndex}"
            is MidiTarget.Pad -> "Pad-${target.padIndex}"
            is MidiTarget.Pot -> "Pot-${target.potIndex}"
            is MidiTarget.MasterVolume -> "MasterVolume"
            is MidiTarget.FilterCutoff -> "FilterCutoff"
            is MidiTarget.ReverbMix -> "ReverbMix"
            is MidiTarget.Sustain -> "Sustain"
            is MidiTarget.Modulation -> "Modulation"
            is MidiTarget.OctaveUp -> "OctaveUp"
            is MidiTarget.OctaveDown -> "OctaveDown"
            is MidiTarget.NextPatch -> "NextPatch"
            is MidiTarget.PreviousPatch -> "PreviousPatch"
        }
    }
    
    private fun deserializeTarget(str: String): MidiTarget? {
        val parts = str.split("-")
        return when (parts[0]) {
            "ChannelVolume" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelVolume(it) }
            "ChannelMute" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelMute(it) }
            "ChannelSolo" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelSolo(it) }
            "Pad" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.Pad(it) }
            "Pot" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.Pot(it) }
            "MasterVolume" -> MidiTarget.MasterVolume
            "FilterCutoff" -> MidiTarget.FilterCutoff
            "ReverbMix" -> MidiTarget.ReverbMix
            "Sustain" -> MidiTarget.Sustain
            "Modulation" -> MidiTarget.Modulation
            "OctaveUp" -> MidiTarget.OctaveUp
            "OctaveDown" -> MidiTarget.OctaveDown
            "NextPatch" -> MidiTarget.NextPatch
            "PreviousPatch" -> MidiTarget.PreviousPatch
            else -> null
        }
    }
}




