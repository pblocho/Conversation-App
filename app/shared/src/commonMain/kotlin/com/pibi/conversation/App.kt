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
                text = "Status: ${uiState.statusText}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(8.dp)
            )

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
                    Text("Stop")
                }
            }
        }
    }
}

/** Tells the user which backend is missing while the manager reconnects to it. */
@Composable
private fun ConnectionBanner(connection: ConnectionState)
{
    val missing = buildList {
        if (!connection.sttUp) add("speech recognition")
        if (!connection.ttsUp) add("the assistant")
    }.joinToString(" and ")

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Reconnecting to $missing…",
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
