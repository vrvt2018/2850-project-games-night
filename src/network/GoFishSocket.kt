package com.example.network

import com.example.games.GoFish
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import java.util.*

object GoFishSocket {
    
    data class Player(
        val id: String,
        val session: DefaultWebSocketServerSession
    )

    data class Room(
        val id: String,
        val hostId: String,
        val players: MutableList<Player> = mutableListOf(),
        var game: GoFish? = null,
        var started: Boolean = false
    )

    private val rooms = mutableMapOf<String, Room>()

    suspend fun handle(session: DefaultWebSocketServerSession) {
        var player: Player? = null
        var room: Room? = null
        for (frame in session.incoming) {
            val msg = Json.parseToJsonElement((frame as Frame.Text).readText()).jsonObject
            when (msg["type"]!!.jsonPrimitive.content) {

                "CREATE" -> {
                    val id = generateRoomId()
                    val p = Player(UUID.randomUUID().toString(), session)
                    val r = Room(id, p.id)
                    r.players.add(p)
                    rooms[id] = r
                    player = p
                    room = r
                    session.send("""{"type":"ROOM_CREATED","roomId":"$id","host":true}""")
                }

                "JOIN" -> {
                    val id = msg["roomId"]!!.jsonPrimitive.content
                    val r = rooms[id] ?: continue
                    if (r.players.size >= 4 || r.started) {
                        session.send("""{"type":"JOIN_FAIL"}""")
                        continue
                    }

                    val p = Player(UUID.randomUUID().toString(), session)
                    r.players.add(p)
                    player = p
                    room = r
                    broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
                }

                "START" -> {
                    val r = room ?: continue
                    if (player?.id != r.hostId || r.players.size < 2) continue
                    r.started = true
                    val game = GoFish()
                    repeat(r.players.size) { game.addPlayer() }
                    game.startGame()
                    r.game = game

                    r.players.forEachIndexed { i, p ->
                        val hand = game.getHand(i).map { it.imageUrl() }
                        p.session.send(Json.encodeToString(mapOf(
                            "type" to "START",
                            "cards" to hand,
                            "playerIndex" to i
                        )))
                    }
                }

                "ASK" -> {
                    val r = room ?: continue
                    val g = r.game ?: continue
                    val from = r.players.indexOf(player)
                    val to = msg["target"]!!.jsonPrimitive.int
                    val rank = msg["rank"]!!.jsonPrimitive.content
                    val success = g.ask(from, to, rank)
                    if (g.isGameOver()) {
                        val winner = g.getWinner()
                        broadcast(r, """{"type":"GAME_END","winner":$winner}""")
                        continue
                    }
                    broadcast(r, """{"type":"ASK_RESULT","success":$success}""")
                }
            }
        }
    }

    private suspend fun broadcast(room: Room, msg: String) {
        room.players.forEach { it.session.send(msg) }
    }

    private fun generateRoomId(): String {
        return (1000..9999).random().toString()
    }
}