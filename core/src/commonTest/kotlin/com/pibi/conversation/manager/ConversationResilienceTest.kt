package com.pibi.conversation.manager

import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.data.repository.ConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * A backend that fails its streams a set number of times before working, standing in for a
 * server that is restarted underneath a running conversation.
 */
private class FlakyRepository(
    private val sttFailures: Int = 0,
    private val ttsFailures: Int = 0
) : ConversationRepository
{
    private val _transcripts = MutableSharedFlow<String>()
    override val transcripts: SharedFlow<String> = _transcripts.asSharedFlow()

    var sttAttempts = 0
        private set
    var ttsAttempts = 0
        private set
    val questionsAsked = mutableListOf<String>()

    override suspend fun transcribe(audioSource: Flow<ByteArray>)
    {
        if (sttAttempts++ < sttFailures) throw IllegalStateException("stt stream lost")

        audioSource.collect { chunk ->
            if (chunk.isEmpty()) _transcripts.emit(" Are you still there?")
        }
    }

    override fun synthesizeSpeech(textFlow: Flow<String>): Flow<SynthesizedSpeech> = flow {
        if (ttsAttempts++ < ttsFailures) throw IllegalStateException("tts stream lost")

        textFlow.collect { question ->
            questionsAsked += question
            emit(SynthesizedSpeech("Still here.", byteArrayOf(7, 8, 9)))
        }
    }
}

private fun microphone(): Flow<ByteArray> = flow {
    emit(byteArrayOf(1, 2, 3))
    repeat(20) { emit(ByteArray(0)) }
    awaitCancellation()
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationResilienceTest
{
    private fun TestScope.manager(repository: ConversationRepository) =
        ConversationManager(repository, ::microphone, { }, StandardTestDispatcher(testScheduler))

    @Test
    fun aDroppedSttStreamIsReconnectedAndTheConversationCarriesOn() = runTest {
        val repository = FlakyRepository(sttFailures = 2)
        val manager = manager(repository)

        backgroundScope.launch { manager.startConversation() }

        // The failures are reported rather than swallowed...
        val broken = manager.uiState.first { !it.connection.sttUp && it.connection.lastError != null }
        assertEquals("stt stream lost", broken.connection.lastError)

        // ...and the machine still completes a turn once the backend comes back.
        manager.uiState.first { state -> state.messages.any { it.messageType == MessageType.ANSWER } }
        assertTrue(repository.sttAttempts >= 3, "the STT stream should have been retried")
        assertEquals(listOf("Are you still there?"), repository.questionsAsked)
    }

    @Test
    fun aQuestionSurvivesATtsReconnectInsteadOfBeingDropped() = runTest {
        val repository = FlakyRepository(ttsFailures = 1)
        val manager = manager(repository)

        backgroundScope.launch { manager.startConversation() }

        // The question is asked while the TTS stream is down, and is answered after the reconnect
        // rather than vanishing into a SharedFlow with no subscriber.
        val answered = manager.uiState.first { state ->
            state.messages.any { it.messageType == MessageType.ANSWER }
        }

        assertEquals(listOf("Are you still there?"), repository.questionsAsked)
        assertEquals("Still here.", answered.messages.last { it.messageType == MessageType.ANSWER }.text)
        assertTrue(repository.ttsAttempts >= 2, "the TTS stream should have been retried")
    }

    @Test
    fun connectionRecoveryClearsTheError() = runTest {
        val repository = FlakyRepository(ttsFailures = 1)
        val manager = manager(repository)

        backgroundScope.launch { manager.startConversation() }

        manager.uiState.first { it.connection.lastError != null }
        val recovered = manager.uiState.first { it.connection.isReady }

        assertTrue(recovered.connection.sttUp && recovered.connection.ttsUp)
        assertEquals(null, recovered.connection.lastError, "a healthy session should not show a stale error")
    }

    @Test
    fun noTurnIsStartedWhileABackendStaysDown() = runTest {
        // Never recovers: every TTS attempt fails.
        val repository = FlakyRepository(ttsFailures = Int.MAX_VALUE)
        val manager = manager(repository)

        // Unconfined, so a state the machine only passes through cannot be missed — otherwise
        // this test could pass simply by not observing the recording it is meant to catch.
        val states = mutableSetOf<ConversationState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.uiState.collect { states += it.state }
        }
        backgroundScope.launch { manager.startConversation() }

        // Let a good number of reconnect attempts go by. Waiting on uiState would not work here:
        // the connection state stops changing once it settles on "down", so it stops emitting.
        advanceTimeBy(2.minutes)

        assertTrue(repository.ttsAttempts >= 3, "the TTS stream should keep retrying")
        assertTrue(
            ConversationState.RecordingAudio !in states,
            "the machine should not record while a backend is down, saw: $states"
        )
    }
}
