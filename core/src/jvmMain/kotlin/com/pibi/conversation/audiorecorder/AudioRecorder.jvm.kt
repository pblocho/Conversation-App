package com.pibi.conversation.audiorecorder

import com.pibi.conversation.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt

actual object AudioRecorder
{

    private fun calculateRms(audioData: ByteArray): Double
    {
        val shorts = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)

        var sum = 0.0
        for (sample in shorts)
        {
            sum += sample * sample
        }
        return sqrt(sum / shorts.size)
    }

    actual fun startRecording(): Flow<ByteArray> = callbackFlow {
        val format = AudioFormat(16000f, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info))
        {
            close(IllegalStateException("Mikrofon nie wspiera wymaganego formatu."))
            return@callbackFlow
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        println("🎙️ Mikrofon wystartował...")

        val job = launch(Dispatchers.IO) {
            try
            {
                val buffer = ByteArray(4096)
                while (true)
                {
                    val bytesRead = line.read(buffer, 0, buffer.size)
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
                line.stop()
                line.close()
                println("🛑 Mikrofon został zwolniony i zamknięty.")
            }
        }

        awaitClose {
            job.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
