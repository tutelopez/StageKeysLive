@file:JvmName("CommonPersistence")

package com.tutelopezmusic.stagekeyslive

import kotlin.jvm.JvmName

expect fun saveTextToFile(filename: String, text: String)
expect fun readTextFromFile(filename: String): String?
expect fun deleteLocalFile(path: String)

data class ChannelStripState(
    val id: Int,
    val name: String,
    val sf2Name: String,
    val sf2Path: String?,
    val volume: Float,
    val isMuted: Boolean,
    val isSoloed: Boolean,
    val keyRangeStart: Int,
    val keyRangeEnd: Int,
    val colorHex: String,
    val velocityCurve: String = "LINEAR",
    val pan: Float = 0.5f,
    val reverbSend: Float = 0.2f,
    val chorusSend: Float = 0.0f,
    val filterCutoff: Float = 1.0f
)

data class PatchChannelSnapshot(
    val channelId: Int,
    val name: String,
    val sf2Name: String,
    val sf2Path: String?,
    val volume: Float,
    val isMuted: Boolean,
    val isSoloed: Boolean,
    val keyRangeStart: Int,
    val keyRangeEnd: Int,
    val colorHex: String,
    val velocityCurve: String = "LINEAR",
    val pan: Float = 0.5f,
    val reverbSend: Float = 0.2f,
    val chorusSend: Float = 0.0f,
    val filterCutoff: Float = 1.0f
)

fun applyCurve(velocity: Int, curve: String): Int {
    if (velocity <= 0) return 0
    if (velocity >= 127) return 127
    val norm = velocity / 127.0
    val transformed = when (curve.uppercase()) {
        "SOFT" -> kotlin.math.sqrt(norm)
        "HARD" -> norm * norm
        else -> norm // LINEAR
    }
    return kotlin.math.round(transformed * 127.0).toInt().coerceIn(0, 127)
}

data class PatchState(
    val name: String,
    val category: String,
    val programNumber: Int,
    val description: String,
    val transposeSemitones: Int = 0,
    val isFavorite: Boolean = false,
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
                sb.append("\"transposeSemitones\":${patch.transposeSemitones},")
                sb.append("\"isFavorite\":${patch.isFavorite},")
                sb.append("\"channelsSnapshot\":[")
                patch.channelsSnapshot.forEachIndexed { s, snap ->
                    if (s > 0) sb.append(",")
                    sb.append("{")
                    sb.append("\"channelId\":${snap.channelId},")
                    sb.append("\"name\":\"${escape(snap.name)}\",")
                    sb.append("\"sf2Name\":\"${escape(snap.sf2Name)}\",")
                    if (snap.sf2Path != null) {
                        sb.append("\"sf2Path\":\"${escape(snap.sf2Path)}\",")
                    }
                    sb.append("\"volume\":${snap.volume},")
                    sb.append("\"isMuted\":${snap.isMuted},")
                    sb.append("\"isSoloed\":${snap.isSoloed},")
                    sb.append("\"keyRangeStart\":${snap.keyRangeStart},")
                    sb.append("\"keyRangeEnd\":${snap.keyRangeEnd},")
                    sb.append("\"colorHex\":\"${snap.colorHex}\",")
                    sb.append("\"velocityCurve\":\"${snap.velocityCurve}\",")
                    sb.append("\"pan\":${snap.pan},")
                    sb.append("\"reverbSend\":${snap.reverbSend},")
                    sb.append("\"chorusSend\":${snap.chorusSend},")
                    sb.append("\"filterCutoff\":${snap.filterCutoff}")
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
                sb.append("\"name\":\"${escape(ch.name)}\",")
                sb.append("\"sf2Name\":\"${escape(ch.sf2Name)}\",")
                if (ch.sf2Path != null) {
                    sb.append("\"sf2Path\":\"${escape(ch.sf2Path)}\",")
                }
                sb.append("\"volume\":${ch.volume},")
                sb.append("\"isMuted\":${ch.isMuted},")
                sb.append("\"isSoloed\":${ch.isSoloed},")
                sb.append("\"keyRangeStart\":${ch.keyRangeStart},")
                sb.append("\"keyRangeEnd\":${ch.keyRangeEnd},")
                sb.append("\"colorHex\":\"${ch.colorHex}\",")
                sb.append("\"velocityCurve\":\"${ch.velocityCurve}\",")
                sb.append("\"pan\":${ch.pan},")
                sb.append("\"reverbSend\":${ch.reverbSend},")
                sb.append("\"chorusSend\":${ch.chorusSend},")
                sb.append("\"filterCutoff\":${ch.filterCutoff}")
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

object SinglePatchSerializer {
    fun serialize(patch: PatchState): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"type\":\"stagekeys_single_patch\",")
        sb.append("\"version\":\"1.0\",")
        sb.append("\"patch\":{")
        sb.append("\"id\":\"${patch.id}\",")
        sb.append("\"name\":\"${escape(patch.name)}\",")
        sb.append("\"category\":\"${escape(patch.category)}\",")
        sb.append("\"programNumber\":${patch.programNumber},")
        sb.append("\"description\":\"${escape(patch.description)}\",")
        sb.append("\"transposeSemitones\":${patch.transposeSemitones},")
        sb.append("\"isFavorite\":${patch.isFavorite},")
        sb.append("\"channelsSnapshot\":[")
        patch.channelsSnapshot.forEachIndexed { s, snap ->
            if (s > 0) sb.append(",")
            sb.append("{")
            sb.append("\"channelId\":${snap.channelId},")
            sb.append("\"name\":\"${escape(snap.name)}\",")
            sb.append("\"sf2Name\":\"${escape(snap.sf2Name)}\",")
            if (snap.sf2Path != null) {
                sb.append("\"sf2Path\":\"${escape(snap.sf2Path)}\",")
            }
            sb.append("\"volume\":${snap.volume},")
            sb.append("\"isMuted\":${snap.isMuted},")
            sb.append("\"isSoloed\":${snap.isSoloed},")
            sb.append("\"keyRangeStart\":${snap.keyRangeStart},")
            sb.append("\"keyRangeEnd\":${snap.keyRangeEnd},")
            sb.append("\"colorHex\":\"${snap.colorHex}\",")
            sb.append("\"velocityCurve\":\"${snap.velocityCurve}\",")
            sb.append("\"pan\":${snap.pan},")
            sb.append("\"reverbSend\":${snap.reverbSend},")
            sb.append("\"chorusSend\":${snap.chorusSend},")
            sb.append("\"filterCutoff\":${snap.filterCutoff}")
            sb.append("}")
        }
        sb.append("]")
        sb.append("}")
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\"", "\\\"").replace("\n", "\\n")

    fun deserialize(json: String): Result<PatchState> {
        return try {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return Result.failure(IllegalArgumentException("El archivo no tiene formato JSON válido."))
            }
            val parser = SimpleJsonParser(trimmed)
            val patch = parser.parseSinglePatchEnvelope()
            if (patch == null || patch.name.isBlank()) {
                Result.failure(IllegalArgumentException("El archivo JSON no contiene un patch válido de StageKeysLive."))
            } else {
                val safePatch = patch.copy(
                    id = "patch_${System.currentTimeMillis()}_${(100..999).random()}"
                )
                Result.success(safePatch)
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Estructura de patch inválida: ${e.message}"))
        }
    }
}

class SimpleJsonParser(private val src: String) {
    private var pos = 0

    fun parseSinglePatchEnvelope(): PatchState? {
        skipWhitespace()
        if (pos >= src.length || src[pos] != '{') return null
        val mark = pos
        pos++ // skip '{'
        var foundPatch: PatchState? = null
        var isEnvelope = false

        while (pos < src.length) {
            skipWhitespace()
            if (pos >= src.length || src[pos] == '}') {
                if (pos < src.length) pos++
                break
            }
            val key = parseString()
            skipWhitespace()
            if (pos < src.length && src[pos] == ':') pos++
            skipWhitespace()
            if (key == "patch" && pos < src.length && src[pos] == '{') {
                foundPatch = parsePatch()
                isEnvelope = true
            } else {
                skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }

        if (isEnvelope && foundPatch != null) {
            return foundPatch
        }

        pos = mark
        return try {
            parsePatch()
        } catch (_: Exception) {
            null
        }
    }

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

    fun parseActiveSessionSnapshot(): ActiveSessionSnapshot {
        skipWhitespace()
        if (pos < src.length && src[pos] == '{') pos++ // skip '{'
        var isSessionActive = false
        var concertId = ""
        var selectedPatchIndex = 0
        var timestamp = 0L
        var masterVolume = 0.8f
        var masterPan = 0.5f
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
                "isSessionActive" -> isSessionActive = parseBoolean()
                "concertId" -> concertId = parseString()
                "selectedPatchIndex" -> selectedPatchIndex = parseInt()
                "timestamp" -> timestamp = parseLong()
                "masterVolume" -> masterVolume = parseFloat()
                "masterPan" -> masterPan = parseFloat()
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
        return ActiveSessionSnapshot(isSessionActive, concertId, selectedPatchIndex, timestamp, masterVolume, masterPan, channels)
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
        var transposeSemitones = 0
        var isFavorite = false
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
                "transposeSemitones" -> transposeSemitones = parseInt()
                "isFavorite" -> isFavorite = parseBoolean()
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
        return PatchState(name, category, programNumber, description, transposeSemitones, isFavorite, id, channelsSnapshot)
    }

    private fun parsePatchChannelSnapshot(): PatchChannelSnapshot {
        pos++ // skip '{'
        var channelId = 0
        var name: String? = null
        var sf2Name = ""
        var sf2Path: String? = null
        var volume = 1f
        var isMuted = false
        var isSoloed = false
        var keyRangeStart = 0
        var keyRangeEnd = 127
        var colorHex = "#00D2FF"
        var velocityCurve = "LINEAR"
        var pan = 0.5f
        var reverbSend = 0.2f
        var chorusSend = 0.0f
        var filterCutoff = 1.0f

        while (pos < src.length) {
            skipWhitespace()
            if (pos >= src.length) break
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
                "name" -> name = parseString()
                "sf2Name" -> sf2Name = parseString()
                "sf2Path" -> sf2Path = parseString()
                "volume" -> volume = parseFloat()
                "isMuted" -> isMuted = parseBoolean()
                "isSoloed" -> isSoloed = parseBoolean()
                "keyRangeStart" -> keyRangeStart = parseInt()
                "keyRangeEnd" -> keyRangeEnd = parseInt()
                "colorHex" -> colorHex = parseString()
                "velocityCurve" -> velocityCurve = parseString()
                "pan" -> pan = parseFloat()
                "reverbSend" -> reverbSend = parseFloat()
                "chorusSend" -> chorusSend = parseFloat()
                "filterCutoff" -> filterCutoff = parseFloat()
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return PatchChannelSnapshot(channelId, name ?: "Canal $channelId", sf2Name, sf2Path, volume, isMuted, isSoloed, keyRangeStart, keyRangeEnd, colorHex, velocityCurve, pan, reverbSend, chorusSend, filterCutoff)
    }

    private fun parseChannel(): ChannelStripState {
        pos++ // skip '{'
        var id = 0
        var name: String? = null
        var sf2Name = ""
        var sf2Path: String? = null
        var volume = 1f
        var isMuted = false
        var isSoloed = false
        var keyRangeStart = 0
        var keyRangeEnd = 127
        var colorHex = "#00D2FF"
        var velocityCurve = "LINEAR"
        var pan = 0.5f
        var reverbSend = 0.2f
        var chorusSend = 0.0f
        var filterCutoff = 1.0f

        while (pos < src.length) {
            skipWhitespace()
            if (pos >= src.length) break
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
                "name" -> name = parseString()
                "sf2Name" -> sf2Name = parseString()
                "sf2Path" -> sf2Path = parseString()
                "volume" -> volume = parseFloat()
                "isMuted" -> isMuted = parseBoolean()
                "isSoloed" -> isSoloed = parseBoolean()
                "keyRangeStart" -> keyRangeStart = parseInt()
                "keyRangeEnd" -> keyRangeEnd = parseInt()
                "colorHex" -> colorHex = parseString()
                "velocityCurve" -> velocityCurve = parseString()
                "pan" -> pan = parseFloat()
                "reverbSend" -> reverbSend = parseFloat()
                "chorusSend" -> chorusSend = parseFloat()
                "filterCutoff" -> filterCutoff = parseFloat()
                else -> skipValue()
            }
            skipWhitespace()
            if (pos < src.length && src[pos] == ',') pos++
        }
        return ChannelStripState(id, name ?: "Canal $id", sf2Name, sf2Path, volume, isMuted, isSoloed, keyRangeStart, keyRangeEnd, colorHex, velocityCurve, pan, reverbSend, chorusSend, filterCutoff)
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
            is MidiTarget.ChannelReverb -> "ChannelReverb-${target.channelIndex}"
            is MidiTarget.ChannelChorus -> "ChannelChorus-${target.channelIndex}"
            is MidiTarget.ChannelCutoff -> "ChannelCutoff-${target.channelIndex}"
            is MidiTarget.Pad -> "Pad-${target.padIndex}"
            is MidiTarget.Pot -> "Pot-${target.potIndex}"
            is MidiTarget.PadNoteToggle -> "PadNoteToggle-${target.pitchClass}"
            is MidiTarget.PadEnable -> "PadEnable"
            is MidiTarget.MasterVolume -> "MasterVolume"
            is MidiTarget.FilterCutoff -> "FilterCutoff"
            is MidiTarget.ReverbMix -> "ReverbMix"
            is MidiTarget.Sustain -> "Sustain"
            is MidiTarget.Modulation -> "Modulation"
            is MidiTarget.OctaveUp -> "OctaveUp"
            is MidiTarget.OctaveDown -> "OctaveDown"
            is MidiTarget.NextPatch -> "NextPatch"
            is MidiTarget.PreviousPatch -> "PreviousPatch"
            is MidiTarget.SelectPatch -> "SelectPatch-${target.patchIndex}"
        }
    }
    
    private fun deserializeTarget(str: String): MidiTarget? {
        val parts = str.split("-")
        return when (parts[0]) {
            "ChannelVolume" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelVolume(it) }
            "ChannelMute" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelMute(it) }
            "ChannelSolo" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelSolo(it) }
            "ChannelReverb" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelReverb(it) }
            "ChannelChorus" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelChorus(it) }
            "ChannelCutoff" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.ChannelCutoff(it) }
            "Pad" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.Pad(it) }
            "Pot" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.Pot(it) }
            "PadNoteToggle" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.PadNoteToggle(it) }
            "PadEnable" -> MidiTarget.PadEnable
            "MasterVolume" -> MidiTarget.MasterVolume
            "FilterCutoff" -> MidiTarget.FilterCutoff
            "ReverbMix" -> MidiTarget.ReverbMix
            "Sustain" -> MidiTarget.Sustain
            "Modulation" -> MidiTarget.Modulation
            "OctaveUp" -> MidiTarget.OctaveUp
            "OctaveDown" -> MidiTarget.OctaveDown
            "NextPatch" -> MidiTarget.NextPatch
            "PreviousPatch" -> MidiTarget.PreviousPatch
            "SelectPatch" -> parts.getOrNull(1)?.toIntOrNull()?.let { MidiTarget.SelectPatch(it) }
            else -> null
        }
    }
}

data class MasterFxSettings(
    val reverbRoomSize: Float = 0.7f,
    val reverbDamping: Float = 0.5f,
    val reverbWidth: Float = 0.6f,
    val reverbLevel: Float = 0.8f,
    val chorusNr: Int = 3,
    val chorusDepth: Float = 8.0f,
    val chorusSpeed: Float = 0.3f,
    val chorusLevel: Float = 0.8f
)

object MasterFxSerializer {
    fun serialize(settings: MasterFxSettings): String {
        return "{\"roomSize\":${settings.reverbRoomSize},\"damping\":${settings.reverbDamping},\"width\":${settings.reverbWidth},\"reverbLevel\":${settings.reverbLevel},\"chorusNr\":${settings.chorusNr},\"chorusDepth\":${settings.chorusDepth},\"chorusSpeed\":${settings.chorusSpeed},\"chorusLevel\":${settings.chorusLevel}}"
    }

    fun deserialize(json: String?): MasterFxSettings {
        if (json.isNullOrBlank()) return MasterFxSettings()
        var roomSize = 0.7f
        var damping = 0.5f
        var width = 0.6f
        var reverbLevel = 0.8f
        var chorusNr = 3
        var chorusDepth = 8.0f
        var chorusSpeed = 0.3f
        var chorusLevel = 0.8f

        try {
            val trimmed = json.trim().removeSurrounding("{", "}")
            trimmed.split(",").forEach { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val k = parts[0].trim().removeSurrounding("\"")
                    val v = parts[1].trim()
                    when (k) {
                        "roomSize" -> roomSize = v.toFloatOrNull() ?: roomSize
                        "damping" -> damping = v.toFloatOrNull() ?: damping
                        "width" -> width = v.toFloatOrNull() ?: width
                        "reverbLevel" -> reverbLevel = v.toFloatOrNull() ?: reverbLevel
                        "chorusNr" -> chorusNr = v.toIntOrNull() ?: chorusNr
                        "chorusDepth" -> chorusDepth = v.toFloatOrNull() ?: chorusDepth
                        "chorusSpeed" -> chorusSpeed = v.toFloatOrNull() ?: chorusSpeed
                        "chorusLevel" -> chorusLevel = v.toFloatOrNull() ?: chorusLevel
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return MasterFxSettings(roomSize, damping, width, reverbLevel, chorusNr, chorusDepth, chorusSpeed, chorusLevel)
    }
}

data class ActiveSessionSnapshot(
    val isSessionActive: Boolean = false,
    val concertId: String = "",
    val selectedPatchIndex: Int = 0,
    val timestamp: Long = 0L,
    val masterVolume: Float = 0.8f,
    val masterPan: Float = 0.5f,
    val channels: List<ChannelStripState> = emptyList()
)

object SessionSnapshotSerializer {
    fun serialize(snapshot: ActiveSessionSnapshot): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"isSessionActive\":${snapshot.isSessionActive},")
        sb.append("\"concertId\":\"${escape(snapshot.concertId)}\",")
        sb.append("\"selectedPatchIndex\":${snapshot.selectedPatchIndex},")
        sb.append("\"timestamp\":${snapshot.timestamp},")
        sb.append("\"masterVolume\":${snapshot.masterVolume},")
        sb.append("\"masterPan\":${snapshot.masterPan},")
        sb.append("\"channels\":[")
        snapshot.channels.forEachIndexed { i, ch ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":${ch.id},")
            sb.append("\"name\":\"${escape(ch.name)}\",")
            sb.append("\"sf2Name\":\"${escape(ch.sf2Name)}\",")
            if (ch.sf2Path != null) {
                sb.append("\"sf2Path\":\"${escape(ch.sf2Path)}\",")
            }
            sb.append("\"volume\":${ch.volume},")
            sb.append("\"isMuted\":${ch.isMuted},")
            sb.append("\"isSoloed\":${ch.isSoloed},")
            sb.append("\"keyRangeStart\":${ch.keyRangeStart},")
            sb.append("\"keyRangeEnd\":${ch.keyRangeEnd},")
            sb.append("\"colorHex\":\"${ch.colorHex}\",")
            sb.append("\"velocityCurve\":\"${ch.velocityCurve}\",")
            sb.append("\"pan\":${ch.pan},")
            sb.append("\"reverbSend\":${ch.reverbSend},")
            sb.append("\"chorusSend\":${ch.chorusSend},")
            sb.append("\"filterCutoff\":${ch.filterCutoff}")
            sb.append("}")
        }
        sb.append("]")
        sb.append("}")
        return sb.toString()
    }

    private fun escape(s: String): String = s.replace("\"", "\\\"").replace("\n", "\\n")

    fun deserialize(json: String?): ActiveSessionSnapshot? {
        if (json.isNullOrBlank()) return null
        return try {
            val parser = SimpleJsonParser(json.trim())
            parser.parseActiveSessionSnapshot()
        } catch (e: Exception) {
            CrashReporter.recordException(e, "SessionSnapshotSerializer.deserialize")
            null
        }
    }
}
