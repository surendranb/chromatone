package com.fuseforge.chromatone.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread

class NoisePlayer(private val bufferProvider: (Int) -> ShortArray, private val volume: Float = 0.7f) {
    private var audioTrack: AudioTrack? = null
    private var playThread: Thread? = null
    @Volatile private var isPlaying = false

    fun start() {
        if (isPlaying) return
        isPlaying = true
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
        playThread = thread(start = true) {
            while (isPlaying) {
                val noise = bufferProvider(bufferSize)
                // Apply volume scaling
                for (i in noise.indices) {
                    noise[i] = (noise[i] * volume).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                audioTrack?.write(noise, 0, noise.size)
            }
        }
    }

    fun stop() {
        isPlaying = false
        playThread?.join(200)
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        playThread = null
    }
} 