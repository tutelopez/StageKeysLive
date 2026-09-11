package com.tutelopezmusic.stagekeyslive

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager

private const val TAG = "InAppReviewManager"
private const val PREFS_NAME = "review_prefs"
private const val KEY_SESSION_COUNT = "session_count"
private const val KEY_FIRST_LAUNCH = "first_launch_time"
private const val KEY_REVIEW_REQUESTED = "review_requested"

class InAppReviewManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordSession() {
        val editor = prefs.edit()
        if (prefs.getLong(KEY_FIRST_LAUNCH, 0L) == 0L) {
            editor.putLong(KEY_FIRST_LAUNCH, System.currentTimeMillis())
        }
        val currentSessions = prefs.getInt(KEY_SESSION_COUNT, 0)
        editor.putInt(KEY_SESSION_COUNT, currentSessions + 1)
        editor.apply()
        Log.d(TAG, "Recorded session: ${currentSessions + 1}")
    }

    fun shouldRequestReview(): Boolean {
        val alreadyRequested = prefs.getBoolean(KEY_REVIEW_REQUESTED, false)
        if (alreadyRequested) {
            return false
        }

        val sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0)
        val firstLaunch = prefs.getLong(KEY_FIRST_LAUNCH, 0L)
        val daysSinceFirstLaunch = if (firstLaunch > 0L) {
            (System.currentTimeMillis() - firstLaunch) / (1000L * 60 * 60 * 24)
        } else {
            0L
        }

        Log.d(TAG, "Review check - Sessions: $sessionCount, Days: $daysSinceFirstLaunch, AlreadyRequested: $alreadyRequested")
        return sessionCount >= 3 && daysSinceFirstLaunch >= 5
    }

    fun requestReview(activity: Activity) {
        prefs.edit().putBoolean(KEY_REVIEW_REQUESTED, true).apply()
        Log.i(TAG, "Requesting in-app review flow...")

        val manager = if (BuildConfig.DEBUG) {
            FakeReviewManager(activity)
        } else {
            ReviewManagerFactory.create(activity)
        }

        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = manager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    Log.i(TAG, "In-app review flow completed successfully.")
                }
            } else {
                Log.w(TAG, "In-app review request failed: ${task.exception?.message}")
            }
        }
    }
}
