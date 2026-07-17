package com.pibi.conversation.networking.ttsClient

import com.pibi.conversation.AppConfig
import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.withService

class TtsClient
{
    companion object
    {
        private val client = GrpcClient(AppConfig.SERVER_HOST, AppConfig.TTS_PORT) {
            credentials = plaintext()
        }
    }

    /**
     * Streams text pieces to the TTS backend and emits each synthesized result —
     * audio together with the text it was generated from. Playback is up to the caller.
     */
    fun streamSpeech(textInputFlow: SharedFlow<String>): Flow<SynthesizedSpeech> = flow {
        try
        {
            val service = client.withService<TtsService>()

            val requests = textInputFlow.map { pieceOfText ->
                TextPiece { text = pieceOfText }
            }

            service.Synthesize(requests).collect { audio ->
                emit(SynthesizedSpeech(audio.text, audio.data.toByteArray()))
            }
        } catch (e: Exception)
        {
            println("Error connecting to TTS server: ${e.message}")
        }
    }
}
