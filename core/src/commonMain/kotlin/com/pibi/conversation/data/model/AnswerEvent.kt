package com.pibi.conversation.data.model

/**
 * One item on the answer stream.
 *
 * A single stream carries every turn of the conversation, because the backend keeps the LLM's
 * chat history per stream. The end of an answer therefore cannot be signalled by closing the
 * stream, as a plain RPC would, and arrives as [Complete] instead.
 *
 * Modelled as a sealed type rather than a flag on the sentence: the marker carries neither audio
 * nor text, so nothing downstream can mistake it for something to show or play.
 */
sealed interface AnswerEvent
{
    /** One synthesized sentence: the audio to play, and the text it was generated from. */
    class Sentence(val text: String, val audioWavBytes: ByteArray) : AnswerEvent

    /** This turn's answer is finished; nothing more arrives until the next question. */
    data object Complete : AnswerEvent
}
