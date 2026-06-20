package com.pibi.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.pibi.conversation.data.model.QuestionWithAnswer
import com.pibi.conversation.manager.ConversationManager
import com.pibi.conversation.networking.TtsClient.TtsClient
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Composable
@Preview
fun App()
{
    val scope = rememberCoroutineScope()
    val textFlow = remember { MutableSharedFlow<String>() }
    var textToSpeak by remember { mutableStateOf("Hello World. I'm Conversation App!") }
    val questionsAndAnswers = remember {
        mutableStateListOf<QuestionWithAnswer>(
            QuestionWithAnswer(
                "What is the meaning of life?",
                "Coding!"
            )
        )
    }

    LaunchedEffect(Unit) {
        ConversationManager(TtsClient()).startConversation(textFlow)
    }

    MaterialTheme {
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(
                items = questionsAndAnswers,
                key = { it.question }
            ) { questionAndAnswer ->
                QuestionAndAnswer(questionAndAnswer)
            }

            item {
                TextField(
                    value = textToSpeak,
                    onValueChange = { textToSpeak = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = {
                    scope.launch {
                        textFlow.emit(textToSpeak)
                    }
                }) {
                    Text("Ask question")
                }
            }
        }
    }
}