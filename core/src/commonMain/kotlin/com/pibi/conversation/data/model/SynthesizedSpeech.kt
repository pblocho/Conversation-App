package com.pibi.conversation.data.model

/** One TTS result: the synthesized WAV audio together with the text it was generated from. */
class SynthesizedSpeech(val text: String, val audioWavBytes: ByteArray)
