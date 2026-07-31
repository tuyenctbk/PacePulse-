package com.example.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Low-latency Haptic Engine for wrist and device pulse vibration.
 */
class CadenceHaptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var isHapticEnabled: Boolean = true

    fun setHapticEnabled(enabled: Boolean) {
        this.isHapticEnabled = enabled
    }

    fun playPulse(isAccent: Boolean = false) {
        if (!isHapticEnabled || vibrator == null || !vibrator.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = if (isAccent) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                } else {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                }
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val duration = if (isAccent) 25L else 12L
                val amplitude = if (isAccent) 255 else 180
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (isAccent) 25L else 12L)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
