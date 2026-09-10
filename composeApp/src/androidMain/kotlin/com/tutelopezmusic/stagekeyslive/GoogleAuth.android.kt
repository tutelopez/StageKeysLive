package com.tutelopezmusic.stagekeyslive

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
        driveManager.onAuthRecoveryNeeded = { intent ->
            val launcher = activityLauncher
            if (launcher != null) {
                launcher.invoke(intent)
            } else if (context is Activity) {
                context.startActivity(intent)
            } else {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
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
            .commit()
    }

    private fun clearAccountPrefs() {
        prefs.edit().clear().commit()
    }

    private fun readCurrentState(): GoogleDriveSyncState {
        val lastTime = driveManager.getLastCloudBackupTime()

        // 1. SharedPreferences (Highest priority for user-selected account)
        val isSavedSignedIn = prefs.getBoolean("is_signed_in", false)
        val savedEmail = prefs.getString("user_email", null)
        if (isSavedSignedIn && !savedEmail.isNullOrBlank()) {
            val profile = GoogleUserProfile(
                uid = prefs.getString("user_uid", savedEmail) ?: savedEmail,
                displayName = prefs.getString("user_display_name", null) ?: savedEmail,
                firstName = prefs.getString("user_first_name", null),
                email = savedEmail,
                photoUrl = prefs.getString("user_photo_url", null)
            )
            return GoogleDriveSyncState(
                isSignedIn = true,
                user = profile,
                lastCloudBackupTimestamp = lastTime
            )
        }

        // 2. GoogleSignIn Account
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (googleAccount != null && !googleAccount.email.isNullOrBlank()) {
            val profile = googleAccount.toGoogleUserProfile()
            return GoogleDriveSyncState(
                isSignedIn = true,
                user = profile,
                lastCloudBackupTimestamp = lastTime
            )
        }

        // 3. Firebase User
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

    private fun getProfileForEmail(email: String): GoogleUserProfile {
        var displayName: String? = null
        var photoUrl: String? = null

        try {
            val gAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (gAccount != null && gAccount.email.equals(email, ignoreCase = true)) {
                displayName = gAccount.displayName
                photoUrl = gAccount.photoUrl?.toString()
            }
        } catch (_: Exception) {}

        if (displayName.isNullOrBlank()) {
            try {
                val cursor = context.contentResolver.query(
                    ContactsContract.Profile.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Profile.DISPLAY_NAME,
                        ContactsContract.Profile.PHOTO_URI
                    ),
                    null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(ContactsContract.Profile.DISPLAY_NAME)
                        val photoIdx = it.getColumnIndex(ContactsContract.Profile.PHOTO_URI)
                        if (nameIdx >= 0 && displayName.isNullOrBlank()) displayName = it.getString(nameIdx)
                        if (photoIdx >= 0 && photoUrl.isNullOrBlank()) photoUrl = it.getString(photoIdx)
                    }
                }
            } catch (_: Exception) {}
        }

        if (displayName.isNullOrBlank()) {
            val username = email.substringBefore("@")
            displayName = username.split(".", "_", "-")
                .filter { it.isNotBlank() }
                .joinToString(" ") { part -> part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
        }

        val firstName = displayName?.split(" ")?.firstOrNull()?.trim() ?: displayName

        return GoogleUserProfile(
            uid = email,
            displayName = displayName,
            firstName = firstName,
            email = email,
            photoUrl = photoUrl
        )
    }

    override fun getDeviceAccounts(): List<String> {
        return try {
            val am = AccountManager.get(context)
            am.getAccountsByType("com.google").map { it.name }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot query accounts directly: ${e.message}")
            emptyList()
        }
    }

    override fun signInWithEmail(email: String, onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        coroutineScope.launch {
            try {
                val profile = getProfileForEmail(email.trim())
                saveAccountToPrefs(profile)
                withContext(Dispatchers.Main) {
                    currentSyncState = readCurrentState()
                }
                Log.i(TAG, "Signed in with email directly: ${profile.email}")
                onSuccess(profile)
            } catch (e: Exception) {
                Log.e(TAG, "Error in signInWithEmail", e)
                onError(e.message ?: "Error al iniciar sesión con $email")
            }
        }
    }

    override fun signIn(onSuccess: (GoogleUserProfile) -> Unit, onError: (String) -> Unit) {
        pendingOnSuccess = onSuccess
        pendingOnError = onError

        coroutineScope.launch {
            try {
                Log.i(TAG, "signIn() initiated")
                
                // 1. First try GoogleSignInClient with DEFAULT_SIGN_IN (Email + Profile)
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestProfile()
                    .build()
                val client = GoogleSignIn.getClient(context, gso)
                val intent = client.signInIntent

                val launcher = activityLauncher
                if (launcher != null) {
                    Log.i(TAG, "Launching GoogleSignIn intent via ActivityResultLauncher")
                    launcher.invoke(intent)
                } else if (context is Activity) {
                    Log.i(TAG, "Launching GoogleSignIn intent via Activity.startActivity")
                    context.startActivity(intent)
                } else {
                    onError("No se pudo abrir el selector de cuentas.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening GoogleSignIn", e)
                try {
                    // Fallback to AccountPicker
                    val pickerIntent = com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
                        com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                            .setAllowableAccountsTypes(listOf("com.google"))
                            .setAlwaysShowAccountPicker(true)
                            .build()
                    )
                    activityLauncher?.invoke(pickerIntent) ?: (context as? Activity)?.startActivity(pickerIntent)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback account picker failed", fallbackEx)
                    CrashReporter.recordException(e, "AccountPickerInit")
                    onError(e.message ?: "Error al abrir el selector de cuentas")
                }
            }
        }
    }

    fun handleActivityResult(resultCode: Int, data: Intent?) {
        Log.i(TAG, "handleActivityResult called with resultCode=$resultCode, data=$data")
        coroutineScope.launch {
            try {
                // 1. Try GoogleSignIn task extraction
                var foundProfile: GoogleUserProfile? = null
                
                if (data != null) {
                    try {
                        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                        if (task.isSuccessful) {
                            val account = task.result
                            if (account != null && !account.email.isNullOrBlank()) {
                                Log.i(TAG, "Successfully extracted account from GoogleSignIn task: ${account.email}")
                                foundProfile = account.toGoogleUserProfile()
                            }
                        } else {
                            val exception = task.exception
                            Log.w(TAG, "GoogleSignIn task was not successful: ${exception?.message}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Exception parsing GoogleSignIn result: ${e.message}")
                    }
                }

                // 2. Fallback to AccountManager / Intent extras if GoogleSignIn didn't return profile
                if (foundProfile == null && data != null) {
                    var accountName: String? = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName.isNullOrBlank()) {
                        accountName = data.getStringExtra("authAccount")
                    }
                    if (accountName.isNullOrBlank()) {
                        accountName = data.getStringExtra("accountName")
                    }

                    Log.i(TAG, "Resolved accountName from intent extras: $accountName")
                    if (!accountName.isNullOrBlank()) {
                        foundProfile = getProfileForEmail(accountName)
                    }
                }

                // 3. Fallback to getLastSignedInAccount if already authorized
                if (foundProfile == null) {
                    val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                    if (lastAccount != null && !lastAccount.email.isNullOrBlank()) {
                        Log.i(TAG, "Found last signed in account: ${lastAccount.email}")
                        foundProfile = lastAccount.toGoogleUserProfile()
                    }
                }

                if (foundProfile != null) {
                    saveAccountToPrefs(foundProfile)
                    withContext(Dispatchers.Main) {
                        currentSyncState = readCurrentState()
                    }
                    Log.i(TAG, "Successfully completed sign-in for: ${foundProfile.email}")
                    pendingOnSuccess?.invoke(foundProfile)
                } else {
                    Log.w(TAG, "Could not resolve signed in profile from activity result (resultCode=$resultCode)")
                    if (resultCode != Activity.RESULT_CANCELED) {
                        pendingOnError?.invoke("No se pudo obtener la cuenta seleccionada.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleActivityResult", e)
                CrashReporter.recordException(e, "AccountPickerResult")
                pendingOnError?.invoke(e.message ?: "Error al procesar cuenta seleccionada")
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
                GoogleSignIn.getClient(context, gso).signOut()
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
