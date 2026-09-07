package com.midi.mainstage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
    private val prefs = context.getSharedPreferences("google_account_prefs", Context.MODE_PRIVATE)

    var activityLauncher: ((Intent) -> Unit)? = null
    private var pendingOnSuccess: ((GoogleUserProfile) -> Unit)? = null
    private var pendingOnError: ((String) -> Unit)? = null

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

    private fun saveAccountToPrefs(profile: GoogleUserProfile) {
        prefs.edit()
            .putString("user_uid", profile.uid)
            .putString("user_display_name", profile.displayName)
            .putString("user_first_name", profile.firstName)
            .putString("user_email", profile.email)
            .putString("user_photo_url", profile.photoUrl)
            .putBoolean("is_signed_in", true)
            .apply()
    }

    private fun clearAccountPrefs() {
        prefs.edit().clear().apply()
    }

    private fun readCurrentState(): GoogleDriveSyncState {
        val lastTime = driveManager.getLastCloudBackupTime()

        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount != null) {
            val profile = googleAccount.toGoogleUserProfile()
            return GoogleDriveSyncState(
                isSignedIn = true,
                user = profile,
                lastCloudBackupTimestamp = lastTime
            )
        }

        val isSavedSignedIn = prefs.getBoolean("is_signed_in", false)
        if (isSavedSignedIn) {
            val profile = GoogleUserProfile(
                uid = prefs.getString("user_uid", "user") ?: "user",
                displayName = prefs.getString("user_display_name", null),
                firstName = prefs.getString("user_first_name", null),
                email = prefs.getString("user_email", null),
                photoUrl = prefs.getString("user_photo_url", null)
            )
            return GoogleDriveSyncState(
                isSignedIn = true,
                user = profile,
                lastCloudBackupTimestamp = lastTime
            )
        }

        val user = auth.currentUser
        if (user != null) {
            return GoogleDriveSyncState(
                isSignedIn = true,
                user = user.toGoogleUserProfile(),
                lastCloudBackupTimestamp = lastTime
            )
        }

        return GoogleDriveSyncState(
            isSignedIn = false,
            user = null,
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

    private fun GoogleSignInAccount.toGoogleUserProfile(): GoogleUserProfile {
        val full = displayName ?: email ?: "Usuario Google"
        val first = givenName ?: full.split(" ").firstOrNull()?.trim() ?: full
        return GoogleUserProfile(
            uid = id ?: email ?: "google_user",
            displayName = full,
            firstName = first,
            email = email,
            photoUrl = photoUrl?.toString()
        )
    }

    override fun signIn(onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        pendingOnSuccess = onSuccess
        pendingOnError = onError

        coroutineScope.launch {
            try {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope(DRIVE_FILE_SCOPE))
                    .build()

                val client = GoogleSignIn.getClient(context, gso)
                val signInIntent = client.signInIntent

                val launcher = activityLauncher
                if (launcher != null) {
                    launcher.invoke(signInIntent)
                } else if (context is Activity) {
                    context.startActivity(signInIntent)
                } else {
                    onError("No se pudo abrir el selector de cuentas de Google.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating Google Sign-In", e)
                CrashReporter.recordException(e, "GoogleSignInInit")
                onError(e.message ?: "Error al iniciar inicio de sesión")
            }
        }
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        coroutineScope.launch {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val profile = account.toGoogleUserProfile()
                    saveAccountToPrefs(profile)

                    try {
                        if (account.idToken != null) {
                            val authCred = GoogleAuthProvider.getCredential(account.idToken, null)
                            auth.signInWithCredential(authCred).await()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Firebase credential sign-in note: ${e.message}")
                    }

                    currentSyncState = readCurrentState()
                    pendingOnSuccess?.invoke(profile)
                } else {
                    pendingOnError?.invoke("No se seleccionó ninguna cuenta")
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed with status code ${e.statusCode}", e)
                val msg = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Inicio de sesión cancelado"
                    GoogleSignInStatusCodes.NETWORK_ERROR -> "Error de red al conectar con Google"
                    else -> "Error (${e.statusCode}): ${e.message}"
                }
                pendingOnError?.invoke(msg)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Google Sign-In result", e)
                CrashReporter.recordException(e, "GoogleSignInResult")
                pendingOnError?.invoke(e.message ?: "Error al procesar cuenta de Google")
            } finally {
                pendingOnSuccess = null
                pendingOnError = null
            }
        }
    }

    override fun signOut() {
        coroutineScope.launch {
            try {
                auth.signOut()
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut().await()
            } catch (e: Exception) {
                Log.w(TAG, "Error during signOut: ${e.message}")
            } finally {
                clearAccountPrefs()
                driveManager.clearCache()
                currentSyncState = readCurrentState()
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
    val service = remember(context, coroutineScope) {
        AndroidGoogleDriveService(context, coroutineScope)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        service.handleActivityResult(result.resultCode, result.data)
    }

    DisposableEffect(service, launcher) {
        service.activityLauncher = { intent ->
            launcher.launch(intent)
        }
        onDispose {
            service.activityLauncher = null
        }
    }

    return service
}
