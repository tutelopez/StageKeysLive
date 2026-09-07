package com.midi.mainstage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class DesktopGoogleDriveService : GoogleDriveService {
    override val state: GoogleDriveSyncState = GoogleDriveSyncState()

    override fun getDeviceAccounts(): List<String> = emptyList()

    override fun signIn(onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        onError("Google Sign-In no está soportado en Desktop.")
    }

    override fun signInWithEmail(email: String, onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        onError("Google Sign-In no está soportado en Desktop.")
    }

    override fun signOut() {}

    override suspend fun backupNow(concerts: List<Concert>): Result<Unit> {
        return Result.failure(UnsupportedOperationException("No soportado en Desktop"))
    }

    override suspend fun listBackups(): Result<List<DriveBackupItem>> {
        return Result.success(emptyList())
    }

    override suspend fun restoreBackup(backupId: String): Result<List<Concert>> {
        return Result.failure(UnsupportedOperationException("No soportado en Desktop"))
    }
}

@Composable
actual fun rememberGoogleDriveService(): GoogleDriveService {
    return remember { DesktopGoogleDriveService() }
}
