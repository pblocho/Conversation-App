package com.pibi.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pibi.conversation.data.model.Message
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.manager.ConnectionState
import com.pibi.conversation.manager.ConversationState
import conversation.app.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
@Preview
fun App(
    viewModel: ConversationViewModel = viewModel { ConversationViewModel() }
)
{
    val uiState by viewModel.uiState.collectAsState()
    // The backend answers one message per sentence; merge them so a whole turn is one card.
    val turns = remember(uiState.messages) { uiState.messages.mergeConsecutive() }

    MaterialTheme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.status_format, uiState.state.label()),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(8.dp)
            )

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
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = turns,
                    key = { it.id }
                ) { msg ->
                    when (msg.messageType)
                    {
                        MessageType.QUESTION -> Question(msg)
                        MessageType.ANSWER -> Answer(msg)
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
 * Tells the user which backend is missing while the manager reconnects to it.
 *
 * One whole sentence per case instead of a stem plus a joined list: Polish puts the object of
 * "łączenie z" in the instrumental case, so a sentence built from fragments would be ungrammatical.
 */
@Composable
private fun ConnectionBanner(connection: ConnectionState)
{
    val message = when
    {
        !connection.sttUp && !connection.ttsUp -> stringResource(Res.string.reconnecting_both)
        !connection.sttUp -> stringResource(Res.string.reconnecting_speech_recognition)
        else -> stringResource(Res.string.reconnecting_assistant)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            connection.lastError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Joins runs of messages of the same kind into one message, so the sentences the backend sends
 * separately read as a single turn. The first message of a run keeps its id, which keeps the
 * list key stable while the turn grows.
 */
private fun List<Message>.mergeConsecutive(): List<Message> =
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
