package com.example

import com.example.games.Game
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SchemaUtils.create
import org.jetbrains.exposed.sql.SchemaUtils.createMissingTablesAndColumns

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 64).uniqueIndex()
    val email = varchar("email", 128).uniqueIndex()
    val passwordHash = varchar("password_hash", 256)
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val token = varchar("token", 256).uniqueIndex()
    val username = varchar("username", 64).references(Users.username)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(token)
}

object Games : Table("games") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 64).uniqueIndex()
    val maxPlayers = integer("max_players")
    override val primaryKey = PrimaryKey(id)
}

fun initDatabase() {
    val dbUrl = System.getenv("DB_URL") ?: "jdbc:h2:./build/db/games-night;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
    val dbUser = System.getenv("DB_USER") ?: "sa"
    val dbPassword = System.getenv("DB_PASSWORD") ?: ""

    Database.connect(dbUrl, driver = when {
        dbUrl.startsWith("jdbc:postgresql") -> "org.postgresql.Driver"
        dbUrl.startsWith("jdbc:h2") -> "org.h2.Driver"
        else -> throw IllegalArgumentException("Unsupported DB URL: $dbUrl")
    }, user = dbUser, password = dbPassword)

    transaction {
        try {
            create(Users, Sessions, Games)
        } catch (ignored: Exception) {
            // table may already exist, ignore
        }

        if (Games.selectAll().empty()) {
            Games.insert { it[name] = "Chess"; it[maxPlayers] = 2 }
            Games.insert { it[name] = "Deck"; it[maxPlayers] = 4 }
        }
    }
}

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

fun findUserByUsername(username: String): User? {
    return transaction {
        Users.select { Users.username eq username }
            .map { User(it[Users.username], "", it[Users.email]) }
            .singleOrNull()
    }
}

fun findUserByCredentials(username: String, passwordHash: String): User? {
    return transaction {
        Users.select { (Users.username eq username) and (Users.passwordHash eq passwordHash) }
            .map { User(it[Users.username], "", it[Users.email]) }
            .singleOrNull()
    }
}

fun getUserEmail(username: String): String? {
    return transaction {
        Users.select { Users.username eq username }
            .map { it[Users.email] }
            .singleOrNull()
    }
}

fun createSession(username: String): String {
    val token = "token-${System.currentTimeMillis()}-${username.hashCode()}"
    transaction {
        Sessions.insert {
            it[Sessions.token] = token
            it[Sessions.username] = username
            it[Sessions.createdAt] = System.currentTimeMillis()
        }
    }
    return token
}

fun getUsernameByToken(token: String): String? {
    return transaction {
        Sessions.select { Sessions.token eq token }
            .map { it[Sessions.username] }
            .singleOrNull()
    }
}

fun findAllGames(): List<Game> {
    return transaction {
        Games.selectAll().map { Game(it[Games.name], it[Games.maxPlayers]) }
    }
}

fun findGameByName(name: String): Game? {
    return transaction {
        Games.select { Games.name.lowerCase() eq name.lowercase() }
            .map { Game(it[Games.name], it[Games.maxPlayers]) }
            .singleOrNull()
    }
}

fun hashPassword(password: String): String {
    return java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
