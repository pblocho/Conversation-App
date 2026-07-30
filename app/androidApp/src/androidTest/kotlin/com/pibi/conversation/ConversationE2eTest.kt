package com.pibi.conversation

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.pibi.conversation.audiorecorder.AudioRecorder
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.repository.GrpcConversationRepository
import com.pibi.conversation.manager.ConversationManager
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real pipeline on a device or emulator: real `AudioRecord` capture, real gRPC streams
 * to the fake backend on the host, real `AudioTrack` playback.
 *
 * Run it through `scripts/android_e2e.sh`, which starts that backend and maps the device's ports
 * back to the host with `adb reverse`. Run directly, it just times out.
 */
@RunWith(AndroidJUnit4::class)
class ConversationE2eTest {

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    /**
     * The microphone side, on the real device: permission granted, `AudioRecord` configured for
     * 16 kHz mono, and chunks actually arriving. An emulator's microphone is silent, so these come
     * through as the recorder's empty "silence" chunks — still real reads from real hardware.
     */
    @Test
    fun theMicrophoneDeliversAudioOnDevice() = runBlocking {
        val chunks = withTimeout(15_000) {
            AudioRecorder.startRecording().take(5).toList()
        }

        assertEquals("the recorder should keep delivering chunks", 5, chunks.size)
    }

    /**
     * The rest of the pipeline against the fake backend: an utterance goes out over the STT
     * stream, its transcript becomes the question, and the answer comes back over the TTS stream
     * as text plus audio, which real playback consumes.
     *
     * The microphone is stood in for here deliberately. An emulator records silence, and the
     * machine ignores silence until someone actually speaks, so a device with nobody talking into
     * it could never finish an utterance. Real capture is covered by the test above.
     */
    @Test
    fun aSpokenTurnReachesTheBackendAndComesBackAsSpeech() = runBlocking {
        val manager = ConversationManager(
            repository = GrpcConversationRepository(),
            recordAudio = ::oneSpokenUtterance
        )

        val pipeline = launch { manager.startConversation() }
        try {
            val answered = withTimeout(60_000) {
                manager.uiState.first { state -> state.messages.any { it.messageType == MessageType.ANSWER } }
            }

            val question = answered.messages.first { it.messageType == MessageType.QUESTION }.text
            val answer = answered.messages.first { it.messageType == MessageType.ANSWER }.text

            // What the fake backend echoes back — see server/.../E2eGrpcServer.kt
            assertTrue(
                "STT should have heard the utterance, got: $question",
                question.contains("heard") || question.contains("utterance-end")
            )
            assertTrue("the answer should carry text, got: $answer", answer.isNotBlank())
        } finally {
            pipeline.cancel()
        }
    }

    /** Speech, then enough silence for the machine to call the utterance finished. */
    private fun oneSpokenUtterance(): Flow<ByteArray> = flow {
        emit(ByteArray(4096) { (it % 128).toByte() })
        repeat(20) { emit(ByteArray(0)) }
        awaitCancellation()
    }
}
