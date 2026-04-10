package com.example.network

import com.example.games.Game

data class Room(
    val id: String,
    val players: MutableList<NetworkPlayer> = mutableListOf(),
    var game: Game? = null, // Generic type - originally was specific per game class
    var started: Boolean = false,
)