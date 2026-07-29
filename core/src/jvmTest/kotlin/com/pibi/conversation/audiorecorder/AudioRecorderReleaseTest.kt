package com.pibi.conversation.audiorecorder

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The conversation records one utterance per turn, so the recorder is collected over and over.
 * Each collection has to hand the microphone back, or every turn after the first waits on a line
 * that never delivers audio — which shows up as a conversation stuck on "Listening...".
 *
 * Needs a microphone; skips itself where there is none. Silence counts as audio here (the
 * recorder emits an empty chunk for it), so nobody has to speak for this to be meaningful.
 */
class AudioRecorderReleaseTest
{
    private companion object
    {
        const val CHUNKS_PER_ROUND = 5
        const val ROUND_TIMEOUT_MILLIS = 10_000L
    }

    @Test
    fun microphoneIsReleasedBetweenUtterances() = runBlocking {
        if (!microphoneAvailable())
        {
            println("SKIPPED: no 16 kHz mono microphone on this machine.")
            return@runBlocking
        }

        repeat(3) { round ->
            val chunks = withTimeoutOrNull(ROUND_TIMEOUT_MILLIS) {
                AudioRecorder.startRecording().take(CHUNKS_PER_ROUND).toList()
            }

            assertNotNull(
                chunks,
                "round $round delivered no audio within ${ROUND_TIMEOUT_MILLIS}ms — the previous " +
                        "recording never released the microphone"
            )
            assertEquals(CHUNKS_PER_ROUND, chunks.size, "round $round was cut short")
        }
    }

    private fun microphoneAvailable(): Boolean
    {
        val format = AudioFormat(16000f, 16, 1, true, false)
        return AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, format))
    }
}
