import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.ImageComposeScene
import com.tutelopezmusic.stagekeyslive.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class SnapshotTest {

    private fun renderScene(width: Int = 1280, height: Int = 800, content: @Composable () -> Unit): ByteArray? {
        val scene = ImageComposeScene(width = width, height = height) {
            StageKeysTheme {
                content()
            }
        }
        val img = scene.render()
        return img.encodeToData(EncodedImageFormat.PNG)?.bytes
    }

    @Test
    fun snapshotConcertView() {
        val channels = listOf(
            ChannelStripState(
                id = 1,
                name = "Grand Piano",
                sf2Name = "Yamaha CFX Grand",
                sf2Path = null,
                volume = 0.85f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#F5C542",
                reverbSend = 0.25f,
                chorusSend = 0.0f,
                filterCutoff = 1.0f,
                pan = 0.5f
            ),
            ChannelStripState(
                id = 2,
                name = "Warm Pad",
                sf2Name = "Analog Warm Strings",
                sf2Path = null,
                volume = 0.70f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 36,
                keyRangeEnd = 84,
                colorHex = "#4AA8B5",
                reverbSend = 0.60f,
                chorusSend = 0.40f,
                filterCutoff = 0.75f,
                pan = 0.35f
            ),
            ChannelStripState(
                id = 3,
                name = "Epic Strings",
                sf2Name = "Spitfire Orchestral",
                sf2Path = null,
                volume = 0.65f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 48,
                keyRangeEnd = 96,
                colorHex = "#4CCF5A",
                reverbSend = 0.50f,
                chorusSend = 0.15f,
                filterCutoff = 0.90f,
                pan = 0.65f
            ),
            ChannelStripState(
                id = 4,
                name = "Synth Lead",
                sf2Name = "Moog Mini Solo",
                sf2Path = null,
                volume = 0.75f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 60,
                keyRangeEnd = 108,
                colorHex = "#7C5CFF",
                reverbSend = 0.30f,
                chorusSend = 0.50f,
                filterCutoff = 0.85f,
                pan = 0.5f
            ),
            ChannelStripState(
                id = 5,
                name = "Rhodes MK1",
                sf2Name = "Vintage Suitcase 73",
                sf2Path = null,
                volume = 0.80f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 28,
                keyRangeEnd = 100,
                colorHex = "#FB7185",
                reverbSend = 0.35f,
                chorusSend = 0.60f,
                filterCutoff = 0.95f,
                pan = 0.45f
            ),
            ChannelStripState(
                id = 6,
                name = "B3 Organ",
                sf2Name = "Hammond Classic",
                sf2Path = null,
                volume = 0.72f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 36,
                keyRangeEnd = 96,
                colorHex = "#FBBF24",
                reverbSend = 0.40f,
                chorusSend = 0.45f,
                filterCutoff = 0.80f,
                pan = 0.55f
            ),
            ChannelStripState(
                id = 7,
                name = "French Horns",
                sf2Name = "Brass Ensemble",
                sf2Path = null,
                volume = 0.68f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 40,
                keyRangeEnd = 88,
                colorHex = "#2DD4BF",
                reverbSend = 0.55f,
                chorusSend = 0.20f,
                filterCutoff = 0.88f,
                pan = 0.70f
            ),
            ChannelStripState(
                id = 8,
                name = "Acoustic Bass",
                sf2Name = "Upright Jazz Bass",
                sf2Path = null,
                volume = 0.78f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 60,
                colorHex = "#F472B6",
                reverbSend = 0.15f,
                chorusSend = 0.0f,
                filterCutoff = 0.70f,
                pan = 0.5f
            )
        )

        val patches = listOf(
            PatchState(
                name = "01 Intro Worship",
                category = "Piano & Pad",
                programNumber = 1,
                description = "Balada lenta",
                isFavorite = true,
                id = "p1"
            ),
            PatchState(
                name = "02 Verse Acoustic",
                category = "Acoustic",
                programNumber = 2,
                description = "Solo acústico",
                isFavorite = false,
                id = "p2"
            ),
            PatchState(
                name = "03 Chorus Full",
                category = "Powerful",
                programNumber = 3,
                description = "Toda la banda",
                isFavorite = true,
                id = "p3"
            )
        )

        val concert = Concert(
            id = "c1",
            name = "Domingo Noche - En Vivo",
            lastModified = System.currentTimeMillis(),
            patches = patches,
            channels = channels
        )

        val bytes = renderScene(width = 1280, height = 800) {
            val vuAnim = remember { channels.map { Animatable(0.55f) } }
            ConcertViewScreen(
                concert = concert,
                selectedPatchIndex = 0,
                onSelectPatch = {},
                onAddPatchClick = {},
                onEditPatchClick = {},
                onDeletePatch = {},
                onExportPatchClick = {},
                onImportPatchClick = {},
                onToggleFavorite = {},
                onMovePatch = { _, _ -> },
                onBackClick = {},
                onSettingsClick = {},
                onPanicClick = {},
                performanceStats = PerformanceStats(cpuPercent = 8, ramMb = 180, ramPercent = 18),
                batteryLevel = 95,
                batteryCharging = false,
                audioDiagnostics = "48 kHz • 128 frames (2.7 ms)",
                currentConnectedDevices = listOf("nanoKONTROL2", "Arturia KeyLab 88"),
                midiActivityIndicator = true,
                metronomeOn = true,
                onMetronomeToggle = {},
                metronomeBpm = 128,
                onBpmChange = {},
                onBpmChangeFinished = {},
                onTapTempo = {},
                metronomeVolume = 0.7f,
                onMetronomeVolumeChange = {},
                metronomeTick = false,
                isRecording = false,
                onRecordToggle = {},
                isPlayingRecording = false,
                onPlayRecordingClick = {},
                hasRecording = false,
                onExportMidiClick = {},
                onVolumeChange = { _, _ -> },
                onMuteToggle = {},
                onSoloToggle = {},
                onAddChannelClick = {},
                onChannelGearClick = {},
                onReverbChange = { _, _ -> },
                onChorusChange = { _, _ -> },
                onCutoffChange = { _, _ -> },
                midiMappings = emptyMap(),
                masterVolume = 0.82f,
                masterPan = 0.5f,
                isMasterLimiterActive = true,
                onMasterVolumeChange = {},
                onMasterPanChange = {},
                masterVuLevel = 0.65f,
                padEnabled = true,
                onPadEnabledChange = {},
                padVolume = 0.6f,
                onPadVolumeChange = {},
                padBank = "Ambient C",
                onPadBankChange = {},
                availablePadBanks = listOf("Ambient C", "Shimmer D", "Warm G"),
                activePadNote = 0,
                onPadNoteToggle = {},
                activeNote = 60,
                onNoteDown = {},
                onNoteUp = {},
                pitchBend = remember { Animatable(0f) },
                modulation = remember { Animatable(0.3f) },
                onModulationChange = {},
                sustainActive = true,
                onSustainToggle = {},
                vuLevels = vuAnim
            )
        }

        if (bytes != null) {
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase3_concert_1280x800.png").writeBytes(bytes)
            println("SAVED_PHASE3_CONCERT_1280x800_SNAPSHOT")
        }
    }

    @Test
    fun snapshotConcertViewCompact() {
        val channels = listOf(
            ChannelStripState(
                id = 1,
                name = "Grand Piano",
                sf2Name = "CFX Grand.sf2",
                sf2Path = null,
                volume = 0.85f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#F5C542"
            ),
            ChannelStripState(
                id = 2,
                name = "Warm Pad",
                sf2Name = "Warm Pad.sf2",
                sf2Path = null,
                volume = 0.70f,
                isMuted = false,
                isSoloed = true,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#4AA8B5"
            ),
            ChannelStripState(
                id = 3,
                name = "Strings",
                sf2Name = "Strings.sf2",
                sf2Path = null,
                volume = 0.60f,
                isMuted = true,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#4CCF5A"
            ),
            ChannelStripState(
                id = 4,
                name = "Lead",
                sf2Name = "Moog Lead.sf2",
                sf2Path = null,
                volume = 0.75f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#7C5CFF"
            )
        )

        val concert = Concert(
            id = "c_compact",
            name = "Concierto Móvil Compacto",
            lastModified = System.currentTimeMillis(),
            patches = listOf(PatchState("01 Compact Patch", "Live", 1, "", isFavorite = true)),
            channels = channels
        )

        val bytes = renderScene(width = 800, height = 360) {
            val vuAnim = remember { channels.map { Animatable(0.5f) } }
            ConcertViewScreen(
                concert = concert,
                selectedPatchIndex = 0,
                onSelectPatch = {},
                onAddPatchClick = {},
                onEditPatchClick = {},
                onDeletePatch = {},
                onExportPatchClick = {},
                onImportPatchClick = {},
                onToggleFavorite = {},
                onMovePatch = { _, _ -> },
                onBackClick = {},
                onSettingsClick = {},
                onPanicClick = {},
                performanceStats = PerformanceStats(cpuPercent = 12, ramMb = 140, ramPercent = 15),
                batteryLevel = 80,
                batteryCharging = false,
                audioDiagnostics = "48 kHz",
                currentConnectedDevices = emptyList(),
                midiActivityIndicator = false,
                metronomeOn = false,
                onMetronomeToggle = {},
                metronomeBpm = 120,
                onBpmChange = {},
                onBpmChangeFinished = {},
                onTapTempo = {},
                metronomeVolume = 0.5f,
                onMetronomeVolumeChange = {},
                metronomeTick = false,
                isRecording = false,
                onRecordToggle = {},
                isPlayingRecording = false,
                onPlayRecordingClick = {},
                onVolumeChange = { _, _ -> },
                onMuteToggle = {},
                onSoloToggle = {},
                onAddChannelClick = {},
                onChannelGearClick = {},
                masterVolume = 0.8f,
                masterPan = 0.5f,
                isMasterLimiterActive = false,
                onMasterVolumeChange = {},
                onMasterPanChange = {},
                masterVuLevel = 0.5f,
                padEnabled = false,
                onPadEnabledChange = {},
                padVolume = 0.5f,
                onPadVolumeChange = {},
                padBank = "Bank A",
                onPadBankChange = {},
                availablePadBanks = listOf("Bank A"),
                activePadNote = null,
                onPadNoteToggle = {},
                activeNote = null,
                onNoteDown = {},
                onNoteUp = {},
                pitchBend = remember { Animatable(0f) },
                modulation = remember { Animatable(0f) },
                onModulationChange = {},
                sustainActive = false,
                onSustainToggle = {},
                vuLevels = vuAnim
            )
        }

        if (bytes != null) {
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase3_compact_800x360.png").writeBytes(bytes)
            println("SAVED_PHASE3_COMPACT_800x360_SNAPSHOT")
        }
    }

    @Test
    fun testMixer16ChannelsRecompositionPerformance() {
        val stripRecompositionCounts = IntArray(16) { 0 }
        val masterRecompositionCount = intArrayOf(0)

        // Estados observables simulando 60 FPS de medidores de nivel de audio
        val channelLevels = Array(16) { mutableStateOf(0.1f) }
        val masterLevel = mutableStateOf(0.2f)

        val channels = (1..16).map { id ->
            ChannelStripState(
                id = id,
                name = "Canal $id",
                sf2Name = "Instrument$id.sf2",
                sf2Path = null,
                volume = 0.8f,
                isMuted = false,
                isSoloed = false,
                keyRangeStart = 21,
                keyRangeEnd = 108,
                colorHex = "#7C5CFF"
            )
        }

        val scene = ImageComposeScene(width = 1280, height = 800) {
            StageKeysTheme {
                Row(modifier = Modifier.fillMaxSize()) {
                    channels.forEachIndexed { index, ch ->
                        SideEffect {
                            stripRecompositionCounts[index]++
                        }
                        ProChannelStrip(
                            channel = ch,
                            levelProvider = { channelLevels[index].value },
                            onVolumeChange = {},
                            onMuteToggle = {},
                            onSoloToggle = {},
                            onGearClick = {},
                            onFxClick = {},
                            onPanChange = {}
                        )
                    }

                    SideEffect {
                        masterRecompositionCount[0]++
                    }
                    ProMasterStrip(
                        volume = 0.8f,
                        onVolumeChange = {},
                        levelProvider = { masterLevel.value },
                        isLimiterActive = false,
                        onLimiterToggle = {}
                    )
                }
            }
        }

        // Renderizado inicial (frame 0)
        scene.render()

        val initialStripCounts = stripRecompositionCounts.clone()
        val initialMasterCount = masterRecompositionCount[0]

        // Simulamos 60 frames continuos de audio VU variando niveles
        for (frame in 1..60) {
            for (i in 0 until 16) {
                channelLevels[i].value = (frame % 10) / 10f
            }
            masterLevel.value = (frame % 10) / 10f
            scene.render()
        }

        // Medir recomposiciones adicionales
        val deltaStripCounts = stripRecompositionCounts.mapIndexed { idx, count -> count - initialStripCounts[idx] }
        val deltaMaster = masterRecompositionCount[0] - initialMasterCount

        println("PERFORMANCE_VERIFICATION_RESULTS:")
        println("16 Canales delta recomposiciones tras 60 frames de audio: $deltaStripCounts")
        println("Master delta recomposiciones tras 60 frames de audio: $deltaMaster")

        // El strip NO debe recomponerse durante frames de audio (levelProvider se evalúa en DrawScope)
        deltaStripCounts.forEachIndexed { idx, delta ->
            assertEquals(0, delta, "Canal $idx recomps=$delta (debe ser 0)")
        }
        assertEquals(0, deltaMaster, "Master recomps=$deltaMaster (debe ser 0)")
    }

    @Test
    fun snapshotDashboard() {
        val concerts = listOf(
            Concert(
                id = "c1",
                name = "Domingo Noche - En Vivo",
                lastModified = System.currentTimeMillis() - 3600000,
                patches = listOf(PatchState("Intro", "Worship", 1, "")),
                channels = listOf(ChannelStripState(1, "Piano", "Yamaha Grand", null, 0.8f, false, false, 21, 108, "#F5C542"))
            ),
            Concert(
                id = "c2",
                name = "Ensayo General Banda",
                lastModified = System.currentTimeMillis() - 86400000,
                patches = listOf(PatchState("Song 1", "Rock", 1, "")),
                channels = listOf(ChannelStripState(1, "Organ", "Hammond B3", null, 0.75f, false, false, 21, 108, "#4AA8B5"))
            ),
            Concert(
                id = "c3",
                name = "Acústico Set List",
                lastModified = System.currentTimeMillis() - 172800000,
                patches = emptyList(),
                channels = emptyList()
            )
        )

        val bytes = renderScene {
            DashboardScreen(
                concerts = concerts,
                onCreateConcertClick = {},
                onEditConcertClick = {},
                onOpenLastConcertClick = {},
                onSelectConcert = {},
                onDeleteConcert = {},
                onExportConcertClick = {},
                onImportClick = {},
                onSettingsClick = {},
                userProfile = GoogleUserProfile("usr1", "Tute López", "Tute", "tute@stagekeys.live", null)
            )
        }

        if (bytes != null) {
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase1_dashboard.png").writeBytes(bytes)
            println("SAVED_DASHBOARD_SNAPSHOT")
        }
    }

    @Test
    fun snapshotBaseComponents() {
        val bytes = renderScene(width = 800, height = 700) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Showcase Componentes Base (Fase 2)",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkButton(onClick = {}, variant = SkButtonVariant.Primary) {
                        Text("Primary")
                    }
                    SkButton(onClick = {}, variant = SkButtonVariant.Secondary) {
                        Text("Secondary")
                    }
                    SkButton(onClick = {}, variant = SkButtonVariant.Outline) {
                        Text("Outline")
                    }
                    SkButton(onClick = {}, variant = SkButtonVariant.Danger) {
                        Text("Danger")
                    }
                }

                // Chips Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SkChip(selected = true, onClick = {}, label = "Piano CFX")
                    SkChip(selected = false, onClick = {}, label = "Strings Pad")
                    SkChip(selected = false, onClick = {}, label = "Moog Lead")
                }

                // Switch Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SkSwitch(checked = true, onCheckedChange = {})
                    Text("Limiter Activo", color = TextPrimary, fontSize = 13.sp)

                    Spacer(Modifier.width(20.dp))

                    SkSwitch(checked = false, onCheckedChange = {})
                    Text("Auto-Backup Inactivo", color = TextSecondary, fontSize = 13.sp)
                }

                // Card with ListItems
                SkCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Lista de Dispositivos & Entradas",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkListItem(
                            headlineText = "Nord Stage 3 Compact",
                            supportingText = "MIDI USB · Canal 1 · Conectado",
                            onClick = {}
                        )
                        SkListItem(
                            headlineText = "Korg NanoKontrol 2",
                            supportingText = "8 Faders · 8 Pots · Mapeado",
                            onClick = {}
                        )
                    }
                }
            }
        }

        if (bytes != null) {
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase2_base_components.png").writeBytes(bytes)
            println("SAVED_BASE_COMPONENTS_SNAPSHOT")
        }
    }

    @Test
    fun snapshotProMixerShowcase() {
        val ch1 = ChannelStripState(
            id = 1,
            name = "Grand Piano",
            sf2Name = "Yamaha CFX.sf2",
            sf2Path = null,
            volume = 1.0f, // 0.0 dB
            isMuted = false,
            isSoloed = false,
            keyRangeStart = 21,
            keyRangeEnd = 108,
            colorHex = "#F5C542",
            reverbSend = 0.25f,
            chorusSend = 0.0f,
            filterCutoff = 1.0f,
            pan = 0.5f
        )
        val ch2 = ChannelStripState(
            id = 2,
            name = "Warm Pad",
            sf2Name = "Analog Pad.sf2",
            sf2Path = null,
            volume = 0.3162f, // -10.0 dB
            isMuted = false,
            isSoloed = true,
            keyRangeStart = 36,
            keyRangeEnd = 84,
            colorHex = "#4AA8B5",
            reverbSend = 0.60f,
            chorusSend = 0.35f,
            filterCutoff = 0.80f,
            pan = 0.35f
        )

        val bytes = renderScene(width = 860, height = 520) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Strip 1: Grand Piano
                ProChannelStrip(
                    channel = ch1,
                    levelProvider = { 0.72f },
                    onVolumeChange = {},
                    onMuteToggle = {},
                    onSoloToggle = {},
                    onGearClick = {},
                    onFxClick = {},
                    onPanChange = {},
                    modifier = Modifier.width(110.dp)
                )

                // Strip 2: Warm Pad
                ProChannelStrip(
                    channel = ch2,
                    levelProvider = { 0.45f },
                    onVolumeChange = {},
                    onMuteToggle = {},
                    onSoloToggle = {},
                    onGearClick = {},
                    onFxClick = {},
                    onPanChange = {},
                    modifier = Modifier.width(110.dp)
                )

                // Column of Faders at distinct dB values
                Column(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfacePanel)
                        .border(1.dp, BorderHairline, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Faders a Distintos Valores dB",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // -20 dB (V = 0.1)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            ProFader(volume = 0.10f, onVolumeChange = {}, modifier = Modifier.weight(1f))
                            Text("-20 dB", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        // -30 dB (V = 0.0316)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            ProFader(volume = 0.0316f, onVolumeChange = {}, modifier = Modifier.weight(1f))
                            Text("-30 dB", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        // -50 dB (V = 0.00316)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            ProFader(volume = 0.00316f, onVolumeChange = {}, modifier = Modifier.weight(1f))
                            Text("-50 dB", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        // -inf (V = 0.0)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            ProFader(volume = 0.0f, onVolumeChange = {}, modifier = Modifier.weight(1f))
                            Text("-∞", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Strip Click / Metrónomo
                ProMetronomeStrip(
                    volume = 0.70f,
                    onVolumeChange = {},
                    enabled = true,
                    onToggle = {},
                    bpm = 128,
                    onTapTempo = {},
                    levelProvider = { 0.55f },
                    modifier = Modifier.width(100.dp)
                )

                // Strip Master
                ProMasterStrip(
                    volume = 0.85f,
                    onVolumeChange = {},
                    levelProvider = { 0.68f },
                    isLimiterActive = true,
                    onLimiterToggle = {},
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        if (bytes != null) {
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase3_mixer_showcase.png").writeBytes(bytes)
            println("SAVED_MIXER_SHOWCASE_SNAPSHOT")
        }
    }

    @Test
    fun testOldConcertBackwardCompatibility() {
        val oldConcertJson = """
        [
          {
            "id": "old_c1",
            "name": "Concierto Antiguo Pre-Rediseño",
            "lastModified": 1700000000000,
            "patches": [
              {
                "id": "patch_old_1",
                "name": "Patch Vintage",
                "category": "Classic",
                "programNumber": 1,
                "description": "Creado con versión vieja",
                "transposeSemitones": 0,
                "isFavorite": true,
                "channelsSnapshot": [
                  {
                    "channelId": 1,
                    "name": "Piano Acústico",
                    "sf2Name": "OldPiano.sf2",
                    "volume": 0.8,
                    "isMuted": false,
                    "isSoloed": false,
                    "keyRangeStart": 21,
                    "keyRangeEnd": 108,
                    "colorHex": "#38BDF8",
                    "velocityCurve": "LINEAR",
                    "pan": 0.5,
                    "reverbSend": 0.2,
                    "chorusSend": 0.0,
                    "filterCutoff": 1.0
                  },
                  {
                    "channelId": 2,
                    "name": "Sintetizador Lead",
                    "sf2Name": "OldLead.sf2",
                    "volume": 0.7,
                    "isMuted": false,
                    "isSoloed": false,
                    "keyRangeStart": 40,
                    "keyRangeEnd": 88,
                    "colorHex": "#4ADE80",
                    "velocityCurve": "LINEAR",
                    "pan": 0.5,
                    "reverbSend": 0.3,
                    "chorusSend": 0.1,
                    "filterCutoff": 0.8
                  }
                ]
              }
            ],
            "channels": [
              {
                "id": 1,
                "name": "Piano Acústico",
                "sf2Name": "OldPiano.sf2",
                "volume": 0.8,
                "isMuted": false,
                "isSoloed": false,
                "keyRangeStart": 21,
                "keyRangeEnd": 108,
                "colorHex": "#38BDF8",
                "velocityCurve": "LINEAR",
                "pan": 0.5,
                "reverbSend": 0.2,
                "chorusSend": 0.0,
                "filterCutoff": 1.0
              },
              {
                "id": 2,
                "name": "Sintetizador Lead",
                "sf2Name": "OldLead.sf2",
                "volume": 0.7,
                "isMuted": false,
                "isSoloed": false,
                "keyRangeStart": 40,
                "keyRangeEnd": 88,
                "colorHex": "#4ADE80",
                "velocityCurve": "LINEAR",
                "pan": 0.5,
                "reverbSend": 0.3,
                "chorusSend": 0.1,
                "filterCutoff": 0.8
              }
            ]
          }
        ]
        """.trimIndent()

        val concerts = ConcertSerializer.deserialize(oldConcertJson)
        kotlin.test.assertEquals(1, concerts.size)
        val loaded = concerts[0]
        kotlin.test.assertEquals("Concierto Antiguo Pre-Rediseño", loaded.name)
        kotlin.test.assertEquals(2, loaded.channels.size)
        // Verify old colorHex are preserved intact
        kotlin.test.assertEquals("#38BDF8", loaded.channels[0].colorHex)
        kotlin.test.assertEquals("#4ADE80", loaded.channels[1].colorHex)

        // Verify it renders cleanly in ConcertViewScreen
        val bytes = renderScene {
            val vuAnim = remember { loaded.channels.map { Animatable(0.5f) } }
            ConcertViewScreen(
                concert = loaded,
                selectedPatchIndex = 0,
                onSelectPatch = {},
                onAddPatchClick = {},
                onEditPatchClick = {},
                onDeletePatch = {},
                onExportPatchClick = {},
                onImportPatchClick = {},
                onToggleFavorite = {},
                onMovePatch = { _, _ -> },
                onBackClick = {},
                onSettingsClick = {},
                onPanicClick = {},
                performanceStats = PerformanceStats(cpuPercent = 10, ramMb = 200, ramPercent = 20),
                batteryLevel = 90,
                batteryCharging = false,
                audioDiagnostics = "48 kHz",
                currentConnectedDevices = emptyList(),
                midiActivityIndicator = false,
                metronomeOn = false,
                onMetronomeToggle = {},
                metronomeBpm = 120,
                onBpmChange = {},
                onBpmChangeFinished = {},
                onTapTempo = {},
                metronomeVolume = 0.5f,
                onMetronomeVolumeChange = {},
                metronomeTick = false,
                isRecording = false,
                onRecordToggle = {},
                isPlayingRecording = false,
                onPlayRecordingClick = {},
                hasRecording = false,
                onExportMidiClick = {},
                onVolumeChange = { _, _ -> },
                onMuteToggle = {},
                onSoloToggle = {},
                onAddChannelClick = {},
                onChannelGearClick = {},
                midiMappings = emptyMap(),
                masterVolume = 0.8f,
                masterPan = 0.5f,
                isMasterLimiterActive = false,
                onMasterVolumeChange = {},
                onMasterPanChange = {},
                masterVuLevel = 0.5f,
                padEnabled = false,
                onPadEnabledChange = {},
                padVolume = 0.5f,
                onPadVolumeChange = {},
                padBank = "Bank A",
                onPadBankChange = {},
                availablePadBanks = listOf("Bank A"),
                activePadNote = null,
                onPadNoteToggle = {},
                activeNote = null,
                onNoteDown = {},
                onNoteUp = {},
                pitchBend = remember { Animatable(0f) },
                modulation = remember { Animatable(0f) },
                onModulationChange = {},
                sustainActive = false,
                onSustainToggle = {},
                vuLevels = vuAnim
            )
        }
        kotlin.test.assertNotNull(bytes)
        println("OLD_CONCERT_RENDER_SUCCESS: bytes=${bytes.size}")
    }
}
