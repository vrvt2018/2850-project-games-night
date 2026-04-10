//package com.example.games
//
//import kotlin.test.*
//
//class GoFishTest {
//
//    private fun createGame(playerCount: Int): GoFish {
//        val game = GoFish()
//        repeat(playerCount) { game.addPlayer() }
//        game.startGame()
//        return game
//    }
//
//    @Test
//    fun testAddPlayers() {
//        val game = GoFish()
//        assertTrue(game.addPlayer())
//        assertTrue(game.addPlayer())
//        assertTrue(game.addPlayer())
//        assertTrue(game.addPlayer())
//        assertFalse(game.addPlayer()) // Max 4
//    }
//
//    @Test
//    fun testStartGame2Players() {
//        val game = createGame(2)
//        assertTrue(game.started)
//        assertFalse(game.isGameOver())
//        assertEquals(0, game.currentPlayer())
//
//        val state = game.getState(0)
//        // 2 players get 7 cards each
//        val handSizes = state["handSizes"] as List<*>
//        assertEquals(7, handSizes[0])
//        assertEquals(7, handSizes[1])
//        assertEquals(52 - 14, state["deckSize"])
//    }
//
//    @Test
//    fun testStartGame4Players() {
//        val game = createGame(4)
//        val state = game.getState(0)
//        // 4 players get 5 cards each
//        val handSizes = state["handSizes"] as List<*>
//        assertEquals(4, handSizes.size)
//        handSizes.forEach { assertEquals(5, it) }
//        assertEquals(52 - 20, state["deckSize"])
//    }
//
//    @Test
//    fun testTurnAdvancesAfterGoFish() {
//        val game = createGame(2)
//        assertEquals(0, game.currentPlayer())
//
//        // Ask for a rank unlikely to match — force a Go Fish
//        // We call askForCard and if it returns false, the turn should end
//        val result = game.askForCard(1, "ZZZZZ") // non-existent rank
//        assertFalse(result) // Go Fish
//
//        // Player must explicitly call endTurn
//        game.endTurn()
//        assertEquals(1, game.currentPlayer())
//    }
//
//    @Test
//    fun testCannotAskSelf() {
//        val game = createGame(2)
//        val result = game.askForCard(0, "A") // asking self
//        assertFalse(result)
//    }
//
//    @Test
//    fun testCannotAskInvalidPlayer() {
//        val game = createGame(2)
//        val result = game.askForCard(5, "A") // out of range
//        assertFalse(result)
//    }
//
//    @Test
//    fun testGetStateContainsRequiredFields() {
//        val game = createGame(2)
//        val state = game.getState(0)
//
//        assertNotNull(state["turn"])
//        assertNotNull(state["myHand"])
//        assertNotNull(state["myHandRanks"])
//        assertNotNull(state["handSizes"])
//        assertNotNull(state["books"])
//        assertNotNull(state["deckSize"])
//        assertNotNull(state["gameOver"])
//        assertNotNull(state["playerIndex"])
//        assertNotNull(state["numPlayers"])
//    }
//
//    @Test
//    fun testPlayerIndexIsolation() {
//        val game = createGame(2)
//        val state0 = game.getState(0)
//        val state1 = game.getState(1)
//
//        // Each player should see their own hand (different cards)
//        assertEquals(0, state0["playerIndex"])
//        assertEquals(1, state1["playerIndex"])
//    }
//
//    @Test
//    fun testBooksInitiallyZero() {
//        val game = createGame(2)
//        val state = game.getState(0)
//        val books = state["books"] as List<*>
//        assertEquals(0, books[0])
//        assertEquals(0, books[1])
//    }
//}
