// AI-assisted: WebSocket handler structure and JSON serialisation (Gemini)
// Manages Chess lobby rooms and relays moves between two players via WebSocket
package com.example.network

import com.example.games.Chess
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.serialization.json.*

// This is obviously AI-generated.

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
object ChessHandler : GameSocketHandler() {
    /**
     * Entry point for all WebSocket connections to /chess.
     * Loops over incoming frames and dispatches to the appropriate handler.
     */

    override suspend fun handle(
        msg: JsonObject,
        type: String?,
        session: DefaultWebSocketServerSession,
        player: NetworkPlayer?,
        room: Room?,
    ) {
        // To speed things up, these are asserted not-null here
        // They are required for all functions in this
        val r = room ?: return
        val g = r.game as? Chess
        val p = player ?: return
        when (type) {
            "CHESS_MOVES" -> {
                val from = msg["from"]?.jsonPrimitive?.intOrNull ?: return

                if (g?.currentPlayer() != p.playerIndex) return
                val moves = g.legalMovesFrom(from)
                session.send("""{"type":"LEGAL_MOVES","from":$from,"moves":$moves}""")
            }

            "CHESS_MOVE" -> {
                if (g?.currentPlayer() != p.playerIndex) return
                val from = msg["from"]?.jsonPrimitive?.intOrNull ?: return
                val to = msg["to"]?.jsonPrimitive?.intOrNull ?: return

                if (!g.makeMove(from, to)) {
                    session.send("""{"type":"MOVE_INVALID"}""")
                    return
                }

                if (g.isGameOver()) {
                    val winner = g.getWinner()
                    broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"capture"}""")
                } else {
                    r.players.forEachIndexed { i, pl ->
                        pl.session.send(r.game!!.buildState("STATE", g, i))
                    }
                }
            }

            "CHESS_RESIGN" -> {
                val winner = 1 - p.playerIndex
                broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"resignation"}""")
            }
        }
    }
}
