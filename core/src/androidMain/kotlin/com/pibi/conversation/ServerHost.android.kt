package com.pibi.conversation

import android.os.Build

/** The alias every Android emulator resolves to the host machine's loopback. */
private const val EMULATOR_HOST = "10.0.2.2"

/** On a real handset this only reaches the host through `adb reverse` — see the README. */
private const val DEVICE_HOST = "127.0.0.1"

internal actual fun defaultServerHost(): String = if (isEmulator) EMULATOR_HOST else DEVICE_HOST

/**
 * Best-effort emulator detection from the build fingerprint. Getting it wrong only picks the
 * wrong loopback alias, which surfaces immediately as a failed connection in the UI rather than
 * as anything subtle.
 */
private val isEmulator: Boolean
    get() = Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk")
