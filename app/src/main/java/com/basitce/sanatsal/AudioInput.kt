package com.basitce.sanatsal

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

class AudioInput {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val bufferSize: Int
    private val sampleRate = 44100
    
    @Volatile var amplitude = 0f

    init {
        bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording) return
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Mic might be in use or permission denied
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            thread(start = true) {
                val buffer = ShortArray(bufferSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (read > 0) {
                        var maxVal = 0
                        for (i in 0 until read) {
                            val valAbs = abs(buffer[i].toInt())
                            if (valAbs > maxVal) maxVal = valAbs
                        }
                        // Normalize 0..1 (approximate max for 16-bit audio is 32767)
                        var amp = maxVal / 32767f
                        // Noise gate
                        if (amp < 0.05f) amp = 0f
                        
                        // Smooth decay
                        amplitude = max(amp, amplitude * 0.9f)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
