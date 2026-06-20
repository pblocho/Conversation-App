package com.pibi.conversation

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform