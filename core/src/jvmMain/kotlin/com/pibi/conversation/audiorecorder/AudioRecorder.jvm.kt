package com.pibi.conversation.audiorecorder

import com.pibi.conversation.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

internal actual fun capturePcm(): Flow<ByteArray> = callbackFlow {
    val format = AudioFormat(16000f, 16, 1, true, false)
    val info = DataLine.Info(TargetDataLine::class.java, format)

    if (!AudioSystem.isLineSupported(info))
    {
        close(IllegalStateException("The microphone does not support 16 kHz 16-bit mono."))
        return@callbackFlow
    }

    val line = AudioSystem.getLine(info) as TargetDataLine
    line.open(format)
    line.start()

    Log.recorder.i { "Microphone started (JVM)" }

    val job = launch(Dispatchers.IO) {
        val buffer = ByteArray(4096)
        while (isActive)
        {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0)
            {
                // send, not trySend: suspending is what lets cancellation interrupt this loop,
                // and it applies backpressure instead of dropping audio silently.
                send(buffer.copyOf(bytesRead))
            } else if (bytesRead < 0)
            {
                break
            }
        }
    }

    awaitClose {
        job.cancel()
        // Closing the line makes a read() that is still waiting for data return at once, so the
        // microphone is really free before the next utterance opens it again. Without this the
        // line leaks and every later recording blocks forever.
        line.stop()
        line.close()
        Log.recorder.i { "Microphone released (JVM)" }
    }
}.flowOn(Dispatchers.IO)
