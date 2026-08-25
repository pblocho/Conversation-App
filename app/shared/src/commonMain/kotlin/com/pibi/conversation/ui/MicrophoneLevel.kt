package com.pibi.conversation.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * How loud the microphone is hearing you, so it is obvious the app is listening rather than stuck.
 *
 * Takes the flow rather than a value, and collects it here: the level changes about eight times a
 * second, and reading it any higher up would redraw the whole screen at that rate.
 *
 * The flow carries the raw RMS amplitude of 16-bit audio, which is `core`'s business; turning it
 * into a fraction of a bar is this layer's. Speech sits well below full scale, so the bar fills at
 * a conversational level rather than at 32767.
 */
/** RMS of comfortable speech, which fills the meter. */
private const val SPEAKING_LEVEL = 6000.0

@Composable
fun MicrophoneLevel(levels: StateFlow<Double>)
{
    val volume by levels.collectAsStateWithLifecycle()
    val level = (volume / SPEAKING_LEVEL).coerceIn(0.0, 1.0).toFloat()

    LinearProgressIndicator(
        progress = { level },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
}