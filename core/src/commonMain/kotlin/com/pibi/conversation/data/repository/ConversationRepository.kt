package com.pibi.conversation.data.repository

import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.networking.stt.SttClient
import com.pibi.conversation.networking.tts.TtsClient
import kotlinx.coroutines.flow.Flow

/**
 * Single access point to the speech data sources, following the Android
 * repository pattern: ViewModel -> ConversationManager -> Repository -> remote data sources.
 * The gRPC clients stay an implementation detail behind it.
 *
 * Both members are the same shape: hand in what you are sending, collect what comes back. Neither
 * the repository nor the clients hold conversation state — one collection is one stream.
 */
interface ConversationRepository
{
    /**
     * Streams microphone audio to the STT backend and emits the transcripts it returns.
     * Collection ends when the backend closes the stream and fails when the stream fails, so
     * callers decide whether to reconnect.
     */
    fun transcribe(audioSource: Flow<ByteArray>): Flow<String>

    /**
     * Streams text to the TTS backend; emits each synthesized sentence with its source text.
     * Each collection is one stream — and one LLM conversation on the backend, which keeps the
     * chat history per stream. Failures reach the collector rather than ending the flow quietly.
     */
    fun synthesizeSpeech(textFlow: Flow<String>): Flow<SynthesizedSpeech>
}

/** Talks to the STT and TTS backends over gRPC ([SttClient], [TtsClient]). */
class GrpcConversationRepository(
    private val sttClient: SttClient = SttClient(),
    private val ttsClient: TtsClient = TtsClient()
) : ConversationRepository
{
    override fun transcribe(audioSource: Flow<ByteArray>): Flow<String> =
        sttClient.streamAudioToStt(audioSource)

    override fun synthesizeSpeech(textFlow: Flow<String>): Flow<SynthesizedSpeech> =
        ttsClient.streamSpeech(textFlow)
}
