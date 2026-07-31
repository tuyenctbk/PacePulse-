package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundType
import com.example.data.AppDatabase
import com.example.data.FirebaseManager
import com.example.data.RunSession
import com.example.service.CadenceService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

class CadenceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.runSessionDao()
    private val firebaseManager = FirebaseManager.getInstance(application)
    private val suggestionManager = SuggestionManager(application)

    val cloudSyncStatus: StateFlow<String> = firebaseManager.syncStatus
    val isCloudSyncEnabled: StateFlow<Boolean> = firebaseManager.isCloudSyncEnabled
    
    private val _suggestionType = MutableStateFlow(SuggestionType.NONE)
    val suggestionType: StateFlow<SuggestionType> = _suggestionType.asStateFlow()

    val runHistory: StateFlow<List<RunSession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSteps: StateFlow<Int?> = dao.getTotalStepsCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDurationSeconds: StateFlow<Long?> = dao.getTotalDurationSeconds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val sessionCount: StateFlow<Int> = dao.getSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var cadenceService: CadenceService? = null
    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    // Observable States mirroring service
    val isPacing = MutableStateFlow(false)
    val targetSpm = MutableStateFlow(180)
    val currentSpm = MutableStateFlow(0)
    val totalSessionSteps = MutableStateFlow(0)
    val elapsedSeconds = MutableStateFlow(0L)
    val soundType = MutableStateFlow(SoundType.WOODBLOCK)
    val isHapticEnabled = MutableStateFlow(true)
    val isAutoPauseEnabled = MutableStateFlow(false)
    val isAutoPaused = MutableStateFlow(false)
    val volume = MutableStateFlow(0.9f)
    val accentInterval = MutableStateFlow(4)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CadenceService.LocalBinder
            cadenceService = binder.getService()
            _isBound.value = true

            // Collect state from service
            viewModelScope.launch {
                cadenceService?.isPacing?.collect { isPacing.value = it }
            }
            viewModelScope.launch {
                cadenceService?.targetSpm?.collect { targetSpm.value = it }
            }
            viewModelScope.launch {
                cadenceService?.currentSpm?.collect { currentSpm.value = it }
            }
            viewModelScope.launch {
                cadenceService?.totalSteps?.collect { totalSessionSteps.value = it }
            }
            viewModelScope.launch {
                cadenceService?.elapsedSeconds?.collect { elapsedSeconds.value = it }
            }
            viewModelScope.launch {
                cadenceService?.soundType?.collect { soundType.value = it }
            }
            viewModelScope.launch {
                cadenceService?.isHapticEnabled?.collect { isHapticEnabled.value = it }
            }
            viewModelScope.launch {
                cadenceService?.isAutoPauseEnabled?.collect { isAutoPauseEnabled.value = it }
            }
            viewModelScope.launch {
                cadenceService?.isAutoPaused?.collect { isAutoPaused.value = it }
            }
            viewModelScope.launch {
                cadenceService?.volume?.collect { volume.value = it }
            }
            viewModelScope.launch {
                cadenceService?.accentInterval?.collect { accentInterval.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cadenceService = null
            _isBound.value = false
        }
    }

    init {
        bindCadenceService()
    }

    fun bindCadenceService() {
        val context = getApplication<Application>()
        val intent = Intent(context, CadenceService::class.java)
        context.startService(intent) // Ensure service is created
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun trackScreenView(screenName: String) {
        firebaseManager.trackScreenView(screenName)
    }

    fun togglePacing() {
        val service = cadenceService ?: return
        if (service.isPacing.value) {
            val elapsed = elapsedSeconds.value
            val target = targetSpm.value
            val avgDetected = currentSpm.value
            val steps = totalSessionSteps.value
            val type = soundType.value.displayName

            // Calculate accuracy percentage
            val diff = abs(avgDetected - target)
            val accuracy = if (avgDetected == 0) 100 else (100 - (diff * 100 / target)).coerceIn(0, 100)

            firebaseManager.logPacingStopped(elapsed, steps, accuracy)

            // Save run session to database if duration > 5 seconds
            if (elapsed >= 5L) {
                val session = RunSession(
                    timestamp = System.currentTimeMillis(),
                    targetSpm = target,
                    avgDetectedSpm = if (avgDetected > 0) avgDetected else target,
                    durationSeconds = elapsed,
                    totalSteps = if (steps > 0) steps else (target * elapsed / 60).toInt(),
                    soundTypeName = type,
                    accuracyPercentage = accuracy
                )
                viewModelScope.launch {
                    val id = dao.insertSession(session)
                    firebaseManager.syncSessionToCloud(session.copy(id = id))
                    
                    // Check for suggestions
                    val count = dao.getSessionCountValue()
                    if (suggestionManager.shouldShowRate(count)) {
                        _suggestionType.value = SuggestionType.RATE
                    } else if (suggestionManager.shouldShowShare(count)) {
                        _suggestionType.value = SuggestionType.SHARE
                    }
                }
            }
            service.stopPacing()
        } else {
            firebaseManager.logPacingStarted(targetSpm.value, soundType.value.displayName)
            service.startPacing()
        }
    }

    fun setTargetSpm(spm: Int) {
        targetSpm.value = spm
        cadenceService?.setTargetSpm(spm)
    }

    fun setSoundType(type: SoundType) {
        soundType.value = type
        cadenceService?.setSoundType(type)
    }

    fun setHapticEnabled(enabled: Boolean) {
        isHapticEnabled.value = enabled
        cadenceService?.setHapticEnabled(enabled)
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        isAutoPauseEnabled.value = enabled
        cadenceService?.setAutoPauseEnabled(enabled)
    }

    fun setVolume(vol: Float) {
        volume.value = vol
        cadenceService?.setVolume(vol)
    }

    fun setAccentInterval(interval: Int) {
        accentInterval.value = interval
        cadenceService?.setAccentInterval(interval)
    }

    fun deleteSession(session: RunSession) {
        viewModelScope.launch {
            dao.deleteSession(session)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            dao.deleteAllSessions()
        }
    }

    fun dismissSuggestion() {
        if (_suggestionType.value == SuggestionType.RATE) {
            suggestionManager.markRateShown()
        } else if (_suggestionType.value == SuggestionType.SHARE) {
            suggestionManager.markShareShown()
        }
        _suggestionType.value = SuggestionType.NONE
    }

    fun handleSuggestionAction(type: SuggestionType) {
        if (type == SuggestionType.RATE) {
            suggestionManager.markRated()
            // Logic to open Play Store (not implemented yet, just dismiss for now)
        } else if (type == SuggestionType.SHARE) {
            suggestionManager.markShareShown()
            // Logic to share (not implemented yet, just dismiss for now)
        }
        _suggestionType.value = SuggestionType.NONE
    }

    override fun onCleared() {
        if (_isBound.value) {
            getApplication<Application>().unbindService(connection)
            _isBound.value = false
        }
        super.onCleared()
    }
}
