package com.pibi.conversation.ui

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

    /** Kept apart from [uiState] so its ~8 Hz updates only redraw the level meter. */
    val microphoneLevel = manager.microphoneLevel

    init {
        viewModelScope.launch(Dispatchers.IO) {
            manager.startConversation()
        }
    }

    /** Cancels the turn in flight; the machine goes back to listening on its own. */
    fun onStopClicked() = manager.stop()

    /** The app is on screen again: start listening. */
    fun onAppForegrounded() = manager.resume()

    /** The app went out of sight: stop holding the microphone open. */
    fun onAppBackgrounded() = manager.pause()
}
