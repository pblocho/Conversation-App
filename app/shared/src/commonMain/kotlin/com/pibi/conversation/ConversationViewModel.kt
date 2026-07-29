package com.pibi.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pibi.conversation.data.repository.GrpcConversationRepository
import com.pibi.conversation.manager.ConversationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

class ConversationViewModel : ViewModel() {
    private val manager = ConversationManager(GrpcConversationRepository())
    val uiState = manager.uiState

    init {
        viewModelScope.launch(Dispatchers.IO) {
            manager.startConversation()
        }
    }

    /** Cancels the turn in flight; the machine goes back to listening on its own. */
    fun onStopClicked() = manager.stop()
}
