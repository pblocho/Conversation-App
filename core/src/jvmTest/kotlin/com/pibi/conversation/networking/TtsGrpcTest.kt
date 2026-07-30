package com.pibi.conversation.networking

import com.pibi.conversation.grpc.AudioData
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import com.pibi.conversation.networking.tts.TtsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.bytestring.ByteString
import kotlinx.rpc.grpc.server.GrpcServer
import kotlinx.rpc.registerService
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Answers each text piece with audio, the way the real backend does one sentence at a time. */
private class EchoTtsService : TtsService {
    override fun Synthesize(message: Flow<TextPiece>): Flow<AudioData> =
        message.map { piece ->
            AudioData {
                text = "spoken: ${piece.text}"
                data = ByteString(*piece.text.encodeToByteArray())
            }
        }
}

/** A backend that drops the stream, which is what the manager's reconnect logic hangs on. */
private class BrokenTtsService : TtsService {
    override fun Synthesize(message: Flow<TextPiece>): Flow<AudioData> = flow {
        throw IllegalStateException("synthesis unavailable")
    }
}

private fun freePort(): Int = ServerSocket(0).use { it.localPort }

class TtsGrpcTest {
    @Test
    fun textGoesOutAndSpeechComesBackWithItsSourceText() = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services { registerService<TtsService> { EchoTtsService() } }
        }.start()

        val client = TtsClient(port = port)
        try {
            val spoken = withTimeout(15_000) {
                client.streamSpeech(flowOf("Hello there", "How are you")).toList()
            }

            // Each result pairs the audio with the text it was synthesized from — the thing the
            // app shows on screen while it plays.
            assertEquals(listOf("spoken: Hello there", "spoken: How are you"), spoken.map { it.text })
            assertContentEquals("Hello there".encodeToByteArray(), spoken.first().audioWavBytes)
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }

    @Test
    fun aDroppedStreamReachesTheCollector(): Unit = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services { registerService<TtsService> { BrokenTtsService() } }
        }.start()

        val client = TtsClient(port = port)
        try {
            // The client must not swallow this: a flow that just ends looks like a finished
            // answer, and the manager would never know to reconnect.
            assertFails {
                withTimeout(15_000) { client.streamSpeech(flowOf("Hello")).toList() }
            }
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }
}
