package com.midi.mainstage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

class DesktopSf2ExplorerController : Sf2ExplorerController {
    override val state: Sf2ExplorerState = Sf2ExplorerState()
    override fun requestSelectFolder() {}
    override fun clearFolder() {}
    override fun refreshFiles() {}
    override suspend fun previewFile(entry: Sf2FileEntry, synth: PlatformAudioSynth) {}
    override fun stopPreview(synth: PlatformAudioSynth) {}
    override suspend fun importFileForChannel(entry: Sf2FileEntry): Pair<String, String>? = null
}

@Composable
actual fun rememberSf2ExplorerController(): Sf2ExplorerController {
    return remember { DesktopSf2ExplorerController() }
}
