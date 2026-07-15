package com.pibi.conversation.networking

import com.pibi.conversation.AppConfig
import com.pibi.conversation.grpc.AudioChunk
import com.pibi.conversation.grpc.SttService
import com.pibi.conversation.grpc.Transcript
import com.pibi.conversation.grpc.invoke
import com.pibi.conversation.networking.SttClient.SttClient
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.rpc.grpc.server.GrpcServer
import kotlinx.rpc.registerService
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

class SttGrpcSmokeTest {
    @Test
    fun bidirectionalStreamingRoundTrip() = runBlocking {
        val server = GrpcServer(AppConfig.STT_PORT) {
            services {
                registerService<SttService> { EchoSttService() }
            }
        }.start()

        try {
            val client = SttClient()
            val transcripts = async { client.textOutputFlow.take(2).toList() }
            delay(200) // let the collector subscribe before streaming starts

            launch { client.streamAudioToStt(flowOf(byteArrayOf(1, 2, 3), ByteArray(0))) }

            val received = withTimeout(15_000) { transcripts.await() }
            assertEquals(listOf("3 bytes", "end"), received)
        } finally {
            server.shutdown()
            server.awaitTermination()
        }
    }
}
