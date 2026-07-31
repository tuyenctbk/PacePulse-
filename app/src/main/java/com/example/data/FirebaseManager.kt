package com.example.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseManager private constructor(context: Context) {

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _syncStatus = MutableStateFlow("Initialized")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    init {
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            analytics = FirebaseAnalytics.getInstance(context)
            crashlytics = FirebaseCrashlytics.getInstance()

            crashlytics?.setCustomKey("app_version", "1.0.0")
            crashlytics?.setCustomKey("app_name", "PacePulse")
            
            _isCloudSyncEnabled.value = true
            _syncStatus.value = "Firebase Ready"
            signInAnonymously()
            
            logEvent("app_launched", null)
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Firebase initialization deferred or google-services.json not configured: ${e.message}")
            _isCloudSyncEnabled.value = false
            _syncStatus.value = "Offline Mode"
        }
    }

    private fun signInAnonymously() {
        val a = auth ?: return
        if (a.currentUser == null) {
            a.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = a.currentUser?.uid
                    _syncStatus.value = "Cloud Sync Connected (${uid?.take(6)}...)"
                    Log.d("FirebaseManager", "Anonymous sign-in success: $uid")
                    analytics?.setUserId(uid)
                    crashlytics?.setUserId(uid ?: "anonymous")
                } else {
                    _syncStatus.value = "Sync Error: ${task.exception?.message}"
                }
            }
        } else {
            val uid = a.currentUser?.uid
            _syncStatus.value = "Cloud Sync Connected (${uid?.take(6)}...)"
            analytics?.setUserId(uid)
            crashlytics?.setUserId(uid ?: "anonymous")
        }
    }

    // Analytics: Screen Tracking
    fun trackScreenView(screenName: String) {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenName)
            }
            analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            crashlytics?.setCustomKey("current_screen", screenName)
            Log.d("FirebaseManager", "Tracked screen view: $screenName")
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Analytics trackScreenView error: ${e.message}")
        }
    }

    // Analytics: Custom Event Tracking
    fun logEvent(eventName: String, params: Bundle? = null) {
        try {
            analytics?.logEvent(eventName, params)
            Log.d("FirebaseManager", "Logged event: $eventName")
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Analytics logEvent error: ${e.message}")
        }
    }

    fun logPacingStarted(targetSpm: Int, soundType: String) {
        val bundle = Bundle().apply {
            putInt("target_spm", targetSpm)
            putString("sound_type", soundType)
        }
        logEvent("pacing_started", bundle)
        try {
            crashlytics?.setCustomKey("is_pacing", true)
            crashlytics?.setCustomKey("active_target_spm", targetSpm)
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Crashlytics update error: ${e.message}")
        }
    }

    fun logPacingStopped(durationSeconds: Long, totalSteps: Int, accuracy: Int) {
        val bundle = Bundle().apply {
            putLong("duration_seconds", durationSeconds)
            putInt("total_steps", totalSteps)
            putInt("accuracy_percentage", accuracy)
        }
        logEvent("pacing_stopped", bundle)
        try {
            crashlytics?.setCustomKey("is_pacing", false)
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Crashlytics update error: ${e.message}")
        }
    }

    // Crashlytics: Exception & Log Tracking
    fun recordNonFatalException(throwable: Throwable, message: String? = null) {
        try {
            if (message != null) {
                crashlytics?.log(message)
            }
            crashlytics?.recordException(throwable)
            Log.d("FirebaseManager", "Recorded exception to Crashlytics: ${throwable.message}")
        } catch (e: Exception) {
            Log.w("FirebaseManager", "Crashlytics recordNonFatalException error: ${e.message}")
        }
    }

    suspend fun syncSessionToCloud(session: RunSession) {
        val db = firestore ?: return
        val userId = auth?.currentUser?.uid ?: "anonymous_runner"

        try {
            val sessionMap = mapOf(
                "id" to session.id,
                "timestamp" to session.timestamp,
                "targetSpm" to session.targetSpm,
                "avgDetectedSpm" to session.avgDetectedSpm,
                "durationSeconds" to session.durationSeconds,
                "totalSteps" to session.totalSteps,
                "soundTypeName" to session.soundTypeName,
                "accuracyPercentage" to session.accuracyPercentage,
                "userId" to userId
            )

            db.collection("users")
                .document(userId)
                .collection("run_sessions")
                .document(session.id.toString())
                .set(sessionMap)
                .await()

            _syncStatus.value = "Session Synced to Cloud"
            Log.d("FirebaseManager", "Successfully backed up session ${session.id} to Firestore")

            // Log Analytics Event for Cloud Backup
            val bundle = Bundle().apply {
                putLong("session_id", session.id)
                putInt("target_spm", session.targetSpm)
            }
            logEvent("session_cloud_synced", bundle)

        } catch (e: Exception) {
            Log.e("FirebaseManager", "Error syncing session to Firestore: ${e.message}")
            _syncStatus.value = "Sync Failed: Local Saved"
            recordNonFatalException(e, "Error syncing session to Firestore")
        }
    }

    suspend fun fetchCloudSessions(): List<RunSession> {
        val db = firestore ?: return emptyList()
        val userId = auth?.currentUser?.uid ?: return emptyList()

        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("run_sessions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                val id = doc.getLong("id") ?: return@mapNotNull null
                val timestamp = doc.getLong("timestamp") ?: 0L
                val targetSpm = doc.getLong("targetSpm")?.toInt() ?: 180
                val avgDetectedSpm = doc.getLong("avgDetectedSpm")?.toInt() ?: 180
                val durationSeconds = doc.getLong("durationSeconds") ?: 0L
                val totalSteps = doc.getLong("totalSteps")?.toInt() ?: 0
                val soundTypeName = doc.getString("soundTypeName") ?: "Woodblock"
                val accuracyPercentage = doc.getLong("accuracyPercentage")?.toInt() ?: 100

                RunSession(
                    id = id,
                    timestamp = timestamp,
                    targetSpm = targetSpm,
                    avgDetectedSpm = avgDetectedSpm,
                    durationSeconds = durationSeconds,
                    totalSteps = totalSteps,
                    soundTypeName = soundTypeName,
                    accuracyPercentage = accuracyPercentage
                )
            }
        } catch (e: Exception) {
            Log.e("FirebaseManager", "Failed to fetch cloud sessions: ${e.message}")
            recordNonFatalException(e, "Failed to fetch cloud sessions")
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var instance: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
