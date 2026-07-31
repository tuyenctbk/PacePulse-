package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Real-Time Stride Cadence Detector using accelerometer peak detection.
 * Calculates live Steps Per Minute (SPM) while running.
 */
class StepCadenceDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _currentSpm = MutableStateFlow(0)
    val currentSpm: StateFlow<Int> = _currentSpm

    private val _totalSteps = MutableStateFlow(0)
    val totalSteps: StateFlow<Int> = _totalSteps

    private val stepTimestamps = ArrayDeque<Long>()
    private var lastPeakTimeMs = 0L
    private val minStepIntervalMs = 250L // max 240 SPM
    
    // Simple low-pass filter state
    private var lastMagnitude = 0.0
    private var isRising = false
    private val peakThreshold = 12.0 // m/s^2 magnitude threshold for step peak
    
    private var idleJob: Job? = null

    fun startListening() {
        stepTimestamps.clear()
        _currentSpm.value = 0
        _totalSteps.value = 0
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        stepTimestamps.clear()
        idleJob?.cancel()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        val now = System.currentTimeMillis()

        // Peak detection algorithm with hysterisis
        if (magnitude > peakThreshold && magnitude > lastMagnitude && !isRising) {
            isRising = true
        } else if (magnitude < lastMagnitude && isRising) {
            isRising = false
            if (now - lastPeakTimeMs > minStepIntervalMs) {
                lastPeakTimeMs = now
                onStepDetected(now)
            }
        }
        lastMagnitude = magnitude
    }

    private fun onStepDetected(timestampMs: Long) {
        _totalSteps.value += 1
        stepTimestamps.addLast(timestampMs)

        // Keep timestamps within last 10 seconds to compute sliding window SPM
        val windowCutoff = timestampMs - 10000L
        while (stepTimestamps.isNotEmpty() && stepTimestamps.first() < windowCutoff) {
            stepTimestamps.removeFirst()
        }

        if (stepTimestamps.size >= 2) {
            val durationSeconds = (stepTimestamps.last() - stepTimestamps.first()) / 1000.0
            if (durationSeconds > 1.0) {
                val spm = ((stepTimestamps.size - 1) / durationSeconds * 60.0).toInt()
                _currentSpm.value = spm.coerceIn(0, 260)
            }
        }
        
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(2500L)
            _currentSpm.value = 0
            stepTimestamps.clear()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
