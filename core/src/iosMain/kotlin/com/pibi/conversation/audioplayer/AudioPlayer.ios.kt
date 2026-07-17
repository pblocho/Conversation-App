package com.pibi.conversation.audioplayer

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.usleep

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object AudioPlayer
{
    actual fun playWavBytes(wavBytes: ByteArray)
    {
        try
        {
            if (wavBytes.isEmpty()) return

            val data = wavBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = wavBytes.size.toULong())
            }

            val player = AVAudioPlayer(data = data, error = null)
            player.prepareToPlay()
            player.play()

            // Block until playback finishes, like the JVM line.drain() —
            // TtsClient plays chunks sequentially on Dispatchers.IO.
            while (player.playing)
            {
                usleep(10_000u)
            }
        } catch (e: Exception)
        {
            println("Error playing audio on iOS: ${e.message}")
        }
    }
}
