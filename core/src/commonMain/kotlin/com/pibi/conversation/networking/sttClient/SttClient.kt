package com.pibi.conversation.networking.sttClient

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

class SttClient {
    companion object {
        private val client = GrpcClient(AppConfig.SERVER_HOST, AppConfig.STT_PORT) {
            credentials = plaintext()
        }
    }

    private val _textOutputFlow = MutableSharedFlow<String>()
    val textOutputFlow = _textOutputFlow.asSharedFlow()

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
