package com.pibi.conversation.audioplayer

// TODO: implement WAV playback with AVAudioPlayer/AVAudioEngine
actual object AudioPlayer
{
    actual fun playWavBytes(wavBytes: ByteArray)
    {
        println("AudioPlayer (iOS): playback not implemented yet, received ${wavBytes.size} bytes")
    }
}
