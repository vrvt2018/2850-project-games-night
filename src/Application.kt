package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.pebble.Pebble
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.serialization.gson.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import com.example.network.GoFishSocket
import io.ktor.server.websocket.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    initDatabase()
    configureStatusPages()
    configureRouting()
    configureTemplates()
    configureApi()
    install(WebSockets)
    routing {
        webSocket("/gofish") {
            GoFishSocket.handle(this)
        }
    }
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respondText("Internal server error", status = HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondText("Page not found", status = HttpStatusCode.NotFound)
        }
        status(HttpStatusCode.Unauthorized) { call, _ ->
            call.respondRedirect("/")
        }
    }
}

fun Application.configureTemplates() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates" // templates stored in resources/templates
        })
    }
}

fun Application.configureApi() {
    install(ContentNegotiation) {
        gson {
            setPrettyPrinting()
        }
    }
}