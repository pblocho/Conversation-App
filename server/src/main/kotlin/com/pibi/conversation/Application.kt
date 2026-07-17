package com.pibi.conversation

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(WebSockets)
    routing {
        get("/") {
            call.respondText("Hello, Ktor!")
        }
        webSocket("/") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    send("Server received: $text")
                }
            }
        }
    }
}