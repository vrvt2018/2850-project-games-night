package com.example

import com.example.games.Deck
import io.ktor.server.application.*
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.response.*
import io.ktor.server.routing.*
//import java.io.File

class GameEntity {
    val name: String
    val imgUrl: String

    constructor(name: String, imgUrl: String) {
        this.name = name
        this.imgUrl = imgUrl
    }

    fun getAltText() = name + " logo"
}

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

        get("/game")
        {

            /*
            val games = arrayOf( {
                GameEntity("Chess", "resources/assets/chess/Chess_Clt45.svg")
                GameEntity("Go Fish", "resources/static/assets/Chess_Clt45.svg")
            })

            val gamesList = arrayOf( {
                "<li><img src=\" alt=\"\"></img></li>"
            })*/

            call.respondTemplate("game.peb", model=emptyMap())//model=mapOf(gamesList to gamesList))
        }
    }
}
