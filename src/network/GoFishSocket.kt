// AI-assisted: WebSocket handler structure and JSON message protocol (Gemini)
// Manages Go Fish lobby rooms and relays card asks between 2-4 players via WebSocket
package com.example.network

import com.example.games.Game
import com.example.games.GoFish
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import com.example.network.SocketHandler.*

/**
 * WebSocket handler for Go Fish.
 *
 * Protocol (client -> server):
 *   { "type": "CREATE" }
 *   { "type": "JOIN", "roomId": "XXXX" }
 *   { "type": "START" }
 *   { "type": "ASK", "target": 1, "rank": "A" }
 *   { "type": "END_TURN" }
 *
 * Protocol (server -> client):
 *   { "type": "ROOM_CREATED", "roomId": "XXXX", "playerIndex": 0 }
 *   { "type": "JOIN_OK", "playerIndex": N }
 *   { "type": "JOIN_FAIL", "reason": "..." }
 *   { "type": "PLAYER_UPDATE", "count": N }
 *   { "type": "START", ...state... }
 *   { "type": "ASK_RESULT", "success": true/false, ...state... }
 *   { "type": "STATE", ...state... }
 *   { "type": "GAME_END", "winner": N }
 */
object GoFishSocket : SocketHandler() {
    override suspend fun handle(session: DefaultWebSocketServerSession) {
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
                        val (p,r) = createGame(session)
                        player = p
                        room = r
                    }

                    "JOIN" -> {
                        val(p,r) = joinGame(session, msg, player, room)
                        player = p
                        room = r
                    }

                    "START" -> {
                        val r = room ?: continue
                        val p = player ?: continue
                        if (p.playerIndex != 0 || r.players.size < 2 || r.started) continue
                        r.started = true
                        val game = GoFish()
                        r.players.forEach { game.addPlayer() }
                        game.startGame()
                        r.game = game
                        r.players.forEach { pl ->
                            pl.session.send(buildState("START", game, pl.playerIndex))
                        }
                    }

                    "ASK" -> {
                        val r = room ?: continue
                        val g = r.game ?: continue
                        val p = player ?: continue

                        // Ensure that Game is of type GoFish - important as Game is abstract
                        if (g !is GoFish)
                            throw Exception("Game error!")


                        if (g.currentPlayer() != p.playerIndex) continue
                        val target = msg["target"]?.jsonPrimitive?.intOrNull ?: continue
                        val rank = msg["rank"]?.jsonPrimitive?.content ?: continue

                        val success = g.askForCard(target, rank)

                        if (g.isGameOver()) {
                            broadcast(r, """{"type":"GAME_END","winner":${g.getWinner()}}""")
                        } else {
                            // Send ASK_RESULT with updated state to all players
                            r.players.forEach { pl ->
                                pl.session.send(buildState("ASK_RESULT", g, pl.playerIndex, success))
                            }
                        }
                    }

                    "END_TURN" -> {
                        val r = room ?: continue
                        val g = r.game ?: continue
                        val p = player ?: continue

                        if (g !is GoFish) throw Exception("Game error!")

                        if (g.currentPlayer() != p.playerIndex) continue

                        g.endTurn()

                        if (g.isGameOver()) {
                            broadcast(r, """{"type":"GAME_END","winner":${g.getWinner()}}""")
                        } else {
                            r.players.forEach { pl ->
                                pl.session.send(buildState("STATE", g, pl.playerIndex))
                            }
                        }
                    }
                }
            }
        }
        finally {cleanUpRoom(room, player)}
    }

    override fun buildState(type: String, game: Game, playerIndex: Int, askSuccess: Boolean?): String {
        val state = game.getState(playerIndex)
        val sb = StringBuilder()
        sb.append("""{"type":"$type"""")
        if (askSuccess != null) sb.append(""","success":$askSuccess""")
        sb.append(""","turn":${state["turn"]}""")
        sb.append(""","deckSize":${state["deckSize"]}""")
        sb.append(""","numPlayers":${state["numPlayers"]}""")
        sb.append(""","playerIndex":$playerIndex""")
        sb.append(""","gameOver":${state["gameOver"]}""")
        sb.append(""","winner":${state["winner"] ?: "null"}""")

        // Books array
        val books = state["books"] as List<*>
        sb.append(""","books":[${books.joinToString(",")}]""")

        // Hand sizes array
        val handSizes = state["handSizes"] as List<*>
        sb.append(""","handSizes":[${handSizes.joinToString(",")}]""")

        // My hand (image URLs)
        val myHand = state["myHand"] as List<*>
        sb.append(""","myHand":[${myHand.joinToString(",") { "\"$it\"" }}]""")

        // My hand ranks
        val myHandRanks = state["myHandRanks"] as List<*>
        sb.append(""","myHandRanks":[${myHandRanks.joinToString(",") { "\"$it\"" }}]""")

        sb.append("}")
        return sb.toString()
    }
}
