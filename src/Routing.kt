package com.example

import com.example.games.Deck
import io.ktor.server.application.*
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            //call.respondText("Hello World!!!!")
            call.respondTemplate("login.peb", model=mapOf("title" to "Login"))
        }

        get("/signup")
        {
            call.respondTemplate("signup.peb", model=mapOf("title" to "Sign up"))
        }
    }
}
