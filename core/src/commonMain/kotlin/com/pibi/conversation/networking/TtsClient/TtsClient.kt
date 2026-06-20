package com.pibi.conversation.networking.TtsClient

import com.pibi.conversation.audioplayer.playWavBytes
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TtsClient()
{
    companion object
    {
        private val client = HttpClient {
            install(WebSockets)
        }
    }

    suspend fun streamAudioFromTts(textInputFlow: SharedFlow<String>)
    {
        try
        {
            client.webSocket(host = "127.0.0.1", port = 8000, path = "/ws") {

                val sendJob = launch {
                    textInputFlow.collect { pieceOfText ->
                        send(Frame.Text(pieceOfText))
                    }
                }

                for (frame in incoming)
                {
                    if (frame is Frame.Binary)
                    {
                        val audioChunk = frame.readBytes()
                        withContext(Dispatchers.IO) {
                            playWavBytes(audioChunk)
                        }
                    }
                }
                sendJob.cancel()
            }

        } catch (e: Exception)
        {
            println("Error connecting to TTS server: ${e.message}")
        }
    }
}