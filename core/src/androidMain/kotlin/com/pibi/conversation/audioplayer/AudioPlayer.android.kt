package com.pibi.conversation.audioplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.pibi.conversation.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

actual object AudioPlayer
{
    actual suspend fun playWavBytes(wavBytes: ByteArray) = withContext(Dispatchers.IO) {
        try
        {
            if (wavBytes.size <= 44) return@withContext

            val header = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
            val channels = header.getShort(22).toInt()
            val sampleRate = header.getInt(24)
            val bitsPerSample = header.getShort(34).toInt()

            // Locate the "data" chunk — WAV files may carry extra chunks before it.
            var offset = 12
            var dataOffset = -1
            var dataSize = 0
            while (offset + 8 <= wavBytes.size)
            {
                val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
                val chunkSize = header.getInt(offset + 4)
                if (chunkId == "data")
                {
                    dataOffset = offset + 8
                    dataSize = minOf(chunkSize, wavBytes.size - dataOffset)
                    break
                }
                offset += 8 + chunkSize + (chunkSize and 1)
            }
            if (dataOffset < 0 || dataSize <= 0)
            {
                Log.player.e { "Could not play the answer audio (Android): no data chunk in the WAV" }
                return@withContext
            }

            val channelMask = if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val encoding = if (bitsPerSample == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)

            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .setEncoding(encoding)
                    .build(),
                maxOf(minBufferSize, 4096),
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )

            track.play()

            var position = dataOffset
            val end = dataOffset + dataSize
            while (position < end)
            {
                val written = track.write(wavBytes, position, minOf(4096, end - position))
                if (written <= 0) break
                position += written
            }

            // Block until playback finishes, like the JVM line.drain() —
            // TtsClient plays chunks sequentially on Dispatchers.IO.
            val bytesPerFrame = channels * (bitsPerSample / 8)
            val totalFrames = if (bytesPerFrame > 0) dataSize / bytesPerFrame else 0
            while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < totalFrames)
            {
                Thread.sleep(10)
            }

            track.stop()
            track.release()
        } catch (e: Exception)
        {
            Log.player.e(e) { "Could not play the answer audio (Android)" }
        }
    }
}
