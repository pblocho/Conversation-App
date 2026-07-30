package com.pibi.conversation.manager

import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.model.SynthesizedSpeech
import com.pibi.conversation.data.repository.ConversationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Backend stand-in: answers every finished utterance with a transcript, and every question with speech. */
private class FakeRepository : ConversationRepository
{
    private val _transcripts = MutableSharedFlow<String>()
    override val transcripts: SharedFlow<String> = _transcripts.asSharedFlow()

    val questionsAsked = mutableListOf<String>()

    override suspend fun transcribe(audioSource: Flow<ByteArray>)
    {
        audioSource.collect { chunk ->
            // Like the real backend: transcribe once the end-of-utterance marker arrives.
            if (chunk.isEmpty()) _transcripts.emit(" What is the weather?")
        }
    }

    override fun synthesizeSpeech(textFlow: Flow<String>): Flow<SynthesizedSpeech> = flow {
        textFlow.collect { question ->
            questionsAsked += question
            emit(SynthesizedSpeech("It is sunny.", byteArrayOf(1, 2, 3)))
        }
    }
}

/** Microphone stand-in: one spoken chunk, then enough silence to close the utterance. */
private fun fakeMicrophone(): Flow<ByteArray> = flow {
    emit(byteArrayOf(4, 5, 6))
    repeat(20) { emit(ByteArray(0)) }
    awaitCancellation()
}

/** Microphone stand-in that never falls silent, so the machine stays in RecordingAudio. */
private fun endlessMicrophone(): Flow<ByteArray> = flow {
    emit(byteArrayOf(4, 5, 6))
    awaitCancellation()
}

/**
 * Every state the machine passes through, in order. A channel rather than the StateFlow itself,
 * because StateFlow conflates and would drop the states the machine only passes through.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.statesOf(manager: ConversationManager): ReceiveChannel<ConversationState>
{
    val states = Channel<ConversationState>(Channel.UNLIMITED)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        var previous: ConversationState? = null
        manager.uiState.collect {
            if (it.state != previous)
            {
                previous = it.state
                states.send(it.state)
            }
        }
    }
    return states
}

private suspend fun ReceiveChannel<ConversationState>.receiveUntil(state: ConversationState)
{
    while (receive() != state) { /* walk past the states before the one we care about */ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationStateMachineTest
{
    /** Keeps the manager's own coroutines on the test scheduler, so timeouts use virtual time. */
    private fun TestScope.manager(
        repository: ConversationRepository = FakeRepository(),
        microphone: () -> Flow<ByteArray> = ::fakeMicrophone,
        play: (ByteArray) -> Unit = { }
    ) = ConversationManager(repository, microphone, play, StandardTestDispatcher(testScheduler))

    @Test
    fun turnWalksThroughEveryStateInOrderAndLoopsForever() = runTest {
        val repository = FakeRepository()
        val played = mutableListOf<ByteArray>()
        val manager = manager(repository, play = { played += it })

        val states = statesOf(manager)
        backgroundScope.launch { manager.startConversation() }

        val firstTurn = List(7) { states.receive() }
        assertEquals(
            listOf(
                ConversationState.Init,
                ConversationState.Idle,
                ConversationState.RecordingAudio,
                ConversationState.SendToStt,
                ConversationState.WaitForTextForLlm,
                ConversationState.SendTextToLlm,
                ConversationState.WaitForAudioAndTextFromLlm
            ),
            firstTurn,
            "the machine should walk the states in the declared order"
        )

        // The turn ends back at Idle and the next one starts on its own.
        assertEquals(ConversationState.Idle, states.receive())
        assertEquals(ConversationState.RecordingAudio, states.receive())

        manager.uiState.first { state -> state.messages.any { it.messageType == MessageType.ANSWER } }
        assertEquals(listOf("What is the weather?"), repository.questionsAsked.take(1))
        assertEquals(listOf(listOf<Byte>(1, 2, 3)), played.take(1).map { it.toList() })

        val messages = manager.uiState.value.messages
        assertEquals("What is the weather?", messages.first().text)
        assertEquals(MessageType.QUESTION, messages.first().messageType)
        assertEquals("It is sunny.", messages[1].text)
        assertEquals(MessageType.ANSWER, messages[1].messageType)
    }

    @Test
    fun stopWhileWaitingOnTheBackendRestartsAtIdleThenRecords() = runTest {
        val manager = manager()
        val states = statesOf(manager)
        backgroundScope.launch { manager.startConversation() }

        states.receiveUntil(ConversationState.WaitForAudioAndTextFromLlm)
        manager.stop()

        assertEquals(
            // No Init: opening the session happens once, not on every turn.
            listOf(ConversationState.Idle, ConversationState.RecordingAudio),
            List(2) { states.receive() },
            "stop() should end the turn and let the machine start listening again by itself"
        )
    }

    @Test
    fun initIsPassedOncePerSessionRatherThanEveryTurn() = runTest {
        val manager = manager()
        val states = statesOf(manager)
        backgroundScope.launch { manager.startConversation() }

        // Two full turns' worth of states, which used to contain two Inits.
        val seen = List(16) { states.receive() }

        assertEquals(
            1,
            seen.count { it == ConversationState.Init },
            "Init is opening the session, not a step of a turn; saw: $seen"
        )
    }

    @Test
    fun stopWhileRecordingRestartsAtIdleThenRecords() = runTest {
        val manager = manager(microphone = ::endlessMicrophone)
        val states = statesOf(manager)
        backgroundScope.launch { manager.startConversation() }

        // The endless microphone parks the machine in RecordingAudio until it is stopped.
        states.receiveUntil(ConversationState.RecordingAudio)
        manager.stop()

        assertEquals(
            listOf(ConversationState.Idle, ConversationState.RecordingAudio),
            List(2) { states.receive() },
            "stopping mid-recording should start a fresh utterance"
        )
    }
}
