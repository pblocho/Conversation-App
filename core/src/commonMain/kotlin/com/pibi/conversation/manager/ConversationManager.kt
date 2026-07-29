package com.pibi.conversation.manager

import com.pibi.conversation.audioplayer.AudioPlayer
import com.pibi.conversation.audiorecorder.AudioRecorder
import com.pibi.conversation.data.model.Message
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.data.repository.ConversationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the conversation as the linear state machine described by [ConversationState]: record one
 * utterance, transcribe it, ask the LLM, play the answer, and start over.
 *
 * The machine runs forever — every turn ends back at [ConversationState.Idle] and the next one
 * starts on its own, so nothing has to be started by hand. [stop] cancels whatever state the
 * current turn is in and drops it to [ConversationState.Idle], from where it immediately begins
 * listening again.
 *
 * Both gRPC streams stay open across turns and across stops: the backend keeps the LLM chat
 * history per stream, so reconnecting per turn would erase the conversation's memory.
 *
 * Because the machine is linear, the microphone is only collected during
 * [ConversationState.RecordingAudio] — the assistant's own playback can no longer be picked up as
 * the next question.
 */
class ConversationManager(
    private val repository: ConversationRepository,
    private val recordAudio: () -> Flow<ByteArray> = { AudioRecorder.startRecording() },
    private val playAudio: (ByteArray) -> Unit = { AudioPlayer.playWavBytes(it) },
    /** Where the streams are collected and the blocking playback runs; swapped out in tests. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
)
{
    private companion object
    {
        /**
         * Consecutive silent chunks that end an utterance. The recorder reports silence as an
         * empty chunk; 10 of them are ~1.3 s with the JVM recorder's 4096-byte (128 ms) buffers,
         * matching the hold-off the STT backend applies before it transcribes.
         */
        const val SILENT_CHUNKS_END_UTTERANCE = 10

        /** Give up on a transcript that never arrives (e.g. the utterance held no speech). */
        val TRANSCRIPT_TIMEOUT = 30.seconds

        /** Whisper may split one utterance into several segments; collect the stragglers. */
        val TRANSCRIPT_SEGMENT_WINDOW = 300.milliseconds

        /** The LLM may think for a while before its first sentence comes back synthesized. */
        val FIRST_ANSWER_TIMEOUT = 60.seconds

        /**
         * A gap this long on the answer stream means the assistant stopped talking. The backend
         * synthesizes the next sentence while the current one plays, so by the time playback ends
         * the next sentence is normally already queued. (The TTS contract carries no
         * end-of-answer marker — adding one would turn this guess into a fact.)
         */
        val ANSWER_COMPLETE_TIMEOUT = 5.seconds

        /** Pause before the next turn after one blew up, so a lasting failure cannot spin the loop. */
        val RETRY_AFTER_FAILURE = 2.seconds
    }

    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    /** Audio handed to the STT stream, one utterance at a time. */
    private val sttRequests = Channel<ByteArray>(Channel.BUFFERED)

    /** Transcripts coming back from STT, buffered so none is lost between states. */
    private val transcripts = Channel<String>(Channel.UNLIMITED)

    /** Questions handed to the LLM/TTS stream. */
    private val llmRequests = MutableSharedFlow<String>()

    /** Answer sentences (text plus audio) coming back from the LLM/TTS stream. */
    private val answers = Channel<SynthesizedSpeech>(Channel.UNLIMITED)

    /** The turn currently being run, so [stop] can cancel it. */
    private var currentTurn: Job? = null

    /**
     * Opens the session and runs turns until cancelled. Suspends for as long as the conversation
     * lives, so callers launch it once (the ViewModel does this in its own scope).
     */
    suspend fun startConversation() = coroutineScope {
        openSession(this)

        // Each turn is its own job, so [stop] can cancel one turn without ending the
        // conversation: the next turn starts as soon as this one is done or cancelled.
        while (isActive)
        {
            launch { runTurn() }.also { currentTurn = it }.join()
        }
    }

    /**
     * Cancels the turn in flight. The machine falls back to [ConversationState.Idle] and starts
     * listening again by itself — there is nothing to restart by hand.
     */
    fun stop()
    {
        currentTurn?.cancel()
    }

    /**
     * Opens the two long-lived gRPC streams and funnels everything they produce into channels, so
     * the state machine can pick each event up in the state that expects it instead of racing the
     * streams.
     */
    private fun openSession(scope: CoroutineScope)
    {
        transitionTo(ConversationState.Init)

        scope.launch(ioDispatcher) { repository.transcripts.collect { transcripts.send(it) } }
        scope.launch(ioDispatcher) { repository.synthesizeSpeech(llmRequests).collect { answers.send(it) } }
        scope.launch(ioDispatcher) { repository.transcribe(sttRequests.receiveAsFlow()) }
    }

    /**
     * Walks the states forever: every turn ends back at [ConversationState.Idle] and the next one
     * starts from there. Only [stop] (cancellation) or a failure ends the run, and
     * [startConversation] then begins a new one at [ConversationState.Init].
     */
    private suspend fun runTurn()
    {
        var utterance: List<ByteArray> = emptyList()
        var question = ""
        var state = ConversationState.Init

        try
        {
            while (coroutineContext.isActive)
            {
                transitionTo(state)
                state = when (state)
                {

                    ConversationState.Init -> ConversationState.Idle

                    ConversationState.Idle ->
                    {
                        discardStaleEvents()
                        ConversationState.RecordingAudio
                    }

                    ConversationState.RecordingAudio ->
                    {
                        utterance = recordUtterance()
                        ConversationState.SendToStt
                    }

                    ConversationState.SendToStt ->
                    {
                        sendUtteranceToStt(utterance)
                        ConversationState.WaitForTextForLlm
                    }

                    ConversationState.WaitForTextForLlm ->
                    {
                        question = awaitQuestion()
                        // Nothing was recognized — end the turn and listen again.
                        if (question.isEmpty()) ConversationState.Idle else ConversationState.SendTextToLlm
                    }

                    ConversationState.SendTextToLlm ->
                    {
                        llmRequests.emit(question)
                        ConversationState.WaitForAudioAndTextFromLlm
                    }

                    // The answer is done, and so is this turn.
                    ConversationState.WaitForAudioAndTextFromLlm ->
                    {
                        speakAnswer()
                        ConversationState.Idle
                    }

                }
            }
        } catch (cancellation: CancellationException)
        {
            throw cancellation
        } catch (failure: Exception)
        {
            // One broken turn must not end the conversation; pause so a lasting failure
            // (no microphone, for instance) cannot spin the loop.
            println("Conversation turn failed: ${failure.message}")
            _uiState.update { it.copy(statusText = "Error: ${failure.message}") }
            delay(RETRY_AFTER_FAILURE)
        }
    }

    private fun discardStaleEvents()
    {
        while (transcripts.tryReceive().isSuccess)
        {
        }
        while (answers.tryReceive().isSuccess)
        {
        }
    }

    /** Records until the speaker has been silent for [SILENT_CHUNKS_END_UTTERANCE] chunks. */
    private suspend fun recordUtterance(): List<ByteArray>
    {
        val utterance = mutableListOf<ByteArray>()
        var silentChunks = 0

        recordAudio().takeWhile { chunk ->
            when
            {
                chunk.isNotEmpty() ->
                {
                    utterance += chunk
                    silentChunks = 0
                    true
                }

                // Leading silence: the speaker has not started yet, so keep waiting.
                utterance.isEmpty() -> true

                else ->
                {
                    silentChunks++
                    silentChunks < SILENT_CHUNKS_END_UTTERANCE
                }
            }
        }.collect { }

        return utterance
    }

    private suspend fun sendUtteranceToStt(utterance: List<ByteArray>)
    {
        utterance.forEach { sttRequests.send(it) }
        // An empty chunk is the recorder's end-of-utterance marker; SttClient turns it into an
        // AudioChunk with end_of_utterance = true, which is what makes the backend transcribe.
        sttRequests.send(ByteArray(0))
    }

    /**
     * Waits for the transcript of the utterance just sent, joining the segments Whisper may split
     * it into. Returns an empty string when nothing was recognized in time.
     */
    private suspend fun awaitQuestion(): String
    {
        val firstSegment = withTimeoutOrNull(TRANSCRIPT_TIMEOUT) { transcripts.receive() } ?: return ""

        val segments = mutableListOf(firstSegment)
        while (true)
        {
            segments += withTimeoutOrNull(TRANSCRIPT_SEGMENT_WINDOW) { transcripts.receive() } ?: break
        }

        val question = segments.joinToString(" ") { it.trim() }.trim()
        if (question.isNotEmpty()) addMessage(question, MessageType.QUESTION)
        return question
    }

    /** Plays the answer sentence by sentence, showing each one as it is spoken. */
    private suspend fun speakAnswer()
    {
        var timeout = FIRST_ANSWER_TIMEOUT
        while (true)
        {
            val speech = withTimeoutOrNull(timeout) { answers.receive() } ?: break
            addMessage(speech.text, MessageType.ANSWER)
            withContext(ioDispatcher) { playAudio(speech.audioWavBytes) }
            timeout = ANSWER_COMPLETE_TIMEOUT
        }
    }

    private fun transitionTo(state: ConversationState)
    {
        _uiState.update { it.copy(state = state, statusText = state.statusText()) }
    }

    private fun addMessage(text: String, messageType: MessageType)
    {
        _uiState.update {
            it.copy(messages = it.messages + Message(text, Clock.System.now(), messageType))
        }
    }

    private fun ConversationState.statusText(): String = when (this)
    {
        ConversationState.Init -> "Connecting..."
        ConversationState.Idle -> "Idle"
        ConversationState.RecordingAudio -> "Listening..."
        ConversationState.SendToStt -> "Sending audio..."
        ConversationState.WaitForTextForLlm -> "Recognizing speech..."
        ConversationState.SendTextToLlm -> "Asking..."
        ConversationState.WaitForAudioAndTextFromLlm -> "Answering..."
    }
}
