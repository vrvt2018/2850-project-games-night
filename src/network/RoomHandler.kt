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

data class NetworkPlayer(
    val id: String,
    val session: DefaultWebSocketServerSession,
    val playerIndex: Int,
)

data class Room(
    val id: String,
    val players: MutableList<NetworkPlayer> = mutableListOf(),
    var game: Game? = null, // Generic type - originally was specific per game class
    var started: Boolean = false,
)

class GameException : Exception("Game error!") // Thrown when game not recognised - required to ensure that game is correct
// important because game is abstract and IntelliJ is shouting at me!

object RoomHandler {
    // Socket handler class must contain list of rooms
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap<String, Room>()

    // fun createObjectByName(name: String?): Game = Class.forName("com.example." + name?.lowercase()?.capitalize()).newInstance() as Game

    fun createGameByName(name: String?): Game? =
        when (name?.lowercase()) {
            "chess" -> {
                Chess()
            }

            else -> {
                null
            }
        }

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

                // Must be handled outside when statement as requires contains function
                if (type?.contains("START") == true) { // TODO: This doesn't work so add print statement printing after start and stuff
                    val r = room ?: continue
                    val p = player ?: continue
                    // Ignore if not host, max capacity, or game started

                    // Obtain game string from command
                    val gameString = type.substring("START_".length)

                    // Create instance of game
                    val game =
                        createGameByName(gameString) ?: {
                            throw Exception("Game does not exist!")
                        } as Game


                    // The game cannot start if not host, not enough players, or already started
                    if (p.playerIndex == 0 && r.players.size > game.minPlayers && !r.started) {
                        r.started = true

                        // I'm not entirely sure why ktlint flags this without "_ ->"
                        r.players.forEach { _ -> game.addPlayer() }
                        game.startGame()
                        r.game = game
                        r.players.forEachIndexed { i, pl ->
                            // Part of protocol - START type starts game
                            pl.session.send(game.buildState("START", game, i))
                        }
                    }
                }

                when (type) {
                    "CREATE" -> { // Create new room
                        val (p, r) = createGame(session)
                        player = p
                        room = r
                    }

                    "JOIN" -> { // Player joins a room
                        val (p, r) = joinGame(session, msg, player, room)
                        player = p
                        room = r
                    }

                    else -> { // Not a base command -> outsource to respective handle function
                        when (room?.game?.name) // each game has a respective handler
                        {
                            "Chess" -> ChessHandler.handle(msg, type, session, player, room)
                            // "GoFish" -> GoFishHandler.handle(session,player,room)
                        }
                    }
                }
            }
        } finally {
            cleanUpRoom(room, player)
        }
    }

    // Generate room ID between 0000 and 9999 inclusive
    fun generateRoomId(): String = (0..9999).random().toString().padStart(4, '0')

    // Send message to all clients in room over respective websocket

    // Functions createGame, joinGame and cleanUpRoom are for use in respective game sockets
    // For use upon receiving "CREATE"
    suspend fun createGame(session: DefaultWebSocketServerSession): Pair<NetworkPlayer, Room> {
        val roomId = generateRoomId()
        val p = NetworkPlayer(UUID.randomUUID().toString(), session, 0)
        val r = Room(roomId)
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
        val roomId = msg["roomId"]?.jsonPrimitive?.content ?: return player to room
        val r = rooms[roomId]
        when {
            r == null -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
            }

            r.started -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Game already in progress"}""")
            }

            r.players.size >= 2 -> { // Currently hardcoded for 2 players urgh
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
        return player to room // Otherwise, return normal player and room. Seems like the only way to do this when statement
    }

    // Remove all players from room -> to be used in finally{} statement in match statement
    suspend fun cleanUpRoom(
        room: Room?,
        player: NetworkPlayer?,
    ) {
        room?.let { r ->
            r.players.removeIf { it.id == player?.id }
            if (r.players.isEmpty()) {
                rooms.remove(r.id)
            } else {
                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
            }
        }
    }
}

// Needs to be outside so other handlers can use it.
// There might be a better more object-oriented way to do this
suspend fun broadcast(
    room: Room,
    msg: String,
) = room.players.forEach { it.session.send(msg) }
