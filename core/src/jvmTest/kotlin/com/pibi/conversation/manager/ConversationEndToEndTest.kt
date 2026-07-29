package com.pibi.conversation.manager

import com.pibi.conversation.AppConfig
import com.pibi.conversation.data.model.MessageType
import com.pibi.conversation.data.repository.GrpcConversationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Drives the whole pipeline against the **real** backends: a recorded question goes out over the
 * STT stream, and the spoken answer comes back over the TTS stream. Only the microphone and the
 * speaker are stood in for — everything between them is the production path.
 *
 * Start the backends first (Python project: `scripts/start_servers.sh`; the TTS one needs Ollama).
 * With them down the test reports itself as skipped, so a plain `./gradlew :core:jvmTest` stays
 * green without them.
 */
class ConversationEndToEndTest
{
    private companion object
    {
        /** Whisper, an LLM and a speech synthesizer all run on CPU here; give them room. */
        const val PIPELINE_TIMEOUT_MILLIS = 240_000L
        const val CHUNK_SIZE = 4096

        /** Enough empty chunks for the manager to call the utterance finished. */
        const val SILENT_CHUNKS = 12
    }

    @Test
    fun spokenQuestionComesBackAsSpokenAnswer() = runBlocking {
        if (!backendIsUp(AppConfig.STT_PORT) || !backendIsUp(AppConfig.TTS_PORT))
        {
            println(
                "SKIPPED: no STT on ${AppConfig.STT_PORT} / TTS on ${AppConfig.TTS_PORT}. " +
                        "Start the Python backends and run this again."
            )
            return@runBlocking
        }

        val spokenAnswers = mutableListOf<ByteArray>()
        val manager = ConversationManager(
            repository = GrpcConversationRepository(),
            recordAudio = ::recordedQuestion,
            playAudio = { spokenAnswers += it }
        )

        val conversation: Job = launch { manager.startConversation() }
        try
        {
            val answered = withTimeout(PIPELINE_TIMEOUT_MILLIS) {
                manager.uiState.first { state -> state.messages.any { it.messageType == MessageType.ANSWER } }
            }

            val question = answered.messages.first { it.messageType == MessageType.QUESTION }.text
            val answer = answered.messages.first { it.messageType == MessageType.ANSWER }.text
            println("STT heard : $question")
            println("LLM said  : $answer")

            assertTrue(question.contains("capital", ignoreCase = true), "STT should hear the question, got: $question")
            assertTrue(answer.isNotBlank(), "the LLM should answer")

            val audio = spokenAnswers.first()
            println("TTS audio : ${audio.size} bytes")
            assertTrue(audio.size > 1_000, "the answer should carry real audio, got ${audio.size} bytes")
            assertTrue(audio.decodeToString(0, 4) == "RIFF", "the answer audio should be a WAV")

            File("build/e2e-answer.wav").apply { parentFile.mkdirs() }.writeBytes(audio)
            println("Wrote the spoken answer to core/build/e2e-answer.wav")
        } finally
        {
            conversation.cancel()
        }
    }

    /** The microphone stand-in: speaks the recorded question once, then stays silent. */
    private val questionAsked = AtomicBoolean(false)

    private fun recordedQuestion(): Flow<ByteArray> = flow {
        if (!questionAsked.getAndSet(true))
        {
            val pcm = questionPcm()
            for (offset in pcm.indices step CHUNK_SIZE)
            {
                emit(pcm.copyOfRange(offset, minOf(offset + CHUNK_SIZE, pcm.size)))
            }
        }

        repeat(SILENT_CHUNKS) { emit(ByteArray(0)) }
        // Nothing more is ever said, so the next turn just keeps listening.
        awaitCancellation()
    }

    /** Raw 16 kHz mono PCM of the recorded question, exactly what the recorder would produce. */
    private fun questionPcm(): ByteArray
    {
        val wav = checkNotNull(javaClass.getResourceAsStream("/e2e-question.wav")) {
            "missing test fixture e2e-question.wav"
        }

        return wav.buffered().use { stream ->
            AudioSystem.getAudioInputStream(stream).use { audio ->
                check(audio.format.sampleRate == 16_000f && audio.format.channels == 1) {
                    "fixture must be 16 kHz mono, was ${audio.format}"
                }
                audio.readAllBytes()
            }
        }
    }

    private fun backendIsUp(port: Int): Boolean = try
    {
        Socket().use { it.connect(InetSocketAddress(AppConfig.SERVER_HOST, port), 500) }
        true
    } catch (unreachable: Exception)
    {
        false
    }
}
