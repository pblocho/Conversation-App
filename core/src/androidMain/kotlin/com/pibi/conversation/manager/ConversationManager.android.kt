package com.pibi.conversation.manager

import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.flow.SharedFlow

actual class ConversationManager actual constructor(private val ttsClient: TtsClient) {
    actual suspend fun startConversation(textFlow: SharedFlow<String>) {
        ttsClient.streamAudioFromTts(textFlow)
    }
}
