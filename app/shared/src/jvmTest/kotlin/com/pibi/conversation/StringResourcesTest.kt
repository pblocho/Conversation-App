package com.pibi.conversation

import conversation.app.shared.generated.resources.Res
import conversation.app.shared.generated.resources.reconnecting_assistant
import conversation.app.shared.generated.resources.reconnecting_both
import conversation.app.shared.generated.resources.reconnecting_speech_recognition
import conversation.app.shared.generated.resources.state_listening
import conversation.app.shared.generated.resources.status_format
import conversation.app.shared.generated.resources.stop
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A missing or mistranslated string compiles perfectly and only shows up on screen, so resolve a
 * few here in both languages the app ships.
 */
class StringResourcesTest
{
    private fun withLocale(locale: Locale, block: suspend () -> Unit) = runBlocking {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try
        {
            block()
        } finally
        {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun englishIsTheDefault() = withLocale(Locale.ENGLISH) {
        assertEquals("Stop", getString(Res.string.stop))
        assertEquals("Listening…", getString(Res.string.state_listening))
    }

    @Test
    fun polishIsUsedWhenTheSystemAsksForIt() = withLocale(Locale.forLanguageTag("pl")) {
        assertEquals("Zatrzymaj", getString(Res.string.stop))
        assertEquals("Słucham…", getString(Res.string.state_listening))
        assertEquals("Ponowne łączenie z asystentem…", getString(Res.string.reconnecting_assistant))
    }

    @Test
    fun theStatusLineTakesItsArgument() = withLocale(Locale.ENGLISH) {
        assertEquals("Status: Listening…", getString(Res.string.status_format, "Listening…"))
    }

    @Test
    fun everyReconnectCaseHasItsOwnSentence() = withLocale(Locale.forLanguageTag("pl")) {
        // Whole sentences, not a stem plus a joined list: "z" takes the instrumental case, so
        // Polish cannot be assembled from the backend names on their own.
        val sentences = setOf(
            getString(Res.string.reconnecting_speech_recognition),
            getString(Res.string.reconnecting_assistant),
            getString(Res.string.reconnecting_both)
        )

        assertEquals(3, sentences.size, "each case needs its own translatable sentence: $sentences")
    }
}
