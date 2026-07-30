package com.pibi.conversation.networking.ttsClient

import com.pibi.conversation.AppConfig
import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import kotlinx.coroutines.flow.Flow
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
     * Streams text pieces to the TTS backend and emits each synthesized result — audio together
     * with the text it was generated from. Playback is up to the caller.
     *
     * Each collection opens a fresh stream, and **failures propagate to the collector** instead of
     * ending the flow quietly: only the caller knows whether a lost stream should be retried, and
     * a silent completion here would strand every later question with no way to notice.
     */
    fun streamSpeech(textInputFlow: Flow<String>): Flow<SynthesizedSpeech> = flow {
        val service = client.withService<TtsService>()

        val requests = textInputFlow.map { pieceOfText ->
            TextPiece { text = pieceOfText }
        }

        service.Synthesize(requests).collect { audio ->
            emit(SynthesizedSpeech(audio.text, audio.data.toByteArray()))
        }
    }
}
