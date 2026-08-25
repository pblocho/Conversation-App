package com.pibi.conversation

import com.pibi.conversation.grpc.AudioChunk
import com.pibi.conversation.grpc.AudioData
import com.pibi.conversation.grpc.SttService
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.Transcript
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
import kotlinx.rpc.grpc.server.GrpcServer
import kotlinx.rpc.registerService

/**
 * Fake STT/TTS backend for end-to-end tests (see scripts/android_e2e.sh).
 * Speaks the real proto contracts on the real ports:
 *  - STT (:8001): echoes a transcript for every audio chunk / end-of-utterance marker
 *  - TTS (:8000): answers every text piece with a short WAV tone
 */
private class EchoSttService : SttService {
    override fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript> = flow {
        message.collect { chunk ->
            if (chunk.endOfUtterance) {
                println("E2E STT: end of utterance")
                emit(Transcript { text = "utterance-end" })
            } else {
                println("E2E STT: ${chunk.data.size} bytes of audio")
                emit(Transcript { text = "heard ${chunk.data.size} bytes" })
            }
        }
    }
}

private class ToneTtsService : TtsService {
    override fun Synthesize(message: Flow<TextPiece>): Flow<AudioData> = flow {
        message.collect { piece ->
            println("E2E TTS: synthesizing \"${piece.text}\"")
            emit(AudioData {
                data = ByteString(*toneWav())
                text = piece.text
            })
        }
    }
}

/** 300 ms, 440 Hz, 16 kHz mono 16-bit PCM WAV. */
private fun toneWav(sampleRate: Int = 16000, durationMs: Int = 300, frequency: Double = 440.0): ByteArray {
    val samples = sampleRate * durationMs / 1000
    val dataSize = samples * 2
    val out = ByteArrayOutputStream()
    fun int32(v: Int) { out.write(v); out.write(v shr 8); out.write(v shr 16); out.write(v shr 24) }
    fun int16(v: Int) { out.write(v); out.write(v shr 8) }

    out.write("RIFF".toByteArray()); int32(36 + dataSize); out.write("WAVE".toByteArray())
    out.write("fmt ".toByteArray()); int32(16); int16(1); int16(1)
    int32(sampleRate); int32(sampleRate * 2); int16(2); int16(16)
    out.write("data".toByteArray()); int32(dataSize)
    for (i in 0 until samples) {
        int16((sin(2 * PI * frequency * i / sampleRate) * 8000).toInt())
    }
    return out.toByteArray()
}

/**
 * Ports default to the real ones, so `adb reverse tcp:8001 tcp:8001` needs no arguments.
 * Pass `--args="<sttPort> <ttsPort>"` to run beside a real backend that already holds those
 * ports, and point the device at them with `adb reverse tcp:8001 tcp:<sttPort>`.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val sttPort = args.getOrNull(0)?.toInt() ?: AppConfig.STT_PORT
    val ttsPort = args.getOrNull(1)?.toInt() ?: AppConfig.TTS_PORT

    val sttServer = GrpcServer(sttPort) {
        services { registerService<SttService> { EchoSttService() } }
    }.start()
    val ttsServer = GrpcServer(ttsPort) {
        services { registerService<TtsService> { ToneTtsService() } }
    }.start()

    println("E2E gRPC test server ready: STT :$sttPort, TTS :$ttsPort")
    sttServer.awaitTermination()
    ttsServer.awaitTermination()
}
