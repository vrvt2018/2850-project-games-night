package com.example

import kotlin.test.*
import java.io.File
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.deleteAll

class DatabaseTest {

    @BeforeTest
    fun setup() {
        // Use an in-memory test database
        initDatabase("jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
        transaction {
            Users.deleteAll()
            Sessions.deleteAll()
            Games.deleteAll()
            GameHistory.deleteAll()
        }
    }

    @Test
    fun testUserCreationAndLookup() {
        val success = createUser("testuser", "test@example.com", hashPassword("secret123"))
        assertTrue(success, "User should be created successfully")

        val user = findUserByCredentials("testuser", hashPassword("secret123"))
        assertNotNull(user, "Should find user with correct credentials")

        val duplicate = createUser("testuser", "other@example.com", hashPassword("secret123"))
        assertFalse(duplicate, "Should not allow duplicate usernames")
    }

    @Test
    fun testSessionManagement() {
        createUser("sessionuser", "sess@example.com", hashPassword("pass"))
        val token = createSession("sessionuser")
        
        val username = getUsernameByToken(token)
        assertEquals("sessionuser", username, "Token should resolve to the correct username")

        deleteSession(token)
        val afterLogout = getUsernameByToken(token)
        assertNull(afterLogout, "Session should be invalidated after logout")
    }

    @Test
    fun testPasswordHashing() {
        val hash1 = hashPassword("MySecurePass1!")
        val hash2 = hashPassword("MySecurePass1!")
        val hash3 = hashPassword("DifferentPass")

        assertEquals(hash1, hash2, "Same password should yield identical hashes with static salt")
        assertNotEquals(hash1, hash3, "Different passwords must yield different hashes")
        assertTrue(hash1.length > 30, "Hash should be adequately long")
    }

    @Test
    fun testMatchHistoryRetrieval() {
        recordGameResult("Chess", "alice", listOf("alice", "bob"))

        val history = getMatchHistory()

        assertEquals(1, history.size)
        assertEquals("Chess", history.first().gameName)
        assertEquals("alice", history.first().winner)
        assertEquals("alice", history.first().winnerLabel)
        assertEquals("alice, bob", history.first().playersLabel)
    }

    @Test
    fun testValidationFunctions() {
        assertTrue(isValidUsername("player1"))
        assertFalse(isValidUsername("pl"), "Too short")
        assertFalse(isValidUsername("player one"), "No spaces allowed")

        assertTrue(isValidEmail("test@example.com"))
        assertFalse(isValidEmail("test@example"))

        assertTrue(isValidPassword("Strong123"))
        assertFalse(isValidPassword("weak123"), "No capital letter")
    }
}
