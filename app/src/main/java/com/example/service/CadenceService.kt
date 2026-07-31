package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.CadenceHaptics
import com.example.audio.CadenceSoundPool
import com.example.audio.SoundType
import com.example.sensor.StepCadenceDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CadenceService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var soundPool: CadenceSoundPool
    private lateinit var haptics: CadenceHaptics
    private lateinit var stepDetector: StepCadenceDetector

    private var pacingJob: Job? = null

    // Service State
    private val _isPacing = MutableStateFlow(false)
    val isPacing: StateFlow<Boolean> = _isPacing

    private val _targetSpm = MutableStateFlow(180)
    val targetSpm: StateFlow<Int> = _targetSpm

    private val _soundType = MutableStateFlow(SoundType.WOODBLOCK)
    val soundType: StateFlow<SoundType> = _soundType

    private val _isHapticEnabled = MutableStateFlow(true)
    val isHapticEnabled: StateFlow<Boolean> = _isHapticEnabled

    private val _volume = MutableStateFlow(0.9f)
    val volume: StateFlow<Float> = _volume

    private val _accentInterval = MutableStateFlow(4) // Accent every 4 steps (e.g., 2 full strides)
    val accentInterval: StateFlow<Int> = _accentInterval

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _isAutoPauseEnabled = MutableStateFlow(false)
    val isAutoPauseEnabled: StateFlow<Boolean> = _isAutoPauseEnabled

    private val _isAutoPaused = MutableStateFlow(false)
    val isAutoPaused: StateFlow<Boolean> = _isAutoPaused

    private var timerJob: Job? = null
    private var stepCountAtStart = 0
    private var autoPauseMonitorJob: Job? = null

    val currentSpm: StateFlow<Int>
        get() = stepDetector.currentSpm

    val totalSteps: StateFlow<Int>
        get() = stepDetector.totalSteps

    inner class LocalBinder : Binder() {
        fun getService(): CadenceService = this@CadenceService
    }

    override fun onCreate() {
        super.onCreate()
        soundPool = CadenceSoundPool(this)
        haptics = CadenceHaptics(this)
        stepDetector = StepCadenceDetector(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startPacing()
            ACTION_STOP -> stopPacing()
        }
        return START_STICKY
    }

    fun setTargetSpm(spm: Int) {
        val clamped = spm.coerceIn(120, 240)
        _targetSpm.value = clamped
        if (_isPacing.value) {
            restartPacingLoop()
        }
    }

    fun setSoundType(type: SoundType) {
        _soundType.value = type
    }

    fun setHapticEnabled(enabled: Boolean) {
        _isHapticEnabled.value = enabled
        haptics.setHapticEnabled(enabled)
    }

    fun setVolume(vol: Float) {
        _volume.value = vol
        soundPool.setVolume(vol)
    }

    fun setAccentInterval(interval: Int) {
        _accentInterval.value = interval
    }

    fun setAutoPauseEnabled(enabled: Boolean) {
        _isAutoPauseEnabled.value = enabled
        if (!enabled) _isAutoPaused.value = false
    }

    fun startPacing() {
        if (_isPacing.value) return
        _isPacing.value = true
        _elapsedSeconds.value = 0L
        _isAutoPaused.value = false

        startForeground(NOTIFICATION_ID, buildNotification())
        stepDetector.startListening()

        startPacingLoop()
        startTimerLoop()
        startAutoPauseMonitor()
    }

    fun stopPacing() {
        _isPacing.value = false
        _isAutoPaused.value = false
        pacingJob?.cancel()
        timerJob?.cancel()
        autoPauseMonitorJob?.cancel()
        stepDetector.stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startAutoPauseMonitor() {
        autoPauseMonitorJob?.cancel()
        autoPauseMonitorJob = serviceScope.launch {
            stepDetector.currentSpm.collect { spm ->
                if (_isAutoPauseEnabled.value && _isPacing.value) {
                    _isAutoPaused.value = (spm == 0)
                } else {
                    _isAutoPaused.value = false
                }
            }
        }
    }

    private fun startPacingLoop() {
        pacingJob?.cancel()
        pacingJob = serviceScope.launch {
            var tickIndex = 0
            while (isActive && _isPacing.value) {
                val spm = _targetSpm.value
                val intervalMs = (60.0 / spm * 1000.0).toLong()

                tickIndex++
                val isAccent = _accentInterval.value > 0 && (tickIndex % _accentInterval.value == 1)

                if (!_isAutoPaused.value) {
                    // Trigger low-latency sound and haptic simultaneously
                    soundPool.playTick(_soundType.value, isAccent)
                    haptics.playPulse(isAccent)
                }

                delay(intervalMs)
            }
        }
    }

    private fun restartPacingLoop() {
        if (_isPacing.value) {
            startPacingLoop()
        }
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _isPacing.value) {
                delay(1000L)
                _elapsedSeconds.value += 1
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PacePulse Cadence Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Provides background running cadence audio and haptic pacing."
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PacePulse Active — ${_targetSpm.value} SPM")
            .setContentText("Screen-off background cadence pacing running...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        stopPacing()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "pacepulse_cadence_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
    }
}
