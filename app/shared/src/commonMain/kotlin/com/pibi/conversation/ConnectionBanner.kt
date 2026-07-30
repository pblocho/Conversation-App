package com.pibi.conversation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pibi.conversation.manager.ConnectionState
import conversation.app.shared.generated.resources.Res
import conversation.app.shared.generated.resources.reconnecting_assistant
import conversation.app.shared.generated.resources.reconnecting_both
import conversation.app.shared.generated.resources.reconnecting_speech_recognition
import org.jetbrains.compose.resources.stringResource

/**
 * Tells the user which backend is missing while the manager reconnects to it.
 *
 * One whole sentence per case instead of a stem plus a joined list: Polish puts the object of
 * "łączenie z" in the instrumental case, so a sentence built from fragments would be ungrammatical.
 */
@Composable
fun ConnectionBanner(connection: ConnectionState)
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