package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

enum class SoundType(val displayName: String) {
    WOODBLOCK("Woodblock"),
    BEEP("Digital Beep"),
    CLICK("Metallic Click"),
    POP("Soft Pop")
}

/**
 * Low-latency PCM audio synthesizer for running cadence clicks.
 * Generates high-precision, short click waveforms dynamically to ensure
 * zero latency and 100% reliability with $0 external assets.
 */
class CadenceSoundPool(private val context: Context) {

    private val sampleRate = 44100
    private var volumeRatio: Float = 0.9f

    // Pre-synthesized PCM buffer byte arrays
    private val woodblockPcm: ByteArray by lazy { generateWoodblockPcm(isAccent = false) }
    private val woodblockAccentPcm: ByteArray by lazy { generateWoodblockPcm(isAccent = true) }
    
    private val beepPcm: ByteArray by lazy { generateBeepPcm(isAccent = false) }
    private val beepAccentPcm: ByteArray by lazy { generateBeepPcm(isAccent = true) }

    private val clickPcm: ByteArray by lazy { generateClickPcm(isAccent = false) }
    private val clickAccentPcm: ByteArray by lazy { generateClickPcm(isAccent = true) }

    private val popPcm: ByteArray by lazy { generatePopPcm(isAccent = false) }
    private val popAccentPcm: ByteArray by lazy { generatePopPcm(isAccent = true) }

    fun setVolume(volume: Float) {
        this.volumeRatio = volume.coerceIn(0f, 1f)
    }

    fun playTick(soundType: SoundType, isAccent: Boolean = false) {
        if (volumeRatio <= 0.01f) return

        val pcmData = when (soundType) {
            SoundType.WOODBLOCK -> if (isAccent) woodblockAccentPcm else woodblockPcm
            SoundType.BEEP -> if (isAccent) beepAccentPcm else beepPcm
            SoundType.CLICK -> if (isAccent) clickAccentPcm else clickPcm
            SoundType.POP -> if (isAccent) popAccentPcm else popPcm
        }

        playPcmData(pcmData)
    }

    private fun playPcmData(pcmData: ByteArray) {
        try {
            val bufferSize = pcmData.size
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            // Scale volume
            val finalData = if (volumeRatio < 0.99f) {
                scalePcmVolume(pcmData, volumeRatio)
            } else {
                pcmData
            }

            audioTrack.write(finalData, 0, finalData.size)
            audioTrack.play()

            // Release track after playback completes
            val durationMs = (pcmData.size / 2) * 1000L / sampleRate
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (e: Exception) {
                    // Ignore track cleanup exceptions
                }
            }, durationMs + 20)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scalePcmVolume(data: ByteArray, volume: Float): ByteArray {
        val result = ByteArray(data.size)
        for (i in 0 until data.size step 2) {
            if (i + 1 < data.size) {
                val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                val shortSample = sample.toShort()
                val scaledSample = (shortSample * volume).toInt().coerceIn(-32768, 32767).toShort()
                result[i] = (scaledSample.toInt() and 0xFF).toByte()
                result[i + 1] = ((scaledSample.toInt() shr 8) and 0xFF).toByte()
            }
        }
        return result
    }

    private fun generateWoodblockPcm(isAccent: Boolean): ByteArray {
        val durationMs = 25
        val numSamples = (sampleRate * durationMs / 1000)
        val pcm = ByteArray(numSamples * 2)
        val startFreq = if (isAccent) 1600.0 else 1200.0
        val endFreq = if (isAccent) 900.0 else 600.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * progress
            val envelope = Math.exp(-progress * 12.0) // Rapid decay
            val valSample = (Math.sin(2.0 * Math.PI * freq * t) * envelope * 28000.0).toInt().toShort()

            pcm[i * 2] = (valSample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((valSample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun generateBeepPcm(isAccent: Boolean): ByteArray {
        val durationMs = 20
        val numSamples = (sampleRate * durationMs / 1000)
        val pcm = ByteArray(numSamples * 2)
        val freq = if (isAccent) 1800.0 else 1100.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val envelope = Math.exp(-progress * 8.0)
            val valSample = (Math.sin(2.0 * Math.PI * freq * t) * envelope * 26000.0).toInt().toShort()

            pcm[i * 2] = (valSample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((valSample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun generateClickPcm(isAccent: Boolean): ByteArray {
        val durationMs = 15
        val numSamples = (sampleRate * durationMs / 1000)
        val pcm = ByteArray(numSamples * 2)
        val freq = if (isAccent) 2800.0 else 2200.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val envelope = Math.exp(-progress * 15.0)
            val noise = (Math.random() * 2.0 - 1.0) * 0.3
            val tone = Math.sin(2.0 * Math.PI * freq * t) * 0.7
            val valSample = ((tone + noise) * envelope * 29000.0).toInt().toShort()

            pcm[i * 2] = (valSample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((valSample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    private fun generatePopPcm(isAccent: Boolean): ByteArray {
        val durationMs = 30
        val numSamples = (sampleRate * durationMs / 1000)
        val pcm = ByteArray(numSamples * 2)
        val startFreq = if (isAccent) 550.0 else 400.0
        val endFreq = if (isAccent) 200.0 else 120.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * Math.pow(progress, 0.5)
            val envelope = Math.sin(Math.PI * progress) // Soft sine envelope
            val valSample = (Math.sin(2.0 * Math.PI * freq * t) * envelope * 27000.0).toInt().toShort()

            pcm[i * 2] = (valSample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((valSample.toInt() shr 8) and 0xFF).toByte()
        }
        return pcm
    }
}
