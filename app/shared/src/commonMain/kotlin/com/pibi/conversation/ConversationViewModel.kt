package com.pibi.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pibi.conversation.data.repository.ConversationRepository
import com.pibi.conversation.manager.ConversationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ConversationViewModel : ViewModel() {
    private val manager = ConversationManager(ConversationRepository(), viewModelScope)
    val uiState = manager.uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            manager.startConversation()
        }
    }
}
