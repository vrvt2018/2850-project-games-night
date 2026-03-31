package com.example.network

import com.example.games.Game
import com.example.games.GoFish
import com.example.network.GoFishSocket.broadcast
import com.example.network.GoFishSocket.rooms
import io.ktor.network.sockets.Socket
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class Player(
    val id: String,
    val session: DefaultWebSocketServerSession,
    val playerIndex: Int
)

data class Room(
    val id: String,
    val players: MutableList<Player> = mutableListOf(),
    var game: Game? = null, // Generic type - originally was specific per game class
    var started: Boolean = false
)

class GameException : Exception("Game error!") // Thrown when game not recognised - required to ensure that game is correct
                                               // important because game is abstract and IntelliJ is shouting at me!

abstract class SocketHandler {
    // Socket handler class must contain list of rooms
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap<String, Room>()

    // Generic room creation function
    suspend fun generateRoom(session: DefaultWebSocketServerSession): Pair<Player, Room> {
        val roomId = generateRoomId()
        val p = Player(UUID.randomUUID().toString(), session, 0)
        val r = Room(roomId)
        r.players.add(p)
        rooms[roomId] = r
        session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")

        return Pair(p, r)
    }

    // Socket must contain handling code
    //abstract suspend fun handle(session : DefaultWebSocketServerSession)

    // Socket must contain code to build the state
    abstract fun buildState(type: String, game: Game, playerIndex: Int, askSuccess: Boolean? = null): String

    // Generate room ID
    protected fun generateRoomId(): String = (1000..9999).random().toString()

    // Send to all clients in room
    protected suspend fun broadcast(room: Room, msg: String) = room.players.forEach { it.session.send(msg) }
    suspend fun createGame(session: DefaultWebSocketServerSession) : Pair<Player, Room>
    {
        val roomId = generateRoomId()
        val p = Player(UUID.randomUUID().toString(), session, 0)
        val r = Room(roomId)
        r.players.add(p)
        rooms[roomId] = r
        session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")
        return p to r
    }

    suspend fun joinGame(session: DefaultWebSocketServerSession, msg: JsonObject, player: Player?, room: Room?) : Pair<Player?, Room?> {
        val roomId = msg["roomId"]?.jsonPrimitive?.content ?: return player to room
        val r = rooms[roomId]
        when {
            r == null -> session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
            r.started -> session.send("""{"type":"JOIN_FAIL","reason":"Game already in progress"}""")
            r.players.size >= 2 -> session.send("""{"type":"JOIN_FAIL","reason":"Room is full"}""")
            else -> {
                val p = Player(UUID.randomUUID().toString(), session, 1)
                r.players.add(p)
                session.send("""{"type":"JOIN_OK","playerIndex":1}""")
                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
                return p to r
            }
        }
        return player to room // Otherwise, return normal player and room
    }

    suspend fun cleanUpRoom(room: Room?, player: Player?)
    {
        room?.let { r ->
            r.players.removeIf { it.id == player?.id }
            if (r.players.isEmpty()) rooms.remove(r.id)
            else broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
        }
    }

    // Template handle code - this is a test
    protected open suspend fun handle(session: DefaultWebSocketServerSession) {
        var player: Player? = null
        var room: Room? = null

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val msg = runCatching {
                    Json.parseToJsonElement(frame.readText()).jsonObject
                }.getOrNull() ?: continue

                when (msg["type"]?.jsonPrimitive?.content) {

                    "CREATE" -> {
                        val roomId = generateRoomId()
                        val p = Player(UUID.randomUUID().toString(), session, 0)
                        val r = Room(roomId)
                        r.players.add(p)
                        rooms[roomId] = r
                        player = p
                        room = r
                        session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")
                    }

                    "JOIN" -> {
                        val roomId = msg["roomId"]?.jsonPrimitive?.content ?: continue
                        val r = rooms[roomId]
                        when {
                            r == null -> session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
                            r.started -> session.send("""{"type":"JOIN_FAIL","reason":"Game already in progress"}""")
                            r.players.size >= 4 -> session.send("""{"type":"JOIN_FAIL","reason":"Room is full"}""")
                            else -> {
                                val idx = r.players.size
                                val p = Player(UUID.randomUUID().toString(), session, idx)
                                r.players.add(p)
                                player = p
                                room = r
                                session.send("""{"type":"JOIN_OK","playerIndex":$idx}""")
                                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
                            }
                        }
                    }
                }
            }
        } finally {
            room?.let { r ->
                r.players.removeIf { it.id == player?.id }
                if (r.players.isEmpty()) rooms.remove(r.id)
                else broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
            }
        }
    }

}
