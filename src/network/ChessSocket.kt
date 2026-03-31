// AI-assisted: WebSocket handler structure and JSON serialisation (Gemini)
// Manages Chess lobby rooms and relays moves between two players via WebSocket
package com.example.network

import com.example.games.Chess
import com.example.games.Game
//import io.ktor.network.sockets.Socket
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
object ChessSocket : SocketHandler() {
    /**
     * Entry point for all WebSocket connections to /chess.
     * Loops over incoming frames and dispatches to the appropriate handler.
     */
    override suspend fun handle(session: DefaultWebSocketServerSession) { // There is an instance of this code running for each client
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
                        val (p, r) = createGame(session)
                        player = p
                        room = r
                    }

                    "JOIN" -> {
                        val (p, r) = joinGame(session, msg, player, room)
                        player = p
                        room = r
                    }

                    "START" -> {
                        val r = room ?: continue
                        val p = player ?: continue
                        if (p.playerIndex != 0 || r.players.size < 2 || r.started) continue
                        r.started = true
                        val game = Chess() // This function must be unique to games as it instantiates its own game type
                        game.addPlayer(); game.addPlayer() // Add two players
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

                        // Game is abstract so check if it's an instance of chess
                        if (g !is Chess) throw GameException()

                        if (g.currentPlayer() != p.playerIndex) continue
                        val moves = g.legalMovesFrom(from)
                        session.send("""{"type":"LEGAL_MOVES","from":$from,"moves":$moves}""")
                    }

                    "MOVE" -> {
                        val r = room ?: continue
                        val g = r.game ?: continue
                        val p = player ?: continue

                        // as before...
                        if (g !is Chess) throw GameException()

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
            cleanUpRoom(room, player)
        }
    }

    /**
     * Builds a JSON state message from the [game] state for player [playerIndex].
     * @param type The "type" field value (e.g. "START" or "STATE").
     */
    override fun buildState(type: String, game: Game, playerIndex: Int, askSuccess: Boolean?): String {
        // askSuccess only required in GoFish
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

}
