package com.pibi.conversation.networking

import com.pibi.conversation.grpc.AudioChunk
import com.pibi.conversation.grpc.SttService
import com.pibi.conversation.grpc.Transcript
import com.pibi.conversation.grpc.invoke
import com.pibi.conversation.networking.stt.SttClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.rpc.grpc.server.GrpcServer
import kotlinx.rpc.registerService
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

private class EchoSttService : SttService {
    override fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript> =
        message.transformWhile { chunk ->
            if (chunk.endOfUtterance) {
                emit(Transcript { text = "end"; isFinal = true })
                false
            } else {
                emit(Transcript { text = "${chunk.data.size} bytes" })
                true
            }
        }
}

/** Splits every utterance into three transcripts, the way a recognizer segments a long sentence. */
private class SegmentingSttService : SttService {
    override fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript> = flow {
        message.collect { chunk ->
            if (chunk.endOfUtterance) {
                emit(Transcript { text = "I went" })
                emit(Transcript { text = "to the cinema" })
                emit(Transcript { text = "yesterday"; isFinal = true })
            }
        }
    }
}

/** Reports every utterance as holding no speech, the way silence comes back. */
private class NothingHeardSttService : SttService {
    override fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript> = flow {
        message.collect { chunk ->
            if (chunk.endOfUtterance) emit(Transcript { isFinal = true })
        }
    }
}

/** Records what actually reached the wire, so the client's own protocol can be asserted. */
private class RecordingSttService(private val received: MutableList<String>) : SttService {
    override fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript> = flow {
        message.collect { chunk ->
            received += if (chunk.endOfUtterance) "end" else "data(${chunk.data.size})"
            emit(Transcript { text = "ok" })
        }
    }
}

/**
 * A port nobody else is on. The client takes its endpoint as a parameter, so these tests no longer
 * have to squat on the port the real backend uses — they can run while it is up.
 */
private fun freePort(): Int = ServerSocket(0).use { it.localPort }

class SttGrpcSmokeTest {
    @Test
    fun bidirectionalStreamingRoundTrip() = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services {
                registerService<SttService> { EchoSttService() }
            }
        }.start()

        val client = SttClient(port = port)
        try {
            // One collection is one stream, so there is no window in which a transcript could be
            // emitted before anyone is listening.
            val received = withTimeout(15_000) {
                client.streamAudioToStt(flowOf(byteArrayOf(1, 2, 3), ByteArray(0))).take(1).toList()
            }

            // The two transcripts the backend sent are one utterance — the client joins them and
            // emits once, when the backend flags the last one.
            assertEquals(listOf("3 bytes end"), received)
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }

    @Test
    fun segmentsOfOneUtteranceArriveAsOneQuestion() = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services { registerService<SttService> { SegmentingSttService() } }
        }.start()

        val client = SttClient(port = port)
        try {
            val received = withTimeout(15_000) {
                client.streamAudioToStt(flowOf(byteArrayOf(1, 2, 3), ByteArray(0))).take(1).toList()
            }

            // Joined on the flag, not on a timer: the caller gets a whole question, once.
            assertEquals(listOf("I went to the cinema yesterday"), received)
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }

    @Test
    fun anUtteranceWithNoSpeechIsReportedAtOnce() = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services { registerService<SttService> { NothingHeardSttService() } }
        }.start()

        val client = SttClient(port = port)
        try {
            val received = withTimeout(15_000) {
                client.streamAudioToStt(flowOf(byteArrayOf(1, 2, 3), ByteArray(0))).take(1).toList()
            }

            // An empty question rather than silence, so the caller stops waiting immediately
            // instead of sitting out its transcript timeout.
            assertEquals(listOf(""), received)
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }

    @Test
    fun silenceIsMarkedOnceUntilSpeechResumes() = runBlocking {
        val port = freePort()
        val received = CopyOnWriteArrayList<String>()
        val server = GrpcServer(port) {
            services {
                registerService<SttService> { RecordingSttService(received) }
            }
        }.start()

        val client = SttClient(port = port)
        try {
            withTimeout(15_000) {
                client.streamAudioToStt(
                    flowOf(
                        byteArrayOf(1, 2, 3),   // speech
                        ByteArray(0),           // silence  -> end of utterance
                        ByteArray(0),           // still silent -> suppressed
                        ByteArray(0),           // still silent -> suppressed
                        byteArrayOf(4, 5),      // speech again
                        ByteArray(0)            // silence  -> end of utterance
                    )
                ).collect { }
            }

            // The backend transcribes on every end_of_utterance, so repeating the marker through
            // a pause would ask it to transcribe an empty buffer over and over.
            assertEquals(listOf("data(3)", "end", "data(2)", "end"), received.toList())
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }
}
