package com.example.network

import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.JsonObject

/**
 * All handlers should inherit this to be correctly forwarded any messages sent by the server
 */
abstract class GameSocketHandler {
    /**
     * For the game instane to handle any messages forwarded from the room handler
     */
    abstract suspend fun handle(
        msg: JsonObject,
        type: String?,
        session: DefaultWebSocketServerSession,
        player: NetworkPlayer?,
        room: Room?,
    )
}
