package com.pibi.conversation.networking

import com.pibi.conversation.data.model.AnswerEvent
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

/** Answers one sentence and then marks the answer finished, the way a real backend should. */
private class MarkingTtsService : TtsService {
    override fun Synthesize(message: Flow<TextPiece>): Flow<AudioData> = flow {
        message.collect { piece ->
            emit(AudioData { text = "spoken: ${piece.text}"; data = ByteString(1, 2, 3) })
            emit(AudioData { endOfAnswer = true })
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
            val sentences = spoken.filterIsInstance<AnswerEvent.Sentence>()
            assertEquals(listOf("spoken: Hello there", "spoken: How are you"), sentences.map { it.text })
            assertContentEquals("Hello there".encodeToByteArray(), sentences.first().audioWavBytes)
        } finally {
            client.shutdown()
            server.shutdown()
            server.awaitTermination()
        }
    }

    @Test
    fun theEndOfAnswerMarkerArrivesAsItsOwnEvent() = runBlocking {
        val port = freePort()
        val server = GrpcServer(port) {
            services { registerService<TtsService> { MarkingTtsService() } }
        }.start()

        val client = TtsClient(port = port)
        try {
            val events = withTimeout(15_000) { client.streamSpeech(flowOf("Hello")).toList() }

            // The marker carries no audio and no text, so it must not arrive as a sentence the
            // app would show on screen and try to play.
            assertEquals(2, events.size)
            assertEquals("spoken: Hello", (events.first() as AnswerEvent.Sentence).text)
            assertEquals(AnswerEvent.Complete, events.last())
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
