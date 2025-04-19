package com.fuseforge.chromatone.audio

import com.fuseforge.chromatone.NoiseType
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

object NoiseGenerator {
    /**
     * Generate a buffer of white noise samples (PCM 16-bit, mono)
     * @param numSamples Number of samples to generate
     * @return ShortArray of PCM samples
     */
    fun generateWhiteNoise(numSamples: Int): ShortArray {
        val buffer = ShortArray(numSamples)
        for (i in buffer.indices) {
            buffer[i] = (Random.nextDouble(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a buffer of pink noise samples (simple Voss-McCartney algorithm)
     */
    fun generatePinkNoise(numSamples: Int): ShortArray {
        val buffer = ShortArray(numSamples)
        val rows = 16
        val random = Random.Default
        val white = DoubleArray(rows)
        var sum = 0.0
        for (i in buffer.indices) {
            val k = random.nextInt(rows)
            white[k] = random.nextDouble(-1.0, 1.0)
            sum = white.sum()
            buffer[i] = ((sum / rows) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a buffer of brown noise samples (integrated white noise)
     */
    fun generateBrownNoise(numSamples: Int): ShortArray {
        val buffer = ShortArray(numSamples)
        var last = 0.0
        for (i in buffer.indices) {
            val white = Random.nextDouble(-1.0, 1.0)
            last = (last + (0.02 * white)).coerceIn(-1.0, 1.0)
            buffer[i] = (last * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a buffer of green noise samples (bandpass filtered white noise, rough approximation)
     */
    fun generateGreenNoise(numSamples: Int, sampleRate: Int = 44100): ShortArray {
        // Green noise is often defined as white noise filtered around 500 Hz
        val buffer = ShortArray(numSamples)
        val freq = 500.0
        for (i in buffer.indices) {
            val t = i / sampleRate.toDouble()
            val white = Random.nextDouble(-1.0, 1.0)
            val mod = sin(2 * PI * freq * t)
            buffer[i] = ((white * mod) * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a buffer of blue noise samples (differentiated white noise)
     */
    fun generateBlueNoise(numSamples: Int): ShortArray {
        val buffer = ShortArray(numSamples)
        var last = 0.0
        for (i in buffer.indices) {
            val white = Random.nextDouble(-1.0, 1.0)
            val blue = white - last
            last = white
            buffer[i] = (blue * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Generate a buffer of violet noise samples (differentiated blue noise)
     */
    fun generateVioletNoise(numSamples: Int): ShortArray {
        val buffer = ShortArray(numSamples)
        var last = 0.0
        var lastBlue = 0.0
        for (i in buffer.indices) {
            val white = Random.nextDouble(-1.0, 1.0)
            val blue = white - last
            val violet = blue - lastBlue
            last = white
            lastBlue = blue
            buffer[i] = (violet * Short.MAX_VALUE).toInt().toShort()
        }
        return buffer
    }

    /**
     * Get a buffer for the given noise type
     */
    fun getNoiseBuffer(type: NoiseType, numSamples: Int): ShortArray = when (type) {
        NoiseType.White -> generateWhiteNoise(numSamples)
        NoiseType.Pink -> generatePinkNoise(numSamples)
        NoiseType.Brown -> generateBrownNoise(numSamples)
        NoiseType.Green -> generateGreenNoise(numSamples)
        NoiseType.Blue -> generateBlueNoise(numSamples)
        NoiseType.Violet -> generateVioletNoise(numSamples)
    }
} 