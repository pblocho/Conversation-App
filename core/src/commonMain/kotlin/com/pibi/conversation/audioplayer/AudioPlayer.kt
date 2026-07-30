package com.pibi.conversation.audioplayer

expect object AudioPlayer
{
    /**
     * Plays WAV bytes and returns when they have finished sounding, so queued sentences do not
     * overlap.
     *
     * Suspending, and each platform confines its own blocking work: the caller cannot freeze the
     * UI thread with it by mistake.
     */
    suspend fun playWavBytes(wavBytes: ByteArray)
}
