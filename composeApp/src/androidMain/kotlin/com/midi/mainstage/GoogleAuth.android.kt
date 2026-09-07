package com.midi.mainstage

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "StageKeysGoogleAuth"
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

class AndroidGoogleDriveService(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : GoogleDriveService {

    private val auth = FirebaseAuth.getInstance()
    private val driveManager = GoogleDriveBackupManager(context)

    var currentSyncState by mutableStateOf(readCurrentState())
        private set

    override val state: GoogleDriveSyncState
        get() = currentSyncState

    init {
        auth.addAuthStateListener {
            coroutineScope.launch {
                currentSyncState = readCurrentState()
            }
        }
    }

    private fun readCurrentState(): GoogleDriveSyncState {
        val user = auth.currentUser
        val profile = user?.toGoogleUserProfile()
        val lastTime = driveManager.getLastCloudBackupTime()
        return GoogleDriveSyncState(
            isSignedIn = user != null,
            user = profile,
            lastCloudBackupTimestamp = lastTime
        )
    }

    private fun FirebaseUser.toGoogleUserProfile(): GoogleUserProfile {
        val full = displayName
        val first = full?.split(" ")?.firstOrNull()?.trim() ?: full
        return GoogleUserProfile(
            uid = uid,
            displayName = full,
            firstName = first,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }

    override fun signIn(onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        val activity = context as? Activity
        if (activity == null) {
            onError("Context is not an Activity")
            return
        }

        coroutineScope.launch {
            try {
                // 1. Resolve web client ID if present
                val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
                val serverClientId = if (resId != 0) context.getString(resId) else null

                var signedInUser: FirebaseUser? = null

                if (!serverClientId.isNullOrBlank()) {
                    // Modern Credential Manager API
                    try {
                        val credentialManager = CredentialManager.create(activity)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(serverClientId)
                            .setAutoSelectEnabled(false)
                            .build()

                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()

                        val response = credentialManager.getCredential(activity, request)
                        val cred = response.credential

                        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(cred.data)
                            val idToken = googleIdTokenCredential.idToken
                            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                            val authResult = auth.signInWithCredential(authCredential).await()
                            signedInUser = authResult.user
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Credential Manager flow attempt: ${e.message}")
                    }
                }

                // If Credential Manager didn't produce a user (or no web client ID), use GoogleSignInClient with Drive Scope
                if (signedInUser == null) {
                    val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestEmail()
                        .requestScopes(Scope(DRIVE_FILE_SCOPE))
                    
                    if (!serverClientId.isNullOrBlank()) {
                        gsoBuilder.requestIdToken(serverClientId)
                    }

                    val gso = gsoBuilder.build()
                    val googleSignInClient = GoogleSignIn.getClient(activity, gso)
                    
                    // Check already signed in Google account
                    val account = GoogleSignIn.getLastSignedInAccount(activity)
                    if (account != null && account.idToken != null) {
                        val authCredential = GoogleAuthProvider.getCredential(account.idToken, null)
                        val authResult = auth.signInWithCredential(authCredential).await()
                        signedInUser = authResult.user
                    } else {
                        // Request intent launch
                        val signInIntent = googleSignInClient.signInIntent
                        activity.startActivity(signInIntent)
                        return@launch
                    }
                }

                if (signedInUser != null) {
                    currentSyncState = readCurrentState()
                    onSuccess(signedInUser.toGoogleUserProfile())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Google Sign-In", e)
                CrashReporter.recordException(e, "GoogleSignIn")
                onError(e.message ?: "Error al iniciar sesión con Google")
            }
        }
    }

    override fun signOut() {
        coroutineScope.launch {
            try {
                auth.signOut()
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut()
                driveManager.clearCache()
                currentSyncState = readCurrentState()
            } catch (e: Exception) {
                Log.w(TAG, "Error during signOut: ${e.message}")
            }
        }
    }

    override suspend fun backupNow(concerts: List<Concert>): Result<Unit> {
        currentSyncState = currentSyncState.copy(isBackingUp = true)
        val result = driveManager.uploadBackup(concerts)
        currentSyncState = readCurrentState().copy(isBackingUp = false)
        return result
    }

    override suspend fun listBackups(): Result<List<DriveBackupItem>> {
        return driveManager.listBackups()
    }

    override suspend fun restoreBackup(backupId: String): Result<List<Concert>> {
        currentSyncState = currentSyncState.copy(isRestoring = true)
        val result = driveManager.downloadAndRestoreBackup(backupId)
        currentSyncState = currentSyncState.copy(isRestoring = false)
        return result
    }
}

@Composable
actual fun rememberGoogleDriveService(): GoogleDriveService {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val service = remember { AndroidGoogleDriveService(context, coroutineScope) }
    return service
}
