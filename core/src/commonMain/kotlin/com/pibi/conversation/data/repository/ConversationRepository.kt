package com.pibi.conversation.data.repository

import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.networking.stt.SttClient
import com.pibi.conversation.networking.tts.TtsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Single access point to the speech data sources, following the Android
 * repository pattern: ViewModel -> ConversationManager -> Repository -> remote data sources.
 * The gRPC clients stay an implementation detail behind it.
 */
interface ConversationRepository
{
    /** Transcripts recognized by the STT backend. */
    val transcripts: SharedFlow<String>

    /**
     * Streams microphone audio to the STT backend; suspends while the stream is active, returns
     * when the backend closes it, and throws when it fails. Callers decide whether to reconnect.
     */
    suspend fun transcribe(audioSource: Flow<ByteArray>)

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
    override val transcripts: SharedFlow<String> = sttClient.textOutputFlow

    override suspend fun transcribe(audioSource: Flow<ByteArray>) = sttClient.streamAudioToStt(audioSource)

    override fun synthesizeSpeech(textFlow: Flow<String>): Flow<SynthesizedSpeech> =
        ttsClient.streamSpeech(textFlow)
}
