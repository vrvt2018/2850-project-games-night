package com.example.network

import com.example.games.Game

data class RoomChatMessage(
    val author: String,
    val text: String,
    val sentAt: Long = System.currentTimeMillis(),
)

data class RoomStatusSnapshot(
    val roomId: String,
    val gameName: String,
    val statusLabel: String,
    val statusTone: String,
    val hostUsername: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val playerSummary: String,
    val playerNamesDisplay: String,
    val updatedAtLabel: String,
)

data class Room(
    val id: String,
    val players: MutableList<NetworkPlayer> = mutableListOf(),
    var game: Game? = null, // Generic type - originally was specific per game class
    var started: Boolean = false,
    var finished: Boolean = false,
    val participantUsernames: MutableMap<Int, String> = mutableMapOf(),
    var historyRecorded: Boolean = false,
    val chatMessages: MutableList<RoomChatMessage> = mutableListOf(),
    var updatedAt: Long = System.currentTimeMillis(),
)
