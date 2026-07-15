package com.pibi.conversation.manager

import com.pibi.conversation.networking.SttClient.SttClient
import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class ConversationManager actual constructor(
    private val ttsClient: TtsClient,
    private val sttClient: SttClient
) {
    private val _uiState = MutableStateFlow(ConversationUiState())
    actual val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    actual suspend fun startConversation(textFlow: SharedFlow<String>) {
        ttsClient.streamAudioFromTts(textFlow)
    }
}
