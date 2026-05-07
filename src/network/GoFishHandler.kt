package com.example.network

import com.example.games.GoFish
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

object GoFishHandler : GameSocketHandler() {
    private object Protocol {
        const val TYPE_ASK = "GOFISH_ASK"
        const val TYPE_END_TURN = "GOFISH_END_TURN"
        const val TYPE_ASK_RESULT = "ASK_RESULT"
        const val TYPE_STATE = "STATE"
        const val TYPE_GAME_END = "GAME_END"
        const val TYPE_ERROR = "ERROR"

        const val REASON_NOT_YOUR_TURN = "Not your turn"
        const val REASON_INVALID_TARGET = "Invalid player target (out of range)"
        const val REASON_INVALID_RANK = "Invalid card rank (empty or malformed)"
        const val REASON_GAME_NOT_STARTED = "Game not started"
        const val REASON_GAME_OVER = "Game already ended"
    }

    override suspend fun handle(
        msg: JsonObject,
        type: String?,
        session: DefaultWebSocketServerSession,
        player: NetworkPlayer?,
        room: Room?,
    ) {
        val r =
            room ?: run {
                session.send(buildErrorMsg(Protocol.REASON_GAME_NOT_STARTED))
                return
            }
        val g =
            r.game as? GoFish ?: run {
                session.send(buildErrorMsg("Game type mismatch (not Go Fish)"))
                return
            }
        val p =
            player ?: run {
                session.send(buildErrorMsg("Player not found"))
                return
            }

        if (!r.started) {
            session.send(buildErrorMsg(Protocol.REASON_GAME_NOT_STARTED))
            return
        }
        if (g.isGameOver()) {
            session.send(buildErrorMsg(Protocol.REASON_GAME_OVER))
            return
        }

        when (type) {
            Protocol.TYPE_ASK -> handleAsk(msg, g, p, r, session)
            Protocol.TYPE_END_TURN -> handleEndTurn(g, p, r, session)
        }
    }

    private suspend fun handleAsk(
        msg: JsonObject,
        game: GoFish,
        player: NetworkPlayer,
        room: Room,
        session: DefaultWebSocketServerSession,
    ) {
        if (game.currentPlayer() != player.playerIndex) {
            session.send(buildErrorMsg(Protocol.REASON_NOT_YOUR_TURN))
            return
        }

        val target =
            msg["target"]
                ?.jsonPrimitive
                ?.content
                ?.toIntOrNull()
                .takeIf { it in 0 until room.players.size } ?: run {
                session.send(buildErrorMsg(Protocol.REASON_INVALID_TARGET))
                return
            }
        val rank =
            msg["rank"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: run {
                session.send(buildErrorMsg(Protocol.REASON_INVALID_RANK))
                return
            }

        val askSuccess = game.askForCard(target, rank)

        if (game.isGameOver()) {
            val winner = game.getWinner()
            RoomHandler.markRoomFinished(room, winner)
            broadcast(room, buildGameEndMsg(winner))
        } else {
            room.players.forEachIndexed { index, pl ->
                pl.session.send(buildStateMsg(Protocol.TYPE_ASK_RESULT, game, index, askSuccess))
            }
            RoomHandler.touchRoom(room)
        }
    }

    private suspend fun handleEndTurn(
        game: GoFish,
        player: NetworkPlayer,
        room: Room,
        session: DefaultWebSocketServerSession,
    ) {
        if (game.currentPlayer() != player.playerIndex) {
            session.send(buildErrorMsg(Protocol.REASON_NOT_YOUR_TURN))
            return
        }

        game.endTurn()

        if (game.isGameOver()) {
            val winner = game.getWinner()
            RoomHandler.markRoomFinished(room, winner)
            broadcast(room, buildGameEndMsg(winner))
        } else {
            room.players.forEachIndexed { index, pl ->
                pl.session.send(buildStateMsg(Protocol.TYPE_STATE, game, index))
            }
            RoomHandler.touchRoom(room)
        }
    }

    private fun buildErrorMsg(reason: String): String =
        Json.encodeToString(
            mapOf(
                "type" to Protocol.TYPE_ERROR,
                "reason" to reason,
            ),
        )

    private fun buildGameEndMsg(winner: Int): String =
        Json.encodeToString(
            mapOf(
                "type" to Protocol.TYPE_GAME_END,
                "winner" to winner,
            ),
        )

    internal fun buildStateMsg(
        type: String,
        game: GoFish,
        playerIndex: Int,
        askSuccess: Boolean? = null,
    ): String {
        val baseState = Json.parseToJsonElement(game.buildState(type, game, playerIndex)).jsonObject
        return buildJsonObject {
            baseState.forEach { (key, value) -> put(key, value) }
            askSuccess?.let { put("success", it) }
        }.toString()
    }
}
