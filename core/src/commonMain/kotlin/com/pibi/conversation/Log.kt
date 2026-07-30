package com.pibi.conversation

import co.touchlab.kermit.Logger

/**
 * Loggers for the parts of the pipeline worth following at runtime. Kermit routes these to the
 * place each platform actually looks — Logcat on Android, `os_log` on iOS, stdout on the JVM —
 * which `println` never did, and gives the levels needed to keep lifecycle chatter out of the way
 * of real failures.
 */
internal object Log
{
    val recorder = Logger.withTag("AudioRecorder")
    val player = Logger.withTag("AudioPlayer")
    val conversation = Logger.withTag("Conversation")
}
