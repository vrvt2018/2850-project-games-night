package com.example.network

import com.example.games.Chess
import com.example.games.Game
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RoomHandler {
    // Socket handler class must contain list of rooms
    // Concurrent so that multiple clients over multiple sockets can access it at once
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap<String, Room>()

    // fun createObjectByName(name: String?): Game = Class.forName("com.example." + name?.lowercase()?.capitalize()).newInstance() as Game

    fun createGameByName(name: String?): Game? =
        when (name?.lowercase()) {
            "chess" -> {
                Chess()
            }

            else -> {
                println(name?.lowercase())
                throw Exception("Game does not exist!")
            }
        }

    fun getGameStringFromType(type: String): String = type.split('_')[1]

    // Handling code - whenever a socket is created it's directed to this subroutine
    suspend fun handle(session: DefaultWebSocketServerSession) {
        var player: NetworkPlayer? = null // the player in this session
        var room: Room? = null // the room player is in

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val msg =
                    runCatching {
                        Json.parseToJsonElement(frame.readText()).jsonObject
                    }.getOrNull() ?: continue
                val type = msg["type"]?.jsonPrimitive?.content

                println(msg)

                // Must be handled outside when statement as requires contains function
                if (type?.contains("START") == true) {
                    //println("Starting...")
                    val r = room ?: continue
                    val p = player ?: continue

                    // The game cannot start if not host, not enough players, or already started
                    if (p.playerIndex == 0 && r.players.size >= r.game!!.minPlayers && !r.started) {
                        r.started = true
                        // _ -> is required because otherwise the "it" variable is unused and ktlint doesn't like that
                        r.players.forEach { _ -> r.game!!.addPlayer() }
                        r.game!!.startGame()

                        r.players.forEachIndexed { i, pl ->
                            // Part of protocol - START type starts game
                            // Should probably move this function somewhere IDK where tho, it is from when it was all hardcoded
                            pl.session.send(r.game!!.buildState("START", r.game!!, i))
                        }
                    }
                } else if (type?.contains("CREATE") == true) {
                    val game = // Instantiate game depending on who called
                        createGameByName(getGameStringFromType(type)) as Game

                    val (p, r) = createRoom(session, game)
                    player = p
                    room = r
                } else if (type == "JOIN") { // Player joins a room
                    val (p, r) = joinGame(session, msg, player, room)
                    player = p
                    room = r
                } else { // Not a base command -> outsource to respective handle function
                    when (room?.game?.name) // each game has a respective handler
                    {
                        "Chess" -> ChessHandler.handle(msg, type, session, player, room)
                        // "GoFish" -> GoFishHandler.handle(session,player,room)
                    }
                }
            }
        } finally {
            cleanUpRoom(room, player)
        }
    }

    // Generate room ID between 0000 and 9999 inclusive
    fun generateRoomId(): String = (0..9999).random().toString().padStart(4, '0')

    // Functions createGame, joinGame and cleanUpRoom are for use in respective game sockets
    // For use upon receiving "CREATE"
    suspend fun createRoom(
        session: DefaultWebSocketServerSession,
        game: Game,
    ): Pair<NetworkPlayer, Room> {
        val roomId = generateRoomId()
        val p = NetworkPlayer(UUID.randomUUID().toString(), session, 0) // Always 0 == host
        val r = Room(roomId)
        r.game = game // Set room game to the game
        r.players.add(p)
        rooms[roomId] = r
        session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")
        return p to r
    }

    // For use upon receiving "JOIN" - Handle joining game
    suspend fun joinGame(
        session: DefaultWebSocketServerSession,
        msg: JsonObject,
        player: NetworkPlayer?,
        room: Room?,
    ): Pair<NetworkPlayer?, Room?> {
        val roomId = msg["roomId"]!!.jsonPrimitive.content // Exit early if null
        val r = rooms[roomId]
        when {
            // Join fail conditions
            r == null -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
            }

            r.started -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Game already in progress"}""")
            }

            // I used AI on this line. I couldn't figure out why it would fail at all cases of the when
            // It was because game was null and not created at this point, so failed the null assertion
            r.players.size >= r.game!!.maxPlayers -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Room is full"}""")
            }

            else -> {
                val p = NetworkPlayer(UUID.randomUUID().toString(), session, 1)
                r.players.add(p)
                session.send("""{"type":"JOIN_OK","playerIndex":1}""")
                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
                return p to r
            }
        }
        // Otherwise, return normal player and room. Seems like the only way to do this when statement
        // You should never actually reach here!
        return player to room
    }

    // Remove all players from room -> to be used in finally{} statement in match statement
    suspend fun cleanUpRoom(
        room: Room?,
        player: NetworkPlayer?,
    ) {
        room?.let { r ->
            r.players.removeIf { it.id == player!!.id }
            if (r.players.isEmpty()) {
                rooms.remove(r.id)
            } else {
                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
            }
        }
    }
}
