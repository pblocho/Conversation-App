package com.pibi.conversation.manager

import com.pibi.conversation.data.model.Message

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val currentVolume: Double = 0.0,
    val statusText: String = "Ready"
)