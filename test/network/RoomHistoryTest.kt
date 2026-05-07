package com.example.network

import com.example.GameHistory
import com.example.getMatchHistory
import com.example.initDatabase
import com.example.games.Chess
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomHistoryTest {
    @BeforeTest
    fun setup() {
        initDatabase("jdbc:h2:mem:roomhistory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        transaction {
            GameHistory.deleteAll()
        }
    }

    @Test
    fun testMarkRoomFinishedRecordsHistoryOnlyOnce() = runBlocking {
        val room =
            Room(
                id = "1234",
                game = Chess(),
                started = true,
                participantUsernames = mutableMapOf(0 to "alice", 1 to "bob"),
            )

        RoomHandler.markRoomFinished(room, 1)
        RoomHandler.markRoomFinished(room, 1)

        val history = getMatchHistory()
        assertEquals(1, history.size)
        assertEquals("Chess", history.first().gameName)
        assertEquals("bob", history.first().winner)
        assertEquals("alice, bob", history.first().playersLabel)
        assertTrue(room.historyRecorded)
    }
}
