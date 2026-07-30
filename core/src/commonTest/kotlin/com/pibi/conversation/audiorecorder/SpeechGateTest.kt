package com.pibi.conversation.audiorecorder

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Builds 16-bit signed little-endian PCM, the format every recorder produces. */
private fun pcm(vararg samples: Int): ByteArray
{
    val bytes = ByteArray(samples.size * 2)
    samples.forEachIndexed { index, sample ->
        bytes[index * 2] = (sample and 0xFF).toByte()
        bytes[index * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
    }
    return bytes
}

class SpeechGateTest
{
    @Test
    fun loudnessOfAConstantAmplitudeIsThatAmplitude()
    {
        assertEquals(1000.0, rms(pcm(1000, -1000, 1000, -1000)), 0.001)
    }

    @Test
    fun negativeSamplesAreAsLoudAsPositiveOnes()
    {
        // The high byte carries the sign; reading it unsigned would make quiet audio look loud.
        assertEquals(rms(pcm(-500, -500)), rms(pcm(500, 500)), 0.001)
    }

    @Test
    fun theFullSignedRangeIsHandled()
    {
        val loudest = rms(pcm(-32768, 32767))
        assertTrue(abs(loudest - 32767.5) < 1.0, "expected near full scale, got $loudest")
    }

    @Test
    fun silenceAndEmptyChunksHaveNoLoudness()
    {
        assertEquals(0.0, rms(pcm(0, 0, 0)), 0.001)
        assertEquals(0.0, rms(ByteArray(0)), 0.001)
        // A truncated trailing byte is ignored rather than read past the end.
        assertEquals(0.0, rms(byteArrayOf(0)), 0.001)
    }

    @Test
    fun quietChunksBecomeSilenceMarkers() = runTest {
        val quiet = pcm(10, -10, 10, -10)

        val gated = flowOf(quiet).speechGate(threshold = 300).toList()

        assertEquals(1, gated.size)
        assertTrue(gated.single().isEmpty(), "a quiet chunk should be reported as silence")
    }

    @Test
    fun loudChunksPassThroughUntouched() = runTest {
        val speech = pcm(5000, -5000, 5000, -5000)

        val gated = flowOf(speech).speechGate(threshold = 300).toList()

        assertContentEquals(speech, gated.single(), "audio above the threshold must not be altered")
    }

    @Test
    fun theThresholdItselfCountsAsSilence() = runTest {
        // Exactly at the threshold is not speech: the gate opens above it.
        val atThreshold = pcm(300, -300)

        val gated = flowOf(atThreshold).speechGate(threshold = 300).toList()

        assertTrue(gated.single().isEmpty())
    }

    @Test
    fun eachChunkIsJudgedOnItsOwn() = runTest {
        val speech = pcm(5000, 5000)
        val quiet = pcm(1, 1)

        val gated = flowOf(speech, quiet, speech).speechGate(threshold = 300).toList()

        assertEquals(listOf(false, true, false), gated.map { it.isEmpty() })
    }
}
