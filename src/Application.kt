// AI-assisted: Ktor plugin configuration and WebSocket setup (Gemini)
// Entry point: initialises DB, routing, templates, and WebSocket endpoints
package com.example

import com.example.network.RoomHandler
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

fun Application.module() {
    initDatabase()
    configureStatusPages()
    configureRouting()
    configureTemplates()
    configureApi()

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    routing {
        webSocket("/room-status") {
            RoomHandler.handleStatusSubscription(this)
        }
        webSocket("/chess") {
            RoomHandler.handle(this)
        }
        webSocket("/gofish") {
            RoomHandler.handle(this)
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log
                .error("Unhandled exception", cause)
            call.respondText("Internal server error", status = HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondText("Page not found", status = HttpStatusCode.NotFound)
        }
    }
}

fun Application.configureTemplates() {
    install(Pebble) {
        loader(
            ClasspathLoader().apply {
                prefix = "templates"
            },
        )
    }
}

fun Application.configureApi() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
}
