package com.pibi.conversation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pibi.conversation.data.model.Message

@Composable
fun Question(
    message: Message
)
{
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(message.text, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun Answer(
    message: Message
)
{
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(message.text, modifier = Modifier.padding(16.dp))
    }
}