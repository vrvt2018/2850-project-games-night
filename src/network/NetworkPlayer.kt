package com.example.network

import io.ktor.server.websocket.DefaultWebSocketServerSession

data class NetworkPlayer(
    val id: String,
    val session: DefaultWebSocketServerSession,
    val playerIndex: Int,
    val username: String,
)
