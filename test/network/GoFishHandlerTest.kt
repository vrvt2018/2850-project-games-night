package com.example.network

import com.example.games.GoFish
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoFishHandlerTest {
    private fun createStartedGame(playerCount: Int): GoFish {
        val game = GoFish()
        repeat(playerCount) { game.addPlayer() }
        game.startGame()
        return game
    }

    @Test
    fun testAskResultStateMessageSerializes() {
        val game = createStartedGame(2)

        val payload = GoFishHandler.buildStateMsg("ASK_RESULT", game, 0, true)
        val msg = Json.parseToJsonElement(payload).jsonObject

        assertEquals("ASK_RESULT", msg["type"]?.jsonPrimitive?.content)
        assertEquals(msg["success"]?.jsonPrimitive?.boolean, true)
        assertTrue(msg.containsKey("myHand"))
        assertTrue(msg.containsKey("myHandRanks"))
        assertTrue(msg.containsKey("handSizes"))
        assertTrue(msg.containsKey("books"))
    }

    @Test
    fun testRegularStateMessageDoesNotInjectSuccessField() {
        val game = createStartedGame(2)

        val payload = GoFishHandler.buildStateMsg("STATE", game, 1)
        val msg = Json.parseToJsonElement(payload).jsonObject

        assertEquals("STATE", msg["type"]?.jsonPrimitive?.content)
        assertFalse(msg.containsKey("success"))
        assertTrue(msg.containsKey("turn"))
        assertTrue(msg.containsKey("deckSize"))
    }
}
