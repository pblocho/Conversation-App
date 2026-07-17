package com.pibi.conversation.manager

import com.pibi.conversation.audioplayer.AudioPlayer
import com.pibi.conversation.audiorecorder.AudioRecorder
import com.pibi.conversation.data.model.Message
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.repository.ConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ConversationManager(
    private val repository: ConversationRepository,
    scope: CoroutineScope
)
{
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    suspend fun startConversation() = coroutineScope {
        val startRecording = AudioRecorder.startRecording()

        _uiState.update { it.copy(isRecording = true, statusText = "Listening...") }

        launch {
            repository.transcripts.collect { text ->
                _uiState.update { state ->
                    state.copy(
                        messages = (state.messages + Message(text, Clock.System.now(), MessageType.QUESTION)),
                        statusText = "Recognized: $text"
                    )
                }
            }
        }

        launch {
            repository.synthesizeSpeech(repository.transcripts).collect { speech ->
                _uiState.update { state ->
                    state.copy(
                        messages = (state.messages + Message(speech.text, Clock.System.now(), MessageType.ANSWER)),
                        statusText = "Synthesized: ${speech.text}"
                    )
                }
                withContext(Dispatchers.IO) {
                    AudioPlayer.playWavBytes(speech.audioWavBytes)
                }
            }
        }

        repository.transcribe(startRecording)

        _uiState.update { it.copy(isRecording = false, statusText = "Idle") }
    }
}
