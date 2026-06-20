package com.pibi.conversation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pibi.conversation.data.model.QuestionWithAnswer

@Composable
fun QuestionAndAnswer(
    questionWithAnswer: QuestionWithAnswer
)
{
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.LightGray),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(questionWithAnswer.question, modifier = Modifier.padding(16.dp))
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text(questionWithAnswer.answer, modifier = Modifier.padding(16.dp))
        }
    }
}