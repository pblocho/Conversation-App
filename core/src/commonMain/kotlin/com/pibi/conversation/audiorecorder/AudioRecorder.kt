package com.pibi.conversation.audiorecorder

import kotlinx.coroutines.flow.Flow

expect object AudioRecorder
{
    fun startRecording(): Flow<ByteArray>
}
