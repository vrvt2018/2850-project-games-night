package com.example.network

import com.example.games.Chess
import com.example.games.GoFish
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
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap()

    fun createGameByName(name: String?): Game? =
        when (name?.lowercase()) {
            "chess" -> Chess()
            "go fish", "gofish" -> GoFish()
            else -> {
                println(name?.lowercase())
                throw Exception("Game does not exist!")
            }
        }

    fun getGameStringFromType(type: String): String = type.split('_')[1]

    suspend fun handle(session: DefaultWebSocketServerSession) {
        var player: NetworkPlayer? = null
        var room: Room? = null

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val msg =
                    runCatching {
                        Json.parseToJsonElement(frame.readText()).jsonObject
                    }.getOrNull() ?: continue
                val type = msg["type"]?.jsonPrimitive?.content

                println(msg)

                if (type?.contains("START") == true) {
                    val r = room ?: continue
                    val p = player ?: continue

                    if (p.playerIndex == 0 && r.players.size >= r.game!!.minPlayers && !r.started) {
                        r.started = true
                        r.players.forEach { _ -> r.game!!.addPlayer() }
                        r.game!!.startGame()

                        r.players.forEachIndexed { i, pl ->
                            pl.session.send(r.game!!.buildState("START", r.game!!, i))
                        }
                    }
                } else if (type?.contains("CREATE") == true) {
                    val game = createGameByName(getGameStringFromType(type)) as Game
                    val (p, r) = createRoom(session, game)
                    player = p
                    room = r
                } else if (type == "JOIN") {
                    val (p, r) = joinGame(session, msg, player, room)
                    player = p
                    room = r
                } else {
                    when (room?.game?.name) {
                        "Chess" -> ChessHandler.handle(msg, type, session, player, room)
                        "Go Fish" -> GoFishHandler.handle(msg, type, session, player, room)
                    }
                }
            }
        } finally {
            cleanUpRoom(room, player)
        }
    }

    fun generateRoomId(): String = (0..9999).random().toString().padStart(4, '0')

    suspend fun createRoom(
        session: DefaultWebSocketServerSession,
        game: Game,
    ): Pair<NetworkPlayer, Room> {
        val roomId = generateRoomId()
        val p = NetworkPlayer(UUID.randomUUID().toString(), session, 0)
        val r = Room(roomId)
        r.game = game
        r.players.add(p)
        rooms[roomId] = r
        session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")
        return p to r
    }

    suspend fun joinGame(
        session: DefaultWebSocketServerSession,
        msg: JsonObject,
        player: NetworkPlayer?,
        room: Room?,
    ): Pair<NetworkPlayer?, Room?> {
        val roomId = msg["roomId"]!!.jsonPrimitive.content
        val r = rooms[roomId]
        when {
            r == null -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
            }
            r.started -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Game already in progress"}""")
            }
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
        return player to room
    }

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