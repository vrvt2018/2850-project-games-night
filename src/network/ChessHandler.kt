// AI-assisted: WebSocket handler structure and JSON serialisation (Gemini)
// Manages Chess lobby rooms and relays moves between two players via WebSocket
package com.example.network

import com.example.games.Chess
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handler for messages from client relating to chess
 */
object ChessHandler : GameSocketHandler() {
    /**
     * All messages from the client are forwarded to this function from the RoomHandler
     */

    override suspend fun handle(
        msg: JsonObject,
        type: String?,
        session: DefaultWebSocketServerSession,
        player: NetworkPlayer?,
        room: Room?,
    ) {
        // To speed things up, these are asserted not-null here
        // They are required for all functions in this routine
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
                    RoomHandler.markRoomFinished(r)
                    val winner = g.getWinner()
                    broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"capture"}""")
                } else {
                    r.players.forEachIndexed { i, pl ->
                        pl.session.send(r.game!!.buildState("STATE", g, i))
                    }
                    RoomHandler.touchRoom(r)
                }
            }

            "CHESS_RESIGN" -> {
                RoomHandler.markRoomFinished(r)
                val winner = 1 - p.playerIndex
                broadcast(r, """{"type":"GAME_END","winner":$winner,"reason":"resignation"}""")
            }
        }
    }
}
