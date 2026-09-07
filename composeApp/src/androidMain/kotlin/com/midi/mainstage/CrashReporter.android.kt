package com.midi.mainstage

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

actual object CrashReporter {
    private const val TAG = "StageKeysCrash"

    private val crashlytics: FirebaseCrashlytics?
        get() = try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Throwable) {
            null
        }

    actual fun log(message: String) {
        Log.i(TAG, message)
        try {
            crashlytics?.log(message)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }

    actual fun recordException(throwable: Throwable, tag: String?) {
        Log.e(TAG, "[${tag ?: "Error"}] ${throwable.message}", throwable)
        try {
            if (tag != null) {
                crashlytics?.setCustomKey("error_tag", tag)
            }
            crashlytics?.recordException(throwable)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }

    actual fun setCustomKey(key: String, value: String) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }

    actual fun setCustomKey(key: String, value: Boolean) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }

    actual fun setCustomKey(key: String, value: Int) {
        try {
            crashlytics?.setCustomKey(key, value)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }

    actual fun setUserId(userId: String) {
        try {
            crashlytics?.setUserId(userId)
        } catch (e: Throwable) {
            // Ignore failure
        }
    }
}
