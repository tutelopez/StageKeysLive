package com.tutelopezmusic.stagekeyslive

import androidx.compose.runtime.Composable

data class GoogleUserProfile(
    val uid: String,
    val displayName: String?,
    val firstName: String?,
    val email: String?,
    val photoUrl: String?
)

data class DriveBackupItem(
    val id: String,
    val name: String,
    val createdTimeFormatted: String,
    val timestamp: Long,
    val sizeFormatted: String
)

data class GoogleDriveSyncState(
    val isSignedIn: Boolean = false,
    val user: GoogleUserProfile? = null,
    val lastCloudBackupTimestamp: Long? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val statusMessage: String? = null
)

interface GoogleDriveService {
    val state: GoogleDriveSyncState
    fun getDeviceAccounts(): List<String> = emptyList()
    fun signIn(onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit)
    fun signInWithEmail(email: String, onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit)
    fun signOut()
    suspend fun backupNow(concerts: List<Concert>): Result<Unit>
    suspend fun listBackups(): Result<List<DriveBackupItem>>
    suspend fun restoreBackup(backupId: String): Result<List<Concert>>
}

@Composable
expect fun rememberGoogleDriveService(): GoogleDriveService
