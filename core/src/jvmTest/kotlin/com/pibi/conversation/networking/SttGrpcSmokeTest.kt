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
                emit(Transcript { text = "end" })
                false
            } else {
                emit(Transcript { text = "${chunk.data.size} bytes" })
                true
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
            val transcripts = async { client.textOutputFlow.take(2).toList() }
            delay(200) // let the collector subscribe before streaming starts

            launch { client.streamAudioToStt(flowOf(byteArrayOf(1, 2, 3), ByteArray(0))) }

            val received = withTimeout(15_000) { transcripts.await() }
            assertEquals(listOf("3 bytes", "end"), received)
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
                )
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
