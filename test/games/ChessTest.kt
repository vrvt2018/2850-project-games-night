package com.example.games

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class ChessTest {
    @Test
    fun testInitialSetup() {
        val game = Chess()
        assertTrue(game.addPlayer())
        assertTrue(game.addPlayer())
        assertFalse(game.addPlayer()) // Max 2 players

        game.startGame()
        assertEquals(0, game.currentPlayer()) // White to move
        assertFalse(game.isGameOver())
        assertEquals(-2, game.getWinner())
    }

    @Test
    fun testLegalPawnMove() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        // White pawn on e2 (row 6, col 4 -> index 52) moves to e4 (row 4, col 4 -> index 36)
        val e2 = 52
        val e4 = 36
        val moves = game.legalMovesFrom(e2)
        assertTrue(moves.contains(e4), "Pawn should be able to double move from start")

        val success = game.makeMove(e2, e4)
        assertTrue(success)
        assertEquals(1, game.currentPlayer()) // Black to move
    }

    @Test
    fun testIllegalMove() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        // White rook on a1 (row 7, col 0 -> index 56) tries to move to a3 (row 5, col 0 -> index 40)
        // Illegal because a2 pawn is in the way
        val a1 = 56
        val a3 = 40
        val moves = game.legalMovesFrom(a1)
        assertFalse(moves.contains(a3))

        val success = game.makeMove(a1, a3)
        assertFalse(success)
    }

    @Test
    fun testWrongColorMove() {
        val game = Chess()
        game.addPlayer()
        game.addPlayer()
        game.startGame()

        // Black pawn should not be allowed to move on White's turn
        val success = game.makeMove(8, 16) // Black pawn on a7
        assertFalse(success)
    }
}
