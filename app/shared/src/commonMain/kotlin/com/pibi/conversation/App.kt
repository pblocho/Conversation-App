package com.pibi.conversation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pibi.conversation.data.model.Message
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.manager.ConversationState
import com.pibi.conversation.ui.theme.AppTheme
import com.pibi.conversation.ui.Answer
import com.pibi.conversation.ui.ConnectionBanner
import com.pibi.conversation.ui.ConversationViewModel
import com.pibi.conversation.ui.MicrophoneLevel
import com.pibi.conversation.ui.Question
import conversation.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@Preview
fun App(
    viewModel: ConversationViewModel = viewModel { ConversationViewModel() }
)
{
    // Lifecycle-aware: collection stops while the app is in the background.
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Hold the microphone only while the app is actually on screen.
    LifecycleStartEffect(viewModel) {
        viewModel.onAppForegrounded()
        onStopOrDispose { viewModel.onAppBackgrounded() }
    }
    // The backend answers one message per sentence; merge them so a whole turn is one card.
    val turns = remember(uiState.messages) { uiState.messages.mergeConsecutive() }

    // Follow the conversation: jump to the newest turn as it arrives, and keep following while
    // that turn grows — the backend appends sentence by sentence, which adds no new list item.
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size, turns.lastOrNull()?.text) {
        if (turns.isNotEmpty())
        {
            listState.animateScrollToItem(turns.lastIndex)
        }
    }

    AppTheme {
        // Surface rather than Modifier.background: it sets the background *and* the matching
        // content colour, so the text below stays legible when the palette flips to dark.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier
                    .safeContentPadding()
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(Res.string.status_format, uiState.state.label()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )

                if (uiState.isRecording)
                {
                    MicrophoneLevel(viewModel.microphoneLevel)
                }

                uiState.turnError?.let { error ->
                    Text(
                        text = stringResource(Res.string.turn_error, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                if (!uiState.connection.isReady)
                {
                    ConnectionBanner(uiState.connection)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(
                        items = turns,
                        key = { it.id },
                        // Questions and answers are laid out alike, so Compose can reuse one's
                        // composition for the other instead of building it from scratch.
                        contentType = { it.messageType }
                    ) { msg ->
                        val bubble = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        when (msg.messageType)
                        {
                            MessageType.QUESTION -> Question(msg, bubble)
                            MessageType.ANSWER -> Answer(msg, bubble)
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = viewModel::onStopClicked) {
                        Text(stringResource(Res.string.stop))
                    }
                }
            }
        }
    }
}

/**
 * What each step of the conversation is called on screen. Wording lives in the UI, not in `core`,
 * and comes from string resources so it follows the reader's language.
 */
@Composable
private fun ConversationState.label(): String = when (this)
{
    ConversationState.Init -> stringResource(Res.string.state_connecting)
    ConversationState.Idle -> stringResource(Res.string.state_idle)
    ConversationState.RecordingAudio -> stringResource(Res.string.state_listening)
    ConversationState.SendToStt -> stringResource(Res.string.state_sending_audio)
    ConversationState.WaitForTextForLlm -> stringResource(Res.string.state_recognizing)
    ConversationState.SendTextToLlm -> stringResource(Res.string.state_asking)
    ConversationState.WaitForAudioAndTextFromLlm -> stringResource(Res.string.state_answering)
}

/**
 * Joins runs of messages of the same kind into one message, so the sentences the backend sends
 * separately read as a single turn. The first message of a run keeps its id, which keeps the
 * list key stable while the turn grows.
 */
internal fun List<Message>.mergeConsecutive(): List<Message> =
    fold(mutableListOf<Message>()) { turns, message ->
        val turn = turns.lastOrNull()
        if (turn != null && turn.messageType == message.messageType)
        {
            turns[turns.lastIndex] = turn.copy(text = "${turn.text} ${message.text}")
        } else
        {
            turns += message
        }
        turns
    }
