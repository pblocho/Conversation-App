package com.pibi.conversation.manager

import com.pibi.conversation.data.model.Message

data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val state: ConversationState = ConversationState.Init,
    val currentVolume: Double = 0.0,
    val statusText: String = "Ready"
)
{
    val isRecording: Boolean get() = state == ConversationState.RecordingAudio

    /** True while the turn is in the hands of the backends, between recording and the answer. */
    val isProcessing: Boolean get() = state == ConversationState.SendToStt ||
            state == ConversationState.WaitForTextForLlm ||
            state == ConversationState.SendTextToLlm ||
            state == ConversationState.WaitForAudioAndTextFromLlm
}
