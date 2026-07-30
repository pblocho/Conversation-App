package com.pibi.conversation

object AppConfig {
    /**
     * Where the STT/TTS backends are reachable from *this* platform. The backends run on the
     * developer's machine, which is not the same address everywhere — see [defaultServerHost].
     *
     * `SttClient`/`TtsClient` take a host of their own, so anything that needs a different address
     * (a phone on the same Wi-Fi, say) can pass one instead of changing this.
     */
    val SERVER_HOST: String = defaultServerHost()
    const val TTS_PORT = 8000
    const val STT_PORT = 8001
    const val AUDIO_THRESHOLD = 300
}

/**
 * The loopback address of the machine running the backends, as seen from this platform:
 *
 * - **Desktop** — the same machine, so plain loopback.
 * - **Android emulator** — the emulator is its own virtual device, and reaches the host through
 *   the special alias `10.0.2.2`; its own `127.0.0.1` is the emulator itself.
 * - **Android device / iOS** — loopback, which works over `adb reverse` (see the README). A device
 *   reaching the host over Wi-Fi instead needs the machine's LAN address passed to the clients.
 */
internal expect fun defaultServerHost(): String
