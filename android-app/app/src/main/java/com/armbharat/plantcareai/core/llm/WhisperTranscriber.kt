package com.armbharat.plantcareai.core.llm

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.ensureActive

class WhisperTranscriber(context: Context) {
    private val assets = context.applicationContext.assets

    // Input: 16 kHz mono PCM samples normalized to -1.0 through 1.0.
    @SuppressLint("MissingPermission") // Caller checks RECORD_AUDIO before calling.
    suspend fun recordAndTranscribe(): String {
        val samples = withContext(Dispatchers.IO) {
            val sampleRate = 16_000
            val channel = AudioFormat.CHANNEL_IN_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minimumBuffer = AudioRecord.getMinBufferSize(
                sampleRate, channel, encoding
            )
            check(minimumBuffer > 0) { "16 kHz recording is unavailable." }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channel,
                encoding,
                maxOf(minimumBuffer, 4096)
            )

            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                    "Could not initialize the microphone."
                }
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Could not start microphone recording."
                }

                val audio = FloatArray(sampleRate * 6)
                val buffer = ShortArray(1024)
                var offset = 0

                while (offset < audio.size) {
                    coroutineContext.ensureActive()
                    val count = recorder.read(
                        buffer,
                        0,
                        minOf(buffer.size, audio.size - offset)
                    )
                    check(count > 0) { "Microphone read failed ($count)." }

                    for (index in 0 until count) {
                        audio[offset + index] = buffer[index] / 32768f
                    }
                    offset += count
                }

                audio
            } finally {
                try {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                } finally {
                    recorder.release()
                }
            }
        }

        return transcribe(samples)
    }
    suspend fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""

        return withContext(Dispatchers.IO) {
            val whisper = WhisperContext.createContextFromAsset(
                assets,
                "whisper/ggml-base.bin"
            )

            try {
                whisper.transcribeData(samples, false).trim()
            } finally {
                withContext(NonCancellable) {
                    whisper.release()
                }
            }
        }
    }
}