package com.example.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkUtilTest {
    @Test
    fun testPlayerLeftGameEndMessage() {
        val payload = RoomHandler.buildPlayerLeftGameEndMessage("guest123", 1, 0)
        val msg = Json.parseToJsonElement(payload).jsonObject // Create JSON object from payload

        // Unpack msg and assert parts are correct
        assertEquals("GAME_END", msg["type"]?.jsonPrimitive?.content)
        assertEquals("player_left", msg["reason"]?.jsonPrimitive?.content)
        assertEquals("guest123 left the room. You win by default.", msg["message"]?.jsonPrimitive?.content)
        assertEquals("0", msg["winner"]?.jsonPrimitive?.content)
    }
}
