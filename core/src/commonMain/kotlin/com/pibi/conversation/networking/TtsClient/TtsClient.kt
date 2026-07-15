package com.pibi.conversation.networking.TtsClient

import com.pibi.conversation.AppConfig
import com.pibi.conversation.audioplayer.AudioPlayer.playWavBytes
import com.pibi.conversation.grpc.TextPiece
import com.pibi.conversation.grpc.TtsService
import com.pibi.conversation.grpc.invoke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.rpc.grpc.client.GrpcClient
import kotlinx.rpc.withService

class TtsClient
{
    companion object
    {
        private val client = GrpcClient(AppConfig.SERVER_HOST, AppConfig.TTS_PORT) {
            credentials = plaintext()
        }
    }

    suspend fun streamAudioFromTts(textInputFlow: SharedFlow<String>)
    {
        try
        {
            val service = client.withService<TtsService>()

            val requests = textInputFlow.map { pieceOfText ->
                TextPiece { text = pieceOfText }
            }

            service.Synthesize(requests).collect { audio ->
                val audioChunk = audio.data.toByteArray()
                withContext(Dispatchers.IO) {
                    playWavBytes(audioChunk)
                }
            }
        } catch (e: Exception)
        {
            println("Error connecting to TTS server: ${e.message}")
        }
    }
}
