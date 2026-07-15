package com.pibi.conversation.networking.SttClient

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

    suspend fun streamAudioToStt(audioSource: Flow<ByteArray>) {
        try {
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
        } catch (e: Exception) {
            println("STT Connection Error: ${e.message}")
        }
    }
}
