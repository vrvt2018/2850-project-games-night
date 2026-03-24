package com.example

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.pebble.respondTemplate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        staticResources("/resources", "/static")

        // ──────────────────────────────────────────────────────────────────
        // Pages (Templates)
        // ──────────────────────────────────────────────────────────────────

        get("/") {
            val token = call.request.cookies["AUTH_TOKEN"]
            if (token != null && getUsernameByToken(token) != null) {
                return@get call.respondRedirect("/games")
            }
            val error = call.request.queryParameters["error"]
            val model = mutableMapOf<String, Any>("title" to "Login - Games Night")
            if (!error.isNullOrBlank()) {
                model["error"] = error
            }
            call.respondTemplate("login.peb", model = model)
        }

        get("/signup") {
            val token = call.request.cookies["AUTH_TOKEN"]
            if (token != null && getUsernameByToken(token) != null) {
                return@get call.respondRedirect("/games")
            }
            val error = call.request.queryParameters["error"]
            val model = mutableMapOf<String, Any>("title" to "Sign up - Games Night")
            if (!error.isNullOrBlank()) model["error"] = error
            call.respondTemplate("signup.peb", model = model)
        }

        get("/games") {
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) } ?: return@get call.respondRedirect("/")
            val gamesInfo = findAllGames().map { mapOf("name" to it.name, "maxPlayers" to it.maxPlayers) }
            val model = mapOf(
                "title" to "Games Catalogue",
                "games" to gamesInfo,
                "hasGames" to gamesInfo.isNotEmpty(),
                "user" to user
            )
            call.respondTemplate("game.peb", model = model)
        }

        get("/leaderboard") {
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) } ?: return@get call.respondRedirect("/")
            val stats = getLeaderboard()
            call.respondTemplate("leaderboard.peb", mapOf("title" to "Leaderboard", "user" to user, "stats" to stats))
        }

        // Dedicated game pages
        get("/games/gofish") {
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) } ?: return@get call.respondRedirect("/")
            call.respondTemplate("gofish.peb", mapOf("title" to "Go Fish Lobby", "user" to user))
        }

        get("/games/chess") {
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) } ?: return@get call.respondRedirect("/")
            call.respondTemplate("chess.peb", mapOf("title" to "Chess Lobby", "user" to user))
        }

        // Catch-all game handler for generic game page rendering
        get("/games/{name}") {
            val name = call.parameters["name"] ?: return@get call.respondText("game name required", status = HttpStatusCode.BadRequest)
            val game = findGameByName(name) ?: return@get call.respondText("game not found", status = HttpStatusCode.NotFound)
            val user = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) }
            val model = mutableMapOf<String, Any>(
                "title" to game.name,
                "game" to mapOf("name" to game.name, "maxPlayers" to game.maxPlayers),
                "hasGames" to false
            )
            if (user != null) model["user"] = user
            call.respondTemplate("game.peb", model = model)
        }

        // ──────────────────────────────────────────────────────────────────
        // Form submissions (Auth)
        // ──────────────────────────────────────────────────────────────────

        post("/signup") {
            val formParams = call.receiveParameters()
            val username = formParams["username"]?.trim()
            val email = formParams["email"]?.trim()
            val password = formParams["password"]
            val passwordConfirm = formParams["password_confirm"]

            if (username.isNullOrEmpty() || email.isNullOrEmpty() || password.isNullOrEmpty() || passwordConfirm.isNullOrEmpty()) {
                return@post call.respondRedirect("/signup?error=all_fields_required")
            }
            if (password != passwordConfirm) {
                return@post call.respondRedirect("/signup?error=password_mismatch")
            }
            if (!isValidUsername(username)) {
                return@post call.respondRedirect("/signup?error=invalid_username")
            }
            if (!isValidEmail(email)) {
                return@post call.respondRedirect("/signup?error=invalid_email")
            }
            if (!isValidPassword(password)) {
                return@post call.respondRedirect("/signup?error=weak_password")
            }

            val success = createUser(username, email, hashPassword(password))
            if (!success) {
                return@post call.respondRedirect("/signup?error=user_exists")
            }

            // Auto-login after signup
            val token = createSession(username)
            call.response.cookies.append(
                Cookie("AUTH_TOKEN", token, maxAge = 3600, httpOnly = true, secure = false) // secure=false for localhost
            )
            call.respondRedirect("/games")
        }

        post("/login") {
            val formParams = call.receiveParameters()
            val username = formParams["username"]?.trim() ?: return@post call.respondRedirect("/?error=invalid_credentials")
            val password = formParams["password"] ?: return@post call.respondRedirect("/?error=invalid_credentials")

            val user = findUserByCredentials(username, hashPassword(password))
            if (user == null) {
                return@post call.respondRedirect("/?error=invalid_credentials")
            }

            val token = createSession(username)
            call.response.cookies.append(
                Cookie("AUTH_TOKEN", token, maxAge = 3600, httpOnly = true, secure = false) // secure=false for localhost
            )
            call.respondRedirect("/games")
        }

        get("/logout") {
            call.request.cookies["AUTH_TOKEN"]?.let { deleteSession(it) }
            call.response.cookies.append(io.ktor.http.Cookie("AUTH_TOKEN", "", maxAge = 0, path = "/"))
            call.respondRedirect("/")
        }

        // ──────────────────────────────────────────────────────────────────
        // API Endpoints
        // ──────────────────────────────────────────────────────────────────

        get("/api/auth/me") {
            val username = call.request.cookies["AUTH_TOKEN"]?.let { getUsernameByToken(it) } ?: return@get call.respondText("not authenticated", status = HttpStatusCode.Unauthorized)
            val email = getUserEmail(username) ?: "unknown"
            call.respond(mapOf("username" to username, "email" to email))
        }

        get("/api/games") {
            val gameList = findAllGames().map { mapOf("name" to it.name, "maxPlayers" to it.maxPlayers) }
            call.respond(gameList)
        }

        post("/api/games/{name}/start") {
            val name = call.parameters["name"] ?: return@post call.respondText("game name required", status = HttpStatusCode.BadRequest)
            val game = findGameByName(name) ?: return@post call.respondText("game not found", status = HttpStatusCode.NotFound)
            // Redirect to the dedicated lobby page based on game name
            val canonicalName = game.name.replace(" ", "").lowercase()
            call.respondRedirect("/games/$canonicalName")
        }
        
        post("/api/history") {
            // Internal endpoint used by games to post result (in real app, use S2S or direct DB call)
            // For now, WebSocket servers will directly call recordGameResult() from Database.kt
            call.respondText("Use WebSocket direct DB access", status = HttpStatusCode.Forbidden)
        }
    }
}
