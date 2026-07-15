package com.pibi.conversation.manager

import com.pibi.conversation.audiorecorder.AudioRecorder
import com.pibi.conversation.networking.SttClient.SttClient
import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

actual class ConversationManager actual constructor(
    private val ttsClient: TtsClient,
    private val sttClient: SttClient
)
{
    private val _uiState = MutableStateFlow(ConversationUiState())
    actual val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    actual suspend fun startConversation(textFlow: SharedFlow<String>) = coroutineScope {
        val startRecording = AudioRecorder.startRecording()

        _uiState.update { it.copy(isRecording = true, statusText = "Listening...") }

        launch {
            sttClient.textOutputFlow.collect { text ->
                _uiState.update { state ->
                    state.copy(statusText = "Recognized: $text")
                }
            }
        }

        launch {
            ttsClient.streamAudioFromTts(sttClient.textOutputFlow)
        }

        sttClient.streamAudioToStt(startRecording)

        _uiState.update { it.copy(isRecording = false, statusText = "Idle") }
    }
}
