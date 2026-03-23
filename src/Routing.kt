package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.resources
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.*
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

data class User(val username: String, val password: String, val email: String)

fun Application.configureRouting() {
    routing {
        staticResources("/resources", "/static")

        get("/") {
            val error = call.request.queryParameters["error"]
            val model = mutableMapOf<String, Any>("title" to "Login")
            if (!error.isNullOrBlank()) {
                model["error"] = error
            }
            call.respondTemplate("login.peb", model = model)
        }

        get("/signup") {
            call.respondTemplate("signup.peb", model = mapOf("title" to "Sign up"))
        }

        get("/games") {
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) }
            if (user == null) {
                return@get call.respondRedirect("/")
            }
            val games = findAllGames().map { mapOf("name" to it.name, "maxPlayers" to it.maxPlayers) }
            val model = mutableMapOf<String, Any>("title" to "Games", "games" to games, "hasGames" to games.isNotEmpty(), "user" to user)
            call.respondTemplate("game.peb", model = model)
        }

        get("/games/{name}") {
            val name = call.parameters["name"] ?: return@get call.respondText("game name required", status = HttpStatusCode.BadRequest)
            val game =
                findGameByName(name) ?: return@get call.respondText("game not found", status = HttpStatusCode.NotFound)
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) }
            val model = mutableMapOf<String, Any>("title" to game.name, "game" to mapOf("name" to game.name, "maxPlayers" to game.maxPlayers), "hasGames" to false)
            if (user != null) model["user"] = user
            call.respondTemplate("game.peb", model = model)
        }

        fun handleSignup(formParams: Parameters, requestTypeForm: Boolean): Pair<HttpStatusCode, String> {
            val username = formParams["username"]?.trim() ?: return HttpStatusCode.BadRequest to "username required"
            val email = formParams["email"]?.trim() ?: return HttpStatusCode.BadRequest to "email required"
            val password = formParams["password"] ?: return HttpStatusCode.BadRequest to "password required"
            val passwordConfirm = formParams["password_confirm"] ?: return HttpStatusCode.BadRequest to "password confirm required"
            if (password != passwordConfirm) return HttpStatusCode.BadRequest to "password mismatch"

            val success = createUser(username, email, hashPassword(password))
            if (!success) return HttpStatusCode.Conflict to "user exists"

            return if (requestTypeForm) HttpStatusCode.Created to "redirect" else HttpStatusCode.Created to "signup successful"
        }

        post("/api/auth/signup") {
            val formParams = call.receiveParameters()
            val result = handleSignup(formParams, false)
            if (result.second == "redirect") {
                call.respondRedirect("/games")
            } else {
                call.respondText(result.second, status = result.first)
            }
        }

        post("/signup") {
            val formParams = call.receiveParameters()
            val result = handleSignup(formParams, true)
            if (result.second == "redirect") {
                call.respondRedirect("/games")
            } else {
                call.respondText(result.second, status = result.first)
            }
        }

        post("/api/auth/login") {
            val formParams = call.receiveParameters() // Get results from form
            val username = formParams["username"]?.trim() ?: return@post call.respondText("username required", status = HttpStatusCode.BadRequest) // No username
            val password = formParams["password"] ?: return@post call.respondText("password required", status = HttpStatusCode.BadRequest) // No password

            val user = findUserByCredentials(username, hashPassword(password))
            if (user == null) {
                return@post call.respondText("invalid credentials", status = HttpStatusCode.Unauthorized)
            }
            val token = createSession(username)
            call.response.cookies.append("AUTH_TOKEN", token, maxAge = 3600)
            call.respond(mapOf("message" to "login successful", "username" to user.username, "email" to user.email))
        }

        post("/login") {
            val formParams = call.receiveParameters()
            val username = formParams["username"]?.trim() ?: return@post call.respondText("username required", status = HttpStatusCode.BadRequest)
            val password = formParams["password"] ?: return@post call.respondText("password required", status = HttpStatusCode.BadRequest)

            val user = findUserByCredentials(username, hashPassword(password))
            if (user == null) {
                return@post call.respondRedirect("/?error=invalid_credentials")
            }
            val token = createSession(username)
            call.response.cookies.append("AUTH_TOKEN", token, maxAge = 3600)
            call.respondRedirect("/games")
        }

        get("/api/games") {
            val gameList = findAllGames().map { mapOf("name" to it.name, "maxPlayers" to it.maxPlayers) }
            call.respond(gameList)
        }

        get("/api/games/{name}") {
            val name = call.parameters["name"] ?: return@get call.respondText("game name required", status = HttpStatusCode.BadRequest)
            val game =
                findGameByName(name) ?: return@get call.respondText("game not found", status = HttpStatusCode.NotFound)
            call.respond(mapOf("name" to game.name, "maxPlayers" to game.maxPlayers))
        }

        post("/api/games/{name}/start") {
            val name = call.parameters["name"] ?: return@post call.respondText("game name required", status = HttpStatusCode.BadRequest)
            val game = findGameByName(name)
            if (game == null) return@post call.respondText("game not found", status = HttpStatusCode.NotFound)
            // placeholder for real game-start logic
            call.respondText("game started: ${game.name}", status = HttpStatusCode.OK)
        }

        get("/api/auth/me") {
            val token = call.request.cookies["AUTH_TOKEN"] ?: return@get call.respondText(
                "not authenticated",
                status = HttpStatusCode.Unauthorized
            )
            val username = getUsernameByToken(token) ?: return@get call.respondText(
                "not authenticated",
                status = HttpStatusCode.Unauthorized
            )
            val email = getUserEmail(username) ?: return@get call.respondText(
                "user not found",
                status = HttpStatusCode.NotFound
            )
            call.respond(mapOf("username" to username, "email" to email))
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
