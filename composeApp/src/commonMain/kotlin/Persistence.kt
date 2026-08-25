package com.midi.mainstage

expect fun saveTextToFile(filename: String, text: String)
expect fun readTextFromFile(filename: String): String?

data class ChannelStripState(
    val id: Int,
    val sf2Name: String,
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
    val description: String
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
                sb.append("\"name\":\"${escape(patch.name)}\",")
                sb.append("\"category\":\"${escape(patch.category)}\",")
                sb.append("\"programNumber\":${patch.programNumber},")
                sb.append("\"description\":\"${escape(patch.description)}\"")
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
        var name = ""
        var category = ""
        var programNumber = 0
        var description = ""

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
                "name" -> name = parseString()
                "category" -> category = parseString()
                "programNumber" -> programNumber = parseInt()
                "description" -> description = parseString()
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return PatchState(name, category, programNumber, description)
    }

    private fun parseChannel(): ChannelStripState {
        pos++ // skip '{'
        var id = 0
        var sf2Name = ""
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
        return ChannelStripState(id, sf2Name, volume, isMuted, isSoloed, keyRangeStart, keyRangeEnd, colorHex)
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
