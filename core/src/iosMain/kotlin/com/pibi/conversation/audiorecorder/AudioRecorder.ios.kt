package com.pibi.conversation.audiorecorder

import com.pibi.conversation.AppConfig
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.get
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.setActive
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
actual object AudioRecorder
{
    // Must match the JVM recorder and what the STT backend expects:
    // 16 kHz, 16-bit signed, mono, little-endian PCM.
    private const val TARGET_SAMPLE_RATE = 16000.0

    private fun calculateRms(audioData: ByteArray): Double
    {
        val sampleCount = audioData.size / 2
        if (sampleCount == 0) return 0.0

        var sum = 0.0
        var i = 0
        repeat(sampleCount) {
            val lo = audioData[i].toInt() and 0xFF
            val hi = audioData[i + 1].toInt()
            val sample = (hi shl 8) or lo
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        return sqrt(sum / sampleCount)
    }

    // The input tap delivers Float32 buffers at the hardware rate (usually 48 kHz);
    // resample with linear interpolation and convert to Int16 little-endian.
    private fun convertToPcm16Mono16k(buffer: AVAudioPCMBuffer, sourceRate: Double): ByteArray?
    {
        val channels = buffer.floatChannelData ?: return null
        val src = channels[0] ?: return null
        val frames = buffer.frameLength.toInt()
        if (frames == 0) return null

        val ratio = sourceRate / TARGET_SAMPLE_RATE
        val outFrames = (frames / ratio).toInt()
        if (outFrames == 0) return null

        val out = ByteArray(outFrames * 2)
        for (i in 0 until outFrames)
        {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = minOf(i0 + 1, frames - 1)
            val frac = (pos - i0).toFloat()
            val sample = src[i0] * (1f - frac) + src[i1] * frac
            val s = (sample.coerceIn(-1f, 1f) * 32767f).toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    actual fun startRecording(): Flow<ByteArray> = callbackFlow {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null
        )
        session.setActive(true, null)

        val engine = AVAudioEngine()
        val input = engine.inputNode
        val format = input.outputFormatForBus(0u)
        val sourceRate = format.sampleRate

        session.requestRecordPermission { granted ->
            if (!granted)
            {
                close(IllegalStateException("Microphone permission denied"))
                return@requestRecordPermission
            }

            input.installTapOnBus(0u, 4096u, format) { buffer, _ ->
                val pcmBuffer = buffer ?: return@installTapOnBus
                val chunk = convertToPcm16Mono16k(pcmBuffer, sourceRate) ?: return@installTapOnBus

                val rms = calculateRms(chunk)
                if (rms > AppConfig.AUDIO_THRESHOLD)
                {
                    trySend(chunk)
                } else
                {
                    trySend(ByteArray(0))
                }
            }

            engine.prepare()
            if (engine.startAndReturnError(null))
            {
                println("🎙️ Mikrofon wystartował (iOS, ${sourceRate.toInt()} Hz -> 16000 Hz)...")
            } else
            {
                close(IllegalStateException("Failed to start AVAudioEngine"))
            }
        }

        awaitClose {
            input.removeTapOnBus(0u)
            engine.stop()
            println("🛑 Mikrofon został zwolniony i zamknięty (iOS).")
        }
    }
}
