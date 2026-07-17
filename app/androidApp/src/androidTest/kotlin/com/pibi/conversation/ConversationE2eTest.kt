package com.pibi.conversation

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.pibi.conversation.data.repository.ConversationRepository
import com.pibi.conversation.manager.ConversationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end test of the real voice pipeline on a device/emulator:
 * real AudioRecord microphone capture -> gRPC STT stream -> transcript
 * -> UI state update -> gRPC TTS stream -> AudioTrack playback.
 *
 * Requires the fake backend on the host plus adb port reversal —
 * run it via scripts/android_e2e.sh, not directly.
 *
 * The emulator microphone delivers silence, which the recorder turns into
 * an end-of-utterance marker; the fake STT answers it with "utterance-end".
 */
@RunWith(AndroidJUnit4::class)
class ConversationE2eTest {

    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun voicePipelineRoundTrip() {
        // The manager's uiState is shared eagerly in this scope; keep it separate
        // from runBlocking so cancelling it doesn't hang the test.
        val managerScope = CoroutineScope(Dispatchers.IO)
        try {
            runBlocking {
                val manager = ConversationManager(ConversationRepository(), managerScope)

                val pipeline = launch(Dispatchers.IO) {
                    manager.startConversation()
                }

                try {
                    // "Synthesized:" arrives last: mic -> STT transcript -> TTS audio+text round trip
                    val synthesized = withTimeout(30_000) {
                        manager.uiState.first { it.statusText.startsWith("Synthesized:") }
                    }
                    assertTrue(
                        "Unexpected TTS text: ${synthesized.statusText}",
                        synthesized.statusText.contains("utterance-end") ||
                            synthesized.statusText.contains("heard")
                    )
                    assertTrue(
                        "Expected both QUESTION and ANSWER messages, got: ${synthesized.messages}",
                        synthesized.messages.size >= 2
                    )
                } finally {
                    pipeline.cancel()
                }
            }
        } finally {
            managerScope.cancel()
        }
    }
}
