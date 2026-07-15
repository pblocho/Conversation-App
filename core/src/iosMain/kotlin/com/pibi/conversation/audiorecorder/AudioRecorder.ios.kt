package com.pibi.conversation.audiorecorder

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual object AudioRecorder {
    actual fun startRecording(): Flow<ByteArray> = emptyFlow()
}
