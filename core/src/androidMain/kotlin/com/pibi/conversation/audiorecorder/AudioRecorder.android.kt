package com.pibi.conversation.audiorecorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Must match the JVM/iOS recorders and what the STT backend expects:
// 16 kHz, 16-bit signed, mono, little-endian PCM.
private const val SAMPLE_RATE = 16000

// RECORD_AUDIO is requested by MainActivity before the pipeline starts;
// if it is missing, AudioRecord stays uninitialized and the flow closes with an error.
@SuppressLint("MissingPermission")
internal actual fun capturePcm(): Flow<ByteArray> = callbackFlow {
    val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val record = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBufferSize, 8192)
    )

    if (record.state != AudioRecord.STATE_INITIALIZED)
    {
        record.release()
        close(IllegalStateException("Microphone unavailable — is RECORD_AUDIO permission granted?"))
        return@callbackFlow
    }

    record.startRecording()
    println("🎙️ Microphone started (Android, $SAMPLE_RATE Hz)...")

    val job = launch(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        try
        {
            while (isActive)
            {
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0)
                {
                    // send, not trySend: suspending gives cancellation somewhere to land, and
                    // applies backpressure instead of dropping audio silently.
                    send(buffer.copyOf(bytesRead))
                } else if (bytesRead < 0)
                {
                    break
                }
            }
        } finally
        {
            record.stop()
            record.release()
            println("🛑 Microphone released (Android).")
        }
    }

    awaitClose {
        job.cancel()
    }
}.flowOn(Dispatchers.IO)
