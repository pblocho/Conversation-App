package com.pibi.conversation.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pibi.conversation.data.model.Message

/** What was heard from the speaker. Size and spacing are the caller's to decide, hence [modifier]. */
@Composable
fun Question(
    message: Message,
    modifier: Modifier = Modifier
)
{
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        modifier = modifier.padding(start = 50.dp)
    ) {
        Text(message.text, modifier = Modifier.padding(16.dp))
    }
}
