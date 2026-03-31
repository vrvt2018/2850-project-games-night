package com.example.network

import com.example.games.Game
import com.example.network.GoFishSocket.rooms
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
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

abstract class Socket {
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
    abstract suspend fun handle(session : DefaultWebSocketServerSession)

    // Socket must contain code to build the state
    abstract fun buildState(type: String, game: Game, playerIndex: Int, askSuccess: Boolean? = null): String

    // Generate room ID
    protected fun generateRoomId(): String = (1000..9999).random().toString()
}
