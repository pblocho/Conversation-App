package com.pibi.conversation.manager

import com.pibi.conversation.networking.SttClient.SttClient
import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

expect class ConversationManager constructor(
    ttsClient: TtsClient,
    sttClient: SttClient
)
{
    val uiState: StateFlow<ConversationUiState>

    suspend fun startConversation(textFlow: SharedFlow<String>)
}
