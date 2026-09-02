package com.midi.mainstage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class DesktopAutoBackupController : AutoBackupController {
    override val state: AutoBackupState = AutoBackupState()
    override fun requestSelectFolder() {}
    override fun clearFolder() {}
    override suspend fun backupToFolder(concerts: List<Concert>): Result<Unit> = Result.success(Unit)
}

@Composable
actual fun rememberAutoBackupController(): AutoBackupController {
    return remember { DesktopAutoBackupController() }
}
