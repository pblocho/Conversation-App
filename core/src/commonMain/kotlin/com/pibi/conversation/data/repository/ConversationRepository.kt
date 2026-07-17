package com.pibi.conversation.data.repository

import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.networking.sttClient.SttClient
import com.pibi.conversation.networking.ttsClient.TtsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Single access point to the speech data sources, following the Android
 * repository pattern: ViewModel -> ConversationManager -> Repository -> remote data sources.
 * The gRPC clients ([SttClient], [TtsClient]) stay an implementation detail behind it.
 */
class ConversationRepository(
    private val sttClient: SttClient = SttClient(),
    private val ttsClient: TtsClient = TtsClient()
)
{
    /** Transcripts recognized by the STT backend. */
    val transcripts: SharedFlow<String> = sttClient.textOutputFlow

    /** Streams microphone audio to the STT backend; suspends while the stream is active. */
    suspend fun transcribe(audioSource: Flow<ByteArray>) = sttClient.streamAudioToStt(audioSource)

    /** Streams text to the TTS backend; emits each synthesized audio chunk with its source text. */
    fun synthesizeSpeech(textFlow: SharedFlow<String>): Flow<SynthesizedSpeech> = ttsClient.streamSpeech(textFlow)
}
