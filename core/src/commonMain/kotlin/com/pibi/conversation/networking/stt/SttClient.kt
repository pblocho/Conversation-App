package com.pibi.conversation.networking.stt

import com.pibi.conversation.AppConfig
import com.pibi.conversation.grpc.AudioChunk
import com.pibi.conversation.grpc.SttService
import com.pibi.conversation.grpc.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.io.bytestring.ByteString
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.withService

/**
 * Speech-to-text over gRPC. The endpoint is a constructor parameter rather than a global, so the
 * app can point at a different host per platform and tests can point at an in-process server.
 */
class SttClient(
    host: String = AppConfig.SERVER_HOST,
    port: Int = AppConfig.STT_PORT
) {
    private val client = GrpcClient(host, port) {
        credentials = plaintext()
    }

    private val _textOutputFlow = MutableSharedFlow<String>()
    val textOutputFlow = _textOutputFlow.asSharedFlow()

    /** Releases the underlying gRPC channel. */
    fun shutdown() = client.shutdown()

    /**
     * Streams microphone audio to the STT backend and emits transcripts on [textOutputFlow].
     * Returns when the server closes the stream and **throws when the stream fails** — the
     * caller owns reconnection policy, because only it knows what a lost stream means for
     * the conversation.
     */
    suspend fun streamAudioToStt(audioSource: Flow<ByteArray>) {
        val service = client.withService<SttService>()

        val requests = flow {
            // An empty chunk from the recorder marks the end of an utterance.
            // Emit the marker once and suppress repeats until audio resumes.
            var utteranceOpen = true
            audioSource.collect { audioChunk ->
                if (audioChunk.isNotEmpty()) {
                    utteranceOpen = true
                    emit(AudioChunk { data = ByteString(*audioChunk) })
                } else if (utteranceOpen) {
                    utteranceOpen = false
                    emit(AudioChunk { endOfUtterance = true })
                }
            }
        }

        service.Transcribe(requests).collect { transcript ->
            _textOutputFlow.emit(transcript.text)
        }
    }
}
