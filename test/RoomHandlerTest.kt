package com.example

import com.example.network.RoomHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

internal class RoomHandlerTest {
    // Should only start a game if it is a valid game
    @Test
    fun testStartInvalidGame() {
        assertFails { RoomHandler.createGameByName("ThisIsNotAGame") }
    }

    // generateRoomID() should only generate a numeric 4-digit string
    @Test
    fun testValidRoomID() {
        // Generate string until it has a trailing zero - this is most likely to cause padding issues
        var roomID = "   "
        while (roomID[0] != '0') {
            roomID = RoomHandler.generateRoomId()
        }

        // ID must be only numbers
        for (ch in roomID) assertTrue(ch.isDigit())

        // Length must be exactly 4
        assertEquals(roomID.length, 4)
    }
}
