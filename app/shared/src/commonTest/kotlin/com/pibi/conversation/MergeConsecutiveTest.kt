package com.pibi.conversation

import com.pibi.conversation.data.model.Message
import com.pibi.conversation.data.model.MessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private fun message(text: String, type: MessageType, id: String) =
    Message(text, Instant.fromEpochSeconds(0), type, id)

private fun question(text: String, id: String = text) = message(text, MessageType.QUESTION, id)
private fun answer(text: String, id: String = text) = message(text, MessageType.ANSWER, id)

/**
 * The backend answers one message per sentence, so what the list shows is a merge of those runs.
 */
class MergeConsecutiveTest
{
    @Test
    fun sentencesOfOneAnswerBecomeOneMessage()
    {
        val merged = listOf(
            answer("It is sunny."),
            answer("Twenty degrees."),
            answer("No rain expected.")
        ).mergeConsecutive()

        assertEquals(1, merged.size)
        assertEquals("It is sunny. Twenty degrees. No rain expected.", merged.single().text)
    }

    @Test
    fun questionsAndAnswersStaySeparate()
    {
        val merged = listOf(
            question("What is the weather?"),
            answer("It is sunny."),
            answer("Twenty degrees."),
            question("And tomorrow?"),
            answer("Rain.")
        ).mergeConsecutive()

        assertEquals(
            listOf("What is the weather?", "It is sunny. Twenty degrees.", "And tomorrow?", "Rain."),
            merged.map { it.text }
        )
        assertEquals(
            listOf(MessageType.QUESTION, MessageType.ANSWER, MessageType.QUESTION, MessageType.ANSWER),
            merged.map { it.messageType }
        )
    }

    @Test
    fun aMergedTurnKeepsTheIdOfItsFirstMessage()
    {
        val merged = listOf(
            answer("First.", id = "first"),
            answer("Second.", id = "second")
        ).mergeConsecutive()

        // The list keys off this id: keeping the first one lets the card grow as sentences
        // arrive, instead of being thrown away and rebuilt on every new sentence.
        assertEquals("first", merged.single().id)
    }

    @Test
    fun growingAnAnswerKeepsTheSameKeys()
    {
        val afterOneSentence = listOf(question("Hi?", id = "q"), answer("One.", id = "a1"))
        val afterTwo = afterOneSentence + answer("Two.", id = "a2")

        assertEquals(
            afterOneSentence.mergeConsecutive().map { it.id },
            afterTwo.mergeConsecutive().map { it.id },
            "a new sentence must not renumber the list"
        )
    }

    @Test
    fun nothingToMergeIsHandled()
    {
        assertTrue(emptyList<Message>().mergeConsecutive().isEmpty())

        val single = listOf(question("Alone?"))
        assertEquals(single.map { it.text }, single.mergeConsecutive().map { it.text })
    }
}
