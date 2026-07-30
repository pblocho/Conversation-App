package com.pibi.conversation.manager

import androidx.compose.runtime.Immutable
import com.pibi.conversation.data.model.Message

/**
 * `core` does not apply the Compose compiler plugin, so Compose cannot infer stability for
 * anything declared here — without the annotation it assumes the worst and skips less. Every
 * property is a `val` of an immutable type, and the message list is replaced rather than mutated,
 * so the promise holds.
 */
@Immutable
data class ConversationUiState(
    val messages: List<Message> = emptyList(),
    val state: ConversationState = ConversationState.Init,
    val connection: ConnectionState = ConnectionState(),
    /**
     * Why the last turn failed, straight from the failure — not a message for the user to read.
     * The UI decides how (and whether) to word it.
     */
    val turnError: String? = null
)
{
    val isRecording: Boolean get() = state == ConversationState.RecordingAudio

    /** True while the turn is in the hands of the backends, between recording and the answer. */
    val isProcessing: Boolean get() = state == ConversationState.SendToStt ||
            state == ConversationState.WaitForTextForLlm ||
            state == ConversationState.SendTextToLlm ||
            state == ConversationState.WaitForAudioAndTextFromLlm
}
