package com.example

import com.example.games.Deck
import io.ktor.server.application.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.*
import io.ktor.server.routing.*
//import java.io.File

fun Application.configureRouting() {
    routing {
        staticResources("/resources", "/static")

        get("/") {
            //call.respondText("Hello World!!!!")
            call.respondTemplate("login.peb", model=mapOf("title" to "Login"))
        }

        get("/signup")
        {
            call.respondTemplate("signup.peb", model=mapOf("title" to "Sign up"))
        }

        get("/gamelist")
        {
            call.respondTemplate("gamelist.peb", model=emptyMap())
        }
    }
}
