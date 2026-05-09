package com.example.games

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class ChessTest {
    private fun setField(
        game: Chess,
        name: String,
        value: Any,
    ) {
        val field = Chess::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(game, value)
    }

    private fun setupGame(): Chess {
        val game = Chess()
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        game.startGame()
        return game
    }

    fun testInitialSetup() {
        val game = setupGame()

        assertEquals(0, game.currentPlayer())
        assertFalse(game.isGameOver())
        assertEquals(-2, game.getWinner())

        val state = game.getState(0)
        val board = state["board"] as String

        assertEquals('K', board[60])
        assertEquals('k', board[4])
        assertEquals('P', board[52])
        assertEquals('p', board[12])
    }

    fun testMaxPlayers() {
        val game = Chess()

        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertFalse(game.addPlayer())
    }

    fun testLegalPawnMove() {
        val game = setupGame()

        val e2 = 52
        val e4 = 36

        val moves = game.legalMovesFrom(e2)

        assertTrue(moves.contains(e4))

        val success = game.makeMove(e2, e4)

        assertTrue(success)
        assertEquals(1, game.currentPlayer())
    }

    fun testIllegalBlockedRookMove() {
        val game = setupGame()

        val a1 = 56
        val a3 = 40

        val moves = game.legalMovesFrom(a1)

        assertFalse(moves.contains(a3))

        val success = game.makeMove(a1, a3)

        assertFalse(success)
    }

    fun testWrongTurnMove() {
        val game = setupGame()

        val success = game.makeMove(8, 16)

        assertFalse(success)
    }

    fun testKnightCanJump() {
        val game = setupGame()

        val g1 = 62
        val f3 = 45

        val success = game.makeMove(g1, f3)

        assertTrue(success)
    }

    fun testCannotCaptureOwnPiece() {
        val game = setupGame()

        val e1 = 60
        val e2 = 52

        val success = game.makeMove(e1, e2)

        assertFalse(success)
    }

    fun testPawnPromotion() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        val boardField = Chess::class.java.getDeclaredField("board")
        boardField.isAccessible = true

        val customBoard = CharArray(64) { '.' }

        customBoard[60] = 'K'
        customBoard[4] = 'k'
        customBoard[8] = 'P'

        boardField.set(game, customBoard)

        val success = game.makeMove(8, 0)

        assertTrue(success)

        val state = game.getState(0)
        val board = state["board"] as String

        assertEquals('Q', board[0])
    }

    fun testKingsideCastlingWhite() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        val boardField = Chess::class.java.getDeclaredField("board")
        boardField.isAccessible = true

        val customBoard = CharArray(64) { '.' }

        customBoard[60] = 'K'
        customBoard[63] = 'R'
        customBoard[4] = 'k'

        boardField.set(game, customBoard)

        val success = game.makeMove(60, 62)

        assertTrue(success)

        val state = game.getState(0)
        val board = state["board"] as String

        assertEquals('K', board[62])
        assertEquals('R', board[61])
    }

    fun testSimpleCheckmate() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        assertTrue(game.makeMove(53, 45))
        assertTrue(game.makeMove(12, 28))
        assertTrue(game.makeMove(54, 38))
        assertTrue(game.makeMove(3, 39))

        assertTrue(game.isGameOver())
        assertEquals(1, game.getWinner())
    }

    fun testStateContainsCorrectFields() {
        val game = setupGame()

        val state = game.getState(0)

        assertTrue(state.containsKey("board"))
        assertTrue(state.containsKey("turn"))
        assertTrue(state.containsKey("gameOver"))
        assertTrue(state.containsKey("winner"))
        assertTrue(state.containsKey("playerIndex"))
    }

    fun testBuildState() {
        val game = setupGame()

        val json = game.buildState("UPDATE", game, 0)

        assertTrue(json.contains("\"type\":\"UPDATE\""))
        assertTrue(json.contains("\"turn\":0"))
        assertTrue(json.contains("\"gameOver\":false"))
    }

    @Test
    fun testCheckmateSetsWinnerAndReason() {
        val game = setupGame()

        assertTrue(game.makeMove(53, 45))
        assertTrue(game.makeMove(12, 28))
        assertTrue(game.makeMove(54, 38))
        assertTrue(game.makeMove(3, 39))

        assertTrue(game.isGameOver())
        assertEquals(1, game.getWinner())
        assertEquals("checkmate", game.getEndReason())
        assertFalse(game.makeMove(52, 44))
    }

    @Test
    fun testInsufficientMaterialEndsAsDraw() {
        val game = setupGame()
        val customBoard = CharArray(64) { '.' }
        customBoard[52] = 'K'
        customBoard[4] = 'k'

        setField(game, "board", customBoard)
        setField(game, "turn", 0)

        assertTrue(game.makeMove(52, 44))

        assertTrue(game.isGameOver())
        assertEquals(-1, game.getWinner())
        assertEquals("insufficient_material", game.getEndReason())
        assertFalse(game.makeMove(4, 12))
    }

    @Test
    fun testCapturingKingEndsWithWinner() {
        val game = setupGame()
        val customBoard = CharArray(64) { '.' }
        customBoard[60] = 'K'
        customBoard[4] = 'k'
        customBoard[12] = 'R'

        setField(game, "board", customBoard)
        setField(game, "turn", 0)

        assertTrue(game.makeMove(12, 4))

        assertTrue(game.isGameOver())
        assertEquals(0, game.getWinner())
        assertEquals("king_captured", game.getEndReason())
        assertFalse(game.makeMove(4, 12))
    }
}
