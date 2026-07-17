package com.pibi.conversation.audiorecorder

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.pibi.conversation.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

actual object AudioRecorder
{
    // Must match the JVM/iOS recorders and what the STT backend expects:
    // 16 kHz, 16-bit signed, mono, little-endian PCM.
    private const val SAMPLE_RATE = 16000

    private fun calculateRms(audioData: ByteArray): Double
    {
        val shorts = ShortArray(audioData.size / 2)
        if (shorts.isEmpty()) return 0.0
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        var sum = 0.0
        for (sample in shorts)
        {
            sum += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sum / shorts.size)
    }

    // RECORD_AUDIO is requested by MainActivity before the pipeline starts;
    // if it is missing, AudioRecord stays uninitialized and the flow closes with an error.
    @SuppressLint("MissingPermission")
    actual fun startRecording(): Flow<ByteArray> = callbackFlow {
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
        println("🎙️ Mikrofon wystartował (Android, $SAMPLE_RATE Hz)...")

        val job = launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            try
            {
                while (isActive)
                {
                    val bytesRead = record.read(buffer, 0, buffer.size)
                    if (bytesRead > 0)
                    {
                        val currentBuffer = buffer.copyOf(bytesRead)

                        val rms = calculateRms(currentBuffer)
                        if (rms > AppConfig.AUDIO_THRESHOLD)
                        {
                            trySend(currentBuffer)
                        } else
                        {
                            trySend(ByteArray(0))
                        }
                    } else if (bytesRead < 0)
                    {
                        break
                    }
                }
            } finally
            {
                record.stop()
                record.release()
                println("🛑 Mikrofon został zwolniony i zamknięty (Android).")
            }
        }

        awaitClose {
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
