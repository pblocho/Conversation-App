package com.pibi.conversation.manager

import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.flow.SharedFlow

expect class ConversationManager constructor(ttsClient: TtsClient)
{
    suspend fun startConversation(textFlow: SharedFlow<String>)
}
