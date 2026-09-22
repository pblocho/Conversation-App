package com.pibi.conversation.data.model

/**
 * What the app sends up the answer stream.
 *
 * One stream carries the whole conversation, because the backend keeps the LLM's chat history per
 * stream. Stopping an answer therefore cannot be done by closing the stream — that would take the
 * conversation with it — so [Cancel] is a message like any other.
 */
sealed interface SpeechRequest
{
    /** Ask the assistant this, and speak its reply. */
    class Say(val text: String) : SpeechRequest

    /** Stop speaking the answer in flight. The conversation, and its memory, stay open. */
    data object Cancel : SpeechRequest
}
