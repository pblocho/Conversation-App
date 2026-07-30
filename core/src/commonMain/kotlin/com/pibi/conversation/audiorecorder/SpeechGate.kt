package com.pibi.conversation.audiorecorder

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.sqrt

/**
 * Replaces every chunk quieter than [threshold] with an empty chunk — the convention the rest of
 * the pipeline reads as silence, and counts to decide that an utterance has ended.
 *
 * Capturing audio differs per platform; deciding what counts as speech does not, so this lives
 * here instead of being repeated in each `capturePcm` actual.
 */
internal fun Flow<ByteArray>.speechGate(threshold: Int): Flow<ByteArray> = map { chunk ->
    if (rms(chunk) > threshold) chunk else ByteArray(0)
}

/**
 * Root-mean-square amplitude of 16-bit signed little-endian PCM, which is what every recorder
 * produces. Returns 0 for an empty chunk, and ignores a trailing odd byte rather than reading
 * past the end.
 */
internal fun rms(pcm16: ByteArray): Double
{
    val sampleCount = pcm16.size / 2
    if (sampleCount == 0) return 0.0

    var sum = 0.0
    for (index in 0 until sampleCount)
    {
        val low = pcm16[index * 2].toInt() and 0xFF
        // The high byte keeps its sign, which is what makes the sample signed.
        val high = pcm16[index * 2 + 1].toInt()
        val sample = (high shl 8) or low
        sum += sample.toDouble() * sample.toDouble()
    }

    return sqrt(sum / sampleCount)
}
