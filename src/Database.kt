// AI-assisted: Exposed ORM table definitions, HMAC password hashing, and leaderboard queries (Gemini)
// Handles user accounts, sessions, game catalogue, and leaderboard persistence
package com.example

import com.example.games.Game
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import java.security.SecureRandom
import java.util.regex.Pattern
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

/** Represents a user account without sensitive auth data directly exposed to clients. */
data class User(val username: String, val passwordHash: String, val email: String)

// ─────────────────────────────────────────────────────────────────────────────
// Table definitions
// ─────────────────────────────────────────────────────────────────────────────

/** Stores registered user accounts. */
object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val email = varchar("email", 128).uniqueIndex()
    val passwordHash = varchar("password_hash", 256)
    override val primaryKey = PrimaryKey(id)
}

/** Stores active login sessions. Token is a cryptographically random UUID. */
object Sessions : Table("sessions") {
    val token = varchar("token", 256).uniqueIndex()
    val username = varchar("username", 64).references(Users.username)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(token)
}

/** Catalogue of available games. */
object Games : Table("games") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 64).uniqueIndex()
    val maxPlayers = integer("max_players")
    override val primaryKey = PrimaryKey(id)
}

/**
 * Extension feature: records the result of each completed game.
 * Used to build the leaderboard.
 */
object GameHistory : Table("game_history") {
    val id = integer("id").autoIncrement()
    val gameName = varchar("game_name", 64)
    val winner = varchar("winner", 64)          // username of the winner
    val players = varchar("players", 512)        // comma-separated usernames
    val playedAt = long("played_at")
    override val primaryKey = PrimaryKey(id)
}

// ─────────────────────────────────────────────────────────────────────────────
// Initialisation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Initialises the database connection and creates any missing tables.
 * Connection parameters are read from environment variables (defaults to H2 in dev mode).
 */
fun initDatabase(testDbUrl: String? = null) {
    val dbUrl = testDbUrl ?: System.getenv("DB_URL") ?: "jdbc:h2:./build/db/games-night;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    val dbUser = System.getenv("DB_USER") ?: "sa"
    val dbPassword = System.getenv("DB_PASSWORD") ?: "123456"

    Database.connect(
        dbUrl,
        driver = when {
            dbUrl.startsWith("jdbc:postgresql") -> "org.postgresql.Driver"
            dbUrl.startsWith("jdbc:h2") -> "org.h2.Driver"
            else -> throw IllegalArgumentException("Unsupported DB URL: $dbUrl")
        },
        user = dbUser,
        password = dbPassword
    )

    transaction {
        create(Users, Sessions, Games, GameHistory)
        println("Database tables checked/created.")

        // Seed the games catalogue if empty
        if (Games.selectAll().empty()) {
            println("Seeding games catalogue...")
            Games.insert { it[name] = "Go Fish"; it[maxPlayers] = 4 }
            Games.insert { it[name] = "Chess"; it[maxPlayers] = 2 }
        } else {
            println("Games catalogue already contains: ${findAllGames().map { it.name }.joinToString(", ")}")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input validation
// ─────────────────────────────────────────────────────────────────────────────

private val EMAIL_PATTERN: Pattern = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val USERNAME_PATTERN: Pattern = Pattern.compile("^[A-Za-z0-9_-]{3,32}$")

/**
 * Returns true if [username] is 3–32 alphanumeric characters (hyphens and underscores allowed).
 */
fun isValidUsername(username: String): Boolean = USERNAME_PATTERN.matcher(username).matches()

/**
 * Returns true if [email] looks like a valid email address.
 */
fun isValidEmail(email: String): Boolean = EMAIL_PATTERN.matcher(email).matches()

/**
 * Returns true if [password] meets the minimum requirements:
 * at least 8 characters, at least one digit, at least one letter.
 */
fun isValidPassword(password: String): Boolean =
    password.length >= 8 && password.any { it.isDigit() } && password.any { it.isLetter() }

// ─────────────────────────────────────────────────────────────────────────────
// Password hashing (HMAC-SHA256 with a static salt from env)
// ─────────────────────────────────────────────────────────────────────────────

/** Application-level HMAC secret. Override via env variable in production. */
private val HMAC_SECRET: ByteArray =
    (System.getenv("PASSWORD_HMAC_SECRET") ?: "games-night-hmac-secret-dev-only").toByteArray(Charsets.UTF_8)

/**
 * Hashes [password] using HMAC-SHA256 with the application secret.
 * Not as strong as bcrypt, but significantly better than plain SHA-256.
 * @return Hex-encoded HMAC digest.
 */
fun hashPassword(password: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(HMAC_SECRET, "HmacSHA256"))
    return mac.doFinal(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

// ─────────────────────────────────────────────────────────────────────────────
// User CRUD
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Creates a new user account.
 * @return true on success; false if the username or email is already taken.
 */
fun createUser(username: String, email: String, passwordHash: String): Boolean {
    return transaction {
        if (Users.select { (Users.username eq username) or (Users.email eq email) }.any()) {
            false
        } else {
            Users.insert {
                it[Users.username] = username
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
            }
            true
        }
    }
}

/**
 * Looks up a user by [username] and returns a basic [User] object without the password hash.
 */
fun findUserByUsername(username: String): User? {
    return transaction {
        Users.select { Users.username eq username }
            .map { User(it[Users.username], "", it[Users.email]) }
            .singleOrNull()
    }
}

/**
 * Validates credentials and returns the matching [User], or null if invalid.
 */
fun findUserByCredentials(username: String, passwordHash: String): User? {
    return transaction {
        Users.select { (Users.username eq username) and (Users.passwordHash eq passwordHash) }
            .map { User(it[Users.username], "", it[Users.email]) }
            .singleOrNull()
    }
}

/**
 * Returns the email address for [username], or null if the user does not exist.
 */
fun getUserEmail(username: String): String? {
    return transaction {
        Users.select { Users.username eq username }
            .map { it[Users.email] }
            .singleOrNull()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Session management
// ─────────────────────────────────────────────────────────────────────────────

private val secureRandom = SecureRandom()

/**
 * Creates a new cryptographically random session token for [username] and stores it in the DB.
 * Token is a UUID-v4 formatted string.
 * @return The new session token.
 */
fun createSession(username: String): String {
    // Use SecureRandom for a cryptographically random token (not predictable)
    val tokenBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
    val token = tokenBytes.joinToString("") { "%02x".format(it) }
    transaction {
        Sessions.insert {
            it[Sessions.token] = token
            it[Sessions.username] = username
            it[Sessions.createdAt] = System.currentTimeMillis()
        }
    }
    return token
}

/**
 * Returns the username associated with [token], or null if the session does not exist.
 */
fun getUsernameByToken(token: String): String? {
    return transaction {
        Sessions.select { Sessions.token eq token }
            .map { it[Sessions.username] }
            .singleOrNull()
    }
}

/**
 * Deletes the session identified by [sessionToken] (used during logout).
 */
fun deleteSession(sessionToken: String) {
    transaction {
        Sessions.deleteWhere { Sessions.token eq sessionToken }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game catalogue
// ─────────────────────────────────────────────────────────────────────────────

/** Lightweight DTO representing a game catalogue entry. */ // Who wrote these comments??
data class GameInfo(val name: String, val maxPlayers: Int)

/**
 * Returns all games currently registered in the catalogue.
 */
fun findAllGames(): List<GameInfo> {
    return transaction {
        Games.selectAll().map { GameInfo(it[Games.name], it[Games.maxPlayers]) }
    }
}

/**
 * Finds a game by [name] (case-insensitive).
 */
fun findGameByName(name: String): GameInfo? {
    return transaction {
        Games.select { Games.name.lowerCase() eq name.lowercase() }
            .map { GameInfo(it[Games.name], it[Games.maxPlayers]) }
            .singleOrNull()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game history / Leaderboard (extension feature)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Records the result of a completed game.
 * @param gameName  The name of the game (e.g. "Chess").
 * @param winner    Username of the winning player (or "" for a draw).
 * @param players   List of all participating usernames.
 */
fun recordGameResult(gameName: String, winner: String, players: List<String>) {
    transaction {
        GameHistory.insert {
            it[GameHistory.gameName] = gameName
            it[GameHistory.winner] = winner
            it[GameHistory.players] = players.joinToString(",")
            it[GameHistory.playedAt] = System.currentTimeMillis()
        }
    }
}

/** Represents a leaderboard entry for a single player. */
data class LeaderboardEntry(val username: String, val wins: Int, val gamesPlayed: Int)

/**
 * Returns leaderboard data: each player's total wins and games played, ordered by wins descending.
 */
fun getLeaderboard(): List<LeaderboardEntry> {
    return transaction {
        val allHistory = GameHistory.selectAll().toList()
        // Gather all unique players
        val players = allHistory
            .flatMap { it[GameHistory.players].split(",") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        players.map { username ->
            val played = allHistory.count { username in it[GameHistory.players].split(",") }
            val wins = allHistory.count { it[GameHistory.winner] == username }
            LeaderboardEntry(username, wins, played)
        }.sortedByDescending { it.wins }
    }
}
