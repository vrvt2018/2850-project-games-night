package com.example.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoFishTest {
    @Test
    private fun createGame(playerCount: Int): GoFish {
        val game = GoFish()
        repeat(playerCount) {
            game.addPlayer()
        }
        game.startGame()
        return game
    }

    @Test
    fun testAddPlayers() {
        val game = GoFish()

        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())

        assertFalse(game.addPlayer())
    }

    @Test
    fun testStartGame2Players() {
        val game = createGame(2)

        assertTrue(game.started)
        assertFalse(game.isGameOver())
        assertEquals(0, game.currentPlayer())

        val state = game.getState(0)

        val handSizes = state["handSizes"] as List<*>

        assertEquals(7, handSizes[0])
        assertEquals(7, handSizes[1])

        assertEquals(38, state["deckSize"])
    }

    @Test
    fun testStartGame4Players() {
        val game = createGame(4)

        val state = game.getState(0)

        val handSizes = state["handSizes"] as List<*>

        assertEquals(4, handSizes.size)

        handSizes.forEach {
            assertEquals(5, it)
        }

        assertEquals(32, state["deckSize"])
    }

    @Test
    fun testCurrentPlayerStartsAtZero() {
        val game = createGame(2)

        assertEquals(0, game.currentPlayer())
    }

    @Test
    fun testCannotAskSelf() {
        val game = createGame(2)

        val result = game.askForCard(0, "A")

        assertFalse(result)
    }

    @Test
    fun testCannotAskInvalidPlayer() {
        val game = createGame(2)

        val result = game.askForCard(99, "A")

        assertFalse(result)
    }

    @Test
    fun testEndTurnChangesPlayer() {
        val game = createGame(2)

        assertEquals(0, game.currentPlayer())

        game.endTurn()

        assertEquals(1, game.currentPlayer())
    }

    @Test
    fun testGetStateContainsRequiredFields() {
        val game = createGame(2)

        val state = game.getState(0)

        assertNotNull(state["turn"])
        assertNotNull(state["deckSize"])
        assertNotNull(state["numPlayers"])
        assertNotNull(state["gameOver"])
        assertNotNull(state["books"])
        assertNotNull(state["handSizes"])
        assertNotNull(state["myHand"])
        assertNotNull(state["myHandRanks"])
    }

    @Test
    fun testBooksInitiallyZero() {
        val game = createGame(2)

        val state = game.getState(0)

        val books = state["books"] as List<*>

        assertEquals(0, books[0])
        assertEquals(0, books[1])
    }

    @Test
    fun testMyHandMatchesHandSize() {
        val game = createGame(2)

        val state = game.getState(0)

        val myHand = state["myHand"] as List<*>
        val handSizes = state["handSizes"] as List<*>

        assertEquals(handSizes[0], myHand.size)
    }

    @Test
    fun testMyHandRanksMatchesHandSize() {
        val game = createGame(2)

        val state = game.getState(0)

        val myHandRanks = state["myHandRanks"] as List<*>
        val handSizes = state["handSizes"] as List<*>

        assertEquals(handSizes[0], myHandRanks.size)
    }

    @Test
    fun testGameNotOverAtStart() {
        val game = createGame(2)

        assertFalse(game.isGameOver())
        assertEquals(-2, game.getWinner())
    }
}
