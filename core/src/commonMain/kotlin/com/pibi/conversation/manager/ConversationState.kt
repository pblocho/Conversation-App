package com.pibi.conversation.manager

/**
 * Steps of the conversation machine. The machine runs forever: every turn passes through
 * [Idle] and starts the next one on its own.
 *
 * ```
 * Init ─▶ Idle ─▶ RecordingAudio ─▶ SendToStt ─▶ WaitForTextForLlm ─▶ SendTextToLlm ─▶ WaitForAudioAndTextFromLlm ─┐
 *         ▲                                                                                                       │
 *         └───────────────────────────────────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * [Init] is passed once, while the session streams are opened. Stopping the machine cancels
 * whatever state it is in and drops it back to [Idle], from where it starts listening again —
 * so "stop" cancels the current turn rather than the conversation.
 */
enum class ConversationState
{
    /** Opening the STT and TTS streams; passed once per session. */
    Init,

    /** Between turns: the previous turn is finished (or was stopped) and the next one is about to start. */
    Idle,

    /** Capturing microphone audio until the speaker falls silent. */
    RecordingAudio,

    /** Handing the recorded utterance to the STT stream. */
    SendToStt,

    /** Waiting for the STT backend to transcribe what was said. */
    WaitForTextForLlm,

    /** Handing the transcribed question to the LLM/TTS stream. */
    SendTextToLlm,

    /** Waiting for the answer: each sentence arrives as text plus audio, and is played as it lands. */
    WaitForAudioAndTextFromLlm,
}
