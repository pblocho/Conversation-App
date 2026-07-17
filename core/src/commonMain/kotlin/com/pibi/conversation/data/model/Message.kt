package com.pibi.conversation.data.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
data class Message(val text: String, val timestamp: Instant, val messageType: MessageType, val id: String = randomUuid())

enum class MessageType
{
    QUESTION, ANSWER
}