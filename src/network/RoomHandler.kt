package com.example.network

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

// Populate these to add game!
val GAME_HANDLERS = {
}

object RoomHandler {
    // Socket handler class must contain list of rooms
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap<String, Room>()

    fun createObjectByName(name: String?): Game = Class.forName("com.example." + name?.lowercase()?.capitalize()).newInstance() as Game

    // Socket must contain handling code
    public suspend fun handle(session: DefaultWebSocketServerSession) {
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

                // Must be handled outside of when statement as
                if (type?.contains("START") == true) {
                    val r = room ?: continue
                    val p = player ?: continue
                    // Ignore if not not host, max capacity, or game started
                    r.started = true

                    // Obtain game string from command
                    val gameString = type?.substring("START_".length)

                    val game =
                        createObjectByName(gameString) // This function must be unique to games as it instantiates its own game type

                    if (p.playerIndex != 0 || r.players.size < game.maxPlayers || r.started) {
                        continue
                    } else {
                        game.addPlayer()
                        game.addPlayer() // Add two players
                        game.startGame()
                        r.game = game
                        r.players.forEachIndexed { i, pl ->
                            pl.session.send(game.buildState("START", game, i))
                        }
                    }
                }

                when (type) {
                    "CREATE" -> {
                        val (p, r) = createGame(session)
                        player = p
                        room = r
                    }

                    "JOIN" -> {
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

//    // Socket must contain code to build the state
//    fun buildState(
//        type: String,
//        game: Game,
//        playerIndex: Int,
//        askSuccess: Boolean? = null,
//    ): String {
//        // askSuccess only required in GoFish
//        val state = game.getState(playerIndex)
//        return buildString {
//            append("""{"type":"$type"""")
//            append(""","board":"${state["board"]}"""")
//            append(""","turn":${state["turn"]}""")
//            append(""","gameOver":${state["gameOver"]}""")
//            append(""","winner":${state["winner"] ?: "null"}""")
//            append(""","playerIndex":$playerIndex}""")
//        }
//    }

    // Generate room ID
    fun generateRoomId(): String = (1000..9999).random().toString()

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

            r.players.size >= 2 -> {
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
        return player to room // Otherwise, return normal player and room. Seems like the only way to do this
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
