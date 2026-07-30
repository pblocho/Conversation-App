package com.pibi.conversation.audioplayer

import com.pibi.conversation.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.DataLine

actual object AudioPlayer
{
    actual suspend fun playWavBytes(wavBytes: ByteArray) = withContext(Dispatchers.IO) {
        try
        {
            val byteArrayInputStream = ByteArrayInputStream(wavBytes)

            val audioInputStream: AudioInputStream = AudioSystem.getAudioInputStream(byteArrayInputStream)
            val format = audioInputStream.format

            val info = DataLine.Info(SourceDataLine::class.java, format)
            val line = AudioSystem.getLine(info) as SourceDataLine

            line.open(format)
            line.start()

            val buffer = ByteArray(4096)
            var bytesRead = 0
            while (audioInputStream.read(buffer).also { bytesRead = it } != -1)
            {
                line.write(buffer, 0, bytesRead)
            }

            line.drain()
            line.close()
            audioInputStream.close()

        } catch (e: Exception)
        {
            Log.player.e(e) { "Could not play the answer audio (JVM)" }
        }
    }
}