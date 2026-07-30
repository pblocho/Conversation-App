package com.pibi.conversation.audiorecorder

import com.pibi.conversation.AppConfig
import kotlinx.coroutines.flow.Flow

object AudioRecorder
{
    /**
     * Microphone audio as 16 kHz, 16-bit signed mono PCM, with anything quieter than
     * [AppConfig.AUDIO_THRESHOLD] replaced by an empty chunk meaning "silence".
     *
     * Only the capture is platform-specific — see [capturePcm].
     */
    fun startRecording(): Flow<ByteArray> = capturePcm().speechGate(AppConfig.AUDIO_THRESHOLD)
}

/**
 * Raw microphone chunks: 16 kHz, 16-bit signed, mono, little-endian, no loudness filtering.
 * Collecting opens the microphone; cancelling the collection must hand it back.
 */
internal expect fun capturePcm(): Flow<ByteArray>
