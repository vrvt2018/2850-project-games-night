// AI-assisted: WebSocket handler structure and JSON serialisation (Gemini)
// Manages Chess lobby rooms and relays moves between two players via WebSocket
package com.example.network

import com.example.games.Chess
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket handler for the Chess game.
 *
 * Protocol (JSON messages from client → server):
 *   { "type": "CREATE" }                             — Create a new room (you become the host/White)
 *   { "type": "JOIN", "roomId": "XXXX" }             — Join an existing room (you become Black)
 *   { "type": "START" }                              — Host starts the game (requires 2 players)
 *   { "type": "MOVES", "from": 12 }                 — Request legal moves for piece at index 12
 *   { "type": "MOVE", "from": 12, "to": 28 }        — Attempt to move piece from index 12 to 28
 *   { "type": "RESIGN" }                             — Current player resigns
 *
 * Protocol (JSON messages server → client):
 *   { "type": "ROOM_CREATED", "roomId": "XXXX", "playerIndex": 0 }
 *   { "type": "JOIN_OK", "playerIndex": 1 }
 *   { "type": "JOIN_FAIL", "reason": "..." }
 *   { "type": "PLAYER_UPDATE", "count": 2 }
 *   { "type": "START", ...getState() fields... }
 *   { "type": "LEGAL_MOVES", "from": 12, "moves": [28, 36, ...] }
 *   { "type": "STATE", ...getState() fields... }
 *   { "type": "MOVE_INVALID" }
 *   { "type": "GAME_END", "winner": 0, "reason": "capture" }
 */
object ChessSocket {

    /** A connected player in a room. */
    data class Player(
        val id: String,
        val session: DefaultWebSocketServerSession,
        val playerIndex: Int   // 0 = White, 1 = Black
    )

    /** A room holding up to 2 players and a Chess game instance. */
    data class Room(
        val id: String,
        val players: MutableList<Player> = mutableListOf(),
        var game: Chess? = null,
        var started: Boolean = false
    )

    /** Thread-safe room registry. */
    private val rooms = ConcurrentHashMap<String, Room>()

    /**
     * Entry point for all WebSocket connections to /chess.
     * Loops over incoming frames and dispatches to the appropriate handler.
     */
    suspend fun handle(session: DefaultWebSocketServerSession) {
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
                            r.players.size >= 2 -> session.send("""{"type":"JOIN_FAIL","reason":"Room is full"}""")
                            else -> {
                                val p = Player(UUID.randomUUID().toString(), session, 1)
                                r.players.add(p)
                                player = p
                                room = r
                                session.send("""{"type":"JOIN_OK","playerIndex":1}""")
                                broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
                            }
                        }
                    }

                    "START" -> {
                        val r = room ?: continue
                        val p = player ?: continue
                        if (p.playerIndex != 0 || r.players.size < 2 || r.started) continue
                        r.started = true
                        val game = Chess()
                        game.addPlayer(); game.addPlayer()
                        game.startGame()
                        r.game = game
                        r.players.forEachIndexed { i, pl ->
                            pl.session.send(buildState("START", game, i))
                        }
                    }

                    "MOVES" -> {
                        val r = room ?: continue
                        val g = r.game ?: continue
                        val p = player ?: continue
                        val from = msg["from"]?.jsonPrimitive?.intOrNull ?: continue
                        if (g.currentPlayer() != p.playerIndex) continue
                        val moves = g.legalMovesFrom(from)
                        session.send("""{"type":"LEGAL_MOVES","from":$from,"moves":${moves}}""")
                    }

                    "MOVE" -> {
                        val r = room ?: continue
                        val g = r.game ?: continue
                        val p = player ?: continue
                        if (g.currentPlayer() != p.playerIndex) continue
                        val from = msg["from"]?.jsonPrimitive?.intOrNull ?: continue
                        val to = msg["to"]?.jsonPrimitive?.intOrNull ?: continue

                        if (!g.makeMove(from, to)) {
                            session.send("""{"type":"MOVE_INVALID"}""")
                            continue
                        }

                        if (g.isGameOver()) {
                            val winner = g.getWinner()
                            broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"capture"}""")
                        } else {
                            r.players.forEachIndexed { i, pl ->
                                pl.session.send(buildState("STATE", g, i))
                            }
                        }
                    }

                    "RESIGN" -> {
                        val r = room ?: continue
                        val p = player ?: continue
                        val winner = 1 - p.playerIndex
                        broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"resignation"}""")
                    }
                }
            }
        } finally {
            // Clean up the room when a player disconnects
            room?.let { r ->
                r.players.removeIf { it.id == player?.id }
                if (r.players.isEmpty()) rooms.remove(r.id)
                else broadcast(r, """{"type":"PLAYER_UPDATE","count":${r.players.size}}""")
            }
        }
    }

    /**
     * Broadcasts [msg] to every player in [room].
     */
    private suspend fun broadcast(room: Room, msg: String) {
        room.players.forEach { it.session.send(msg) }
    }

    /**
     * Builds a JSON state message from the [game] state for player [playerIndex].
     * @param type The "type" field value (e.g. "START" or "STATE").
     */
    private fun buildState(type: String, game: Chess, playerIndex: Int): String {
        val state = game.getState(playerIndex)
        return buildString {
            append("""{"type":"$type"""")
            append(""","board":"${state["board"]}"""")
            append(""","turn":${state["turn"]}""")
            append(""","gameOver":${state["gameOver"]}""")
            append(""","winner":${state["winner"] ?: "null"}""")
            append(""","playerIndex":$playerIndex}""")
        }
    }

    /** Generates a 4-digit random room ID. */
    private fun generateRoomId(): String = (1000..9999).random().toString()
}
