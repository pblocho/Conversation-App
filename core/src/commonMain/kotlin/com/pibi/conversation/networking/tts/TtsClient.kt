package com.pibi.conversation.networking.tts

import com.pibi.conversation.AppConfig
import com.pibi.conversation.data.model.AnswerEvent
import com.pibi.conversation.data.model.SpeechRequest
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.withService

/**
 * Text-to-speech over gRPC. The endpoint is a constructor parameter rather than a global, so the
 * app can point at a different host per platform and tests can point at an in-process server.
 */
class TtsClient(
    host: String = AppConfig.SERVER_HOST,
    port: Int = AppConfig.TTS_PORT
)
{
    private val client = GrpcClient(host, port) {
        credentials = plaintext()
    }

    /** Releases the underlying gRPC channel. */
    fun shutdown() = client.shutdown()

    /**
     * Streams text pieces to the TTS backend and emits what comes back: each synthesized sentence
     * as [AnswerEvent.Sentence], and the backend's end-of-answer marker as [AnswerEvent.Complete].
     * Playback is up to the caller.
     *
     * The marker is an ordinary message rather than the end of the stream, because the stream
     * outlives the answer — it carries every later question too.
     *
     * Each collection opens a fresh stream, and **failures propagate to the collector** instead of
     * ending the flow quietly: only the caller knows whether a lost stream should be retried, and
     * a silent completion here would strand every later question with no way to notice.
     */
    fun streamSpeech(speechRequests: Flow<SpeechRequest>): Flow<AnswerEvent> = flow {
        val service = client.withService<TtsService>()

        val requests = speechRequests.map { request ->
            when (request)
            {
                is SpeechRequest.Say -> TextPiece { text = request.text }
                SpeechRequest.Cancel -> TextPiece { cancel = true }
            }
        }

        service.Synthesize(requests).collect { audio ->
            emit(
                if (audio.endOfAnswer) AnswerEvent.Complete
                else AnswerEvent.Sentence(audio.text, audio.data.toByteArray())
            )
        }
    }
}
