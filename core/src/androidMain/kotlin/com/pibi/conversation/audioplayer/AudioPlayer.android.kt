package com.pibi.conversation.audioplayer

// TODO: implement WAV playback with AudioTrack/MediaPlayer
actual object AudioPlayer
{
    actual fun playWavBytes(wavBytes: ByteArray)
    {
        println("AudioPlayer (Android): playback not implemented yet, received ${wavBytes.size} bytes")
    }
}
