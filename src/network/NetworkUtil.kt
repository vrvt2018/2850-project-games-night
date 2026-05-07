package com.example.network

import io.ktor.websocket.send

// There might be a better more object-oriented way to do this, but I'm putting it in a standalone file

/**
 * Send message to all clients in room over respective websocket
 */

suspend fun broadcast(
    room: Room,
    msg: String,
) = broadcast(room.players, msg)

/**
 * Send message to all players over socket
 *
 */

suspend fun broadcast(
    players: Iterable<NetworkPlayer>,
    msg: String,
) = players.forEach { it.session.send(msg) }
