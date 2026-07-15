package com.pibi.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pibi.conversation.manager.ConversationManager
import com.pibi.conversation.networking.SttClient.SttClient
import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class ConversationViewModel(
    private val manager: ConversationManager = ConversationManager(TtsClient(), SttClient())
) : ViewModel() {
    
    val uiState = manager.uiState
    private val textFlow = MutableSharedFlow<String>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            manager.startConversation(textFlow)
        }
    }

    fun ask(text: String) {
        viewModelScope.launch {
            textFlow.emit(text)
        }
    }
}
