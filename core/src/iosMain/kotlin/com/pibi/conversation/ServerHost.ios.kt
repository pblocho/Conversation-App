package com.pibi.conversation

/**
 * The simulator shares the Mac's network stack, so loopback reaches the backends directly.
 * A physical iPhone does not: give the clients the Mac's LAN address instead.
 */
internal actual fun defaultServerHost(): String = "127.0.0.1"
