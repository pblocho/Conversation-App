package com.pibi.conversation.audioplayer

import com.pibi.conversation.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

actual object AudioPlayer
{
    actual suspend fun playWavBytes(wavBytes: ByteArray) = withContext(Dispatchers.IO) {
        try
        {
            // use/finally rather than closing at the end of the happy path: a failed write must
            // still hand the audio line back, or the next answer has nothing to play through.
            AudioSystem.getAudioInputStream(ByteArrayInputStream(wavBytes)).use { audio ->
                val format = audio.format
                val info = DataLine.Info(SourceDataLine::class.java, format)
                val line = AudioSystem.getLine(info) as SourceDataLine

                try
                {
                    line.open(format)
                    line.start()

                    val buffer = ByteArray(4096)
                    while (true)
                    {
                        val bytesRead = audio.read(buffer)
                        if (bytesRead == -1) break
                        line.write(buffer, 0, bytesRead)
                    }

                    line.drain()
                } finally
                {
                    line.close()
                }
            }
        } catch (e: Exception)
        {
            Log.player.e(e) { "Could not play the answer audio (JVM)" }
        }
    }
}
