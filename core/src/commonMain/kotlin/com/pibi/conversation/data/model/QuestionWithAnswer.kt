package com.pibi.conversation.data.model


data class QuestionWithAnswer(val question: String, val answer: String, val id: String = randomUuid())
