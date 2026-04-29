package com.example.network

import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.json.JsonObject

abstract class GameSocketHandler {
    abstract suspend fun handle(
        msg: JsonObject,
        type: String?,
        session: DefaultWebSocketServerSession,
        player: NetworkPlayer?,
        room: Room?,
    )
}
