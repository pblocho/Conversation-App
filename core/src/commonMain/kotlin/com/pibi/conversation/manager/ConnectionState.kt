package com.pibi.conversation.manager

import androidx.compose.runtime.Immutable

/**
 * Health of the two backend streams, as far as the app can tell.
 *
 * A stream counts as up once an attempt has run for a moment without failing — a gRPC stream
 * gives no stronger signal than that until data actually flows, so this is a belief, not a
 * guarantee. A stream that turns out to be dead fails on first use and flips back to down, and
 * the manager reconnects it.
 */
@Immutable
data class ConnectionState(
    val sttUp: Boolean = false,
    val ttsUp: Boolean = false,
    /** Why the last stream dropped, kept for the UI while a reconnect is in flight. */
    val lastError: String? = null
)
{
    /** Both backends are believed healthy, so a turn can start. */
    val isReady: Boolean get() = sttUp && ttsUp
}
