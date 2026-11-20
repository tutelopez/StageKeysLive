import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import com.tutelopezmusic.stagekeyslive.*
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test

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

        val bytes = renderScene {
            val vuAnim = remember { channels.map { Animatable(0.45f) } }
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
                performanceStats = PerformanceStats(cpuPercent = 14, ramMb = 256, ramPercent = 28),
                batteryLevel = 95,
                batteryCharging = true,
                audioDiagnostics = "48 kHz · 128 spl",
                currentConnectedDevices = listOf("Nord Stage 3", "Korg NanoKontrol"),
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
            File("/Users/mac/.gemini/antigravity/brain/3ecf3b9c-68b4-45bb-b6d1-9de99142ec8c/desktop_phase1_concert.png").writeBytes(bytes)
            println("SAVED_CONCERT_SNAPSHOT")
        }
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
}
