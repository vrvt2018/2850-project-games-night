package com.example.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GoFishTest {
    private fun setField(
        game: GoFish,
        name: String,
        value: Any,
    ) {
        val field = GoFish::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(game, value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> replaceMutableListField(
        game: GoFish,
        name: String,
        values: List<T>,
    ) {
        val field = GoFish::class.java.getDeclaredField(name)
        field.isAccessible = true
        val list = field.get(game) as MutableList<T>
        list.clear()
        list.addAll(values)
    }

    private fun createGame(playerCount: Int): GoFish {
        val game = GoFish()
        repeat(playerCount) {
            game.addPlayer()
        }
        game.startGame()
        return game
    }

    fun testAddPlayers() {
        val game = GoFish()

        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())

        assertFalse(game.addPlayer())
    }

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

    fun testCurrentPlayerStartsAtZero() {
        val game = createGame(2)

        assertEquals(0, game.currentPlayer())
    }

    fun testCannotAskSelf() {
        val game = createGame(2)

        val result = game.askForCard(0, "A")

        assertFalse(result)
    }

    fun testCannotAskInvalidPlayer() {
        val game = createGame(2)

        val result = game.askForCard(99, "A")

        assertFalse(result)
    }

    fun testEndTurnChangesPlayer() {
        val game = createGame(2)

        assertEquals(0, game.currentPlayer())

        game.endTurn()

        assertEquals(1, game.currentPlayer())
    }

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

    fun testBooksInitiallyZero() {
        val game = createGame(2)

        val state = game.getState(0)

        val books = state["books"] as List<*>

        assertEquals(0, books[0])
        assertEquals(0, books[1])
    }

    fun testMyHandMatchesHandSize() {
        val game = createGame(2)

        val state = game.getState(0)

        val myHand = state["myHand"] as List<*>
        val handSizes = state["handSizes"] as List<*>

        assertEquals(handSizes[0], myHand.size)
    }

    fun testMyHandRanksMatchesHandSize() {
        val game = createGame(2)

        val state = game.getState(0)

        val myHandRanks = state["myHandRanks"] as List<*>
        val handSizes = state["handSizes"] as List<*>

        assertEquals(handSizes[0], myHandRanks.size)
    }

    fun testGameNotOverAtStart() {
        val game = createGame(2)

        assertFalse(game.isGameOver())
        assertEquals(-2, game.getWinner())
    }

    @Test
    fun testMissRequiresEndTurnAndDoesNotDrawRepeatedly() {
        val game = createGame(2)
        replaceMutableListField(
            game,
            "hands",
            listOf(
                mutableListOf(Card(Suit.Hearts, 1)),
                mutableListOf(Card(Suit.Spades, 2)),
            ),
        )
        replaceMutableListField(
            game,
            "drawPile",
            listOf(
                Card(Suit.Clubs, 3),
                Card(Suit.Clubs, 4),
            ),
        )
        replaceMutableListField(game, "books", listOf(0, 0))
        setField(game, "turn", 0)
        setField(game, "mustEndTurn", false)

        assertFalse(game.askForCard(1, "A"))

        var state = game.getState(0)
        assertEquals(1, state["deckSize"])
        assertEquals(2, (state["myHand"] as List<*>).size)
        assertTrue(game.isWaitingForEndTurn())

        assertFalse(game.askForCard(1, "A"))

        state = game.getState(0)
        assertEquals(1, state["deckSize"])
        assertEquals(2, (state["myHand"] as List<*>).size)

        game.endTurn()

        assertEquals(1, game.currentPlayer())
        assertFalse(game.isWaitingForEndTurn())
    }

    @Test
    fun testFinalBookEndsGameWithWinner() {
        val game = createGame(2)
        replaceMutableListField(
            game,
            "hands",
            listOf(
                mutableListOf(Card(Suit.Hearts, 1), Card(Suit.Diamonds, 1), Card(Suit.Spades, 1)),
                mutableListOf(Card(Suit.Clubs, 1)),
            ),
        )
        replaceMutableListField(game, "drawPile", emptyList<Card>())
        replaceMutableListField(game, "books", listOf(6, 6))
        setField(game, "turn", 0)
        setField(game, "mustEndTurn", false)

        assertTrue(game.askForCard(1, "A"))

        assertTrue(game.isGameOver())
        assertEquals(0, game.getWinner())
    }

    @Test
    fun testTiedBookCountEndsAsDraw() {
        val game = createGame(4)
        replaceMutableListField(
            game,
            "hands",
            listOf(
                mutableListOf<Card>(),
                mutableListOf<Card>(),
                mutableListOf<Card>(),
                mutableListOf<Card>(),
            ),
        )
        replaceMutableListField(game, "drawPile", emptyList<Card>())
        replaceMutableListField(game, "books", listOf(4, 4, 3, 2))
        setField(game, "turn", 0)
        setField(game, "mustEndTurn", false)

        game.endTurn()

        assertTrue(game.isGameOver())
        assertEquals(-1, game.getWinner())
    }

    @Test
    fun testEmptyDeckEndsByBookCountWhenOnlyOnePlayerCanMove() {
        val game = createGame(2)
        replaceMutableListField(
            game,
            "hands",
            listOf(
                mutableListOf(Card(Suit.Hearts, 9)),
                mutableListOf(Card(Suit.Spades, 9)),
            ),
        )
        replaceMutableListField(game, "drawPile", emptyList<Card>())
        replaceMutableListField(game, "books", listOf(0, 0))
        setField(game, "turn", 0)
        setField(game, "mustEndTurn", false)

        assertTrue(game.askForCard(1, "9"))

        assertTrue(game.isGameOver())
        assertEquals(-1, game.getWinner())
        val state = game.getState(0)
        assertEquals(listOf(0, 0), state["books"])
    }

    @Test
    fun testEmptyDeckWinnerUsesCompletedBooksOnly() {
        val game = createGame(2)
        replaceMutableListField(
            game,
            "hands",
            listOf(
                mutableListOf(Card(Suit.Hearts, 9)),
                mutableListOf(Card(Suit.Spades, 9)),
            ),
        )
        replaceMutableListField(game, "drawPile", emptyList<Card>())
        replaceMutableListField(game, "books", listOf(2, 1))
        setField(game, "turn", 0)
        setField(game, "mustEndTurn", false)

        assertTrue(game.askForCard(1, "9"))

        assertTrue(game.isGameOver())
        assertEquals(0, game.getWinner())
        assertEquals(listOf(2, 1), game.getState(0)["books"])
    }
}
