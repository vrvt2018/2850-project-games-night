package com.example.network

import com.example.games.Chess
import com.example.games.Game
import com.example.games.GoFish
import com.example.getUsernameByToken
import com.example.recordGameResult
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RoomHandler {
    private val rooms: ConcurrentHashMap<String, Room> = ConcurrentHashMap()
    private val statusSubscribers = ConcurrentHashMap.newKeySet<DefaultWebSocketServerSession>()
    private const val CHAT_HISTORY_LIMIT = 50
    private val chatTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun createGameByName(name: String?): Game? =
        when (name?.lowercase()) {
            "chess" -> {
                Chess()
            }

            "go fish", "gofish" -> {
                GoFish()
            }

            else -> {
                println(name?.lowercase())
                throw Exception("Game does not exist!")
            }
        }

    fun getGameStringFromType(type: String): String = type.split('_')[1]

    fun getRoomStatusSnapshots(): List<RoomStatusSnapshot> =
        rooms.values
            .map { room ->
                synchronized(room) {
                    val status =
                        when {
                            room.finished -> "Finished" to "status-finished"
                            room.started -> "In Progress" to "status-live"
                            else -> "Waiting" to "status-waiting"
                        }
                    val game = room.game
                    val playerNames = room.players.map { it.username }
                    RoomStatusSnapshot(
                        roomId = room.id,
                        gameName = game?.name ?: "Unknown Game",
                        statusLabel = status.first,
                        statusTone = status.second,
                        hostUsername = playerNames.firstOrNull() ?: "Unknown host",
                        playerCount = room.players.size,
                        maxPlayers = game?.maxPlayers ?: room.players.size,
                        playerSummary = "${room.players.size} / ${game?.maxPlayers ?: room.players.size} players",
                        playerNamesDisplay = playerNames.ifEmpty { listOf("Waiting for players") }.joinToString(", "),
                        updatedAtLabel = formatUpdatedAt(room.updatedAt),
                    )
                }
            }.sortedWith(
                compareBy<RoomStatusSnapshot>(
                    { statusSortRank(it.statusLabel) },
                    { it.gameName },
                    { it.roomId },
                ),
            )

    suspend fun handle(session: DefaultWebSocketServerSession) {
        var player: NetworkPlayer? = null
        var room: Room? = null
        val username = resolveUsername(session)

        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val msg =
                    runCatching {
                        Json.parseToJsonElement(frame.readText()).jsonObject
                    }.getOrNull() ?: continue
                val type = msg["type"]?.jsonPrimitive?.content

                println(msg)

                if (type?.contains("START") == true) {
                    val r = room ?: continue
                    val p = player ?: continue
                    val game = r.game ?: continue

                    if (p.playerIndex == 0 && r.players.size >= game.minPlayers && !r.started) {
                        synchronized(r) {
                            r.started = true
                            r.finished = false
                            r.updatedAt = System.currentTimeMillis()
                            r.players.forEach { _ -> game.addPlayer() }
                            game.startGame()
                        }

                        r.players.forEachIndexed { i, pl ->
                            pl.session.send(game.buildState("START", game, i))
                        }
                        broadcastRoomStatuses()
                    }
                } else if (type?.contains("CREATE") == true) {
                    val game = createGameByName(getGameStringFromType(type)) as Game
                    val (p, r) = createRoom(session, game, username)
                    player = p
                    room = r
                } else if (type == "JOIN") {
                    val (p, r) = joinGame(session, msg, player, room, username)
                    player = p
                    room = r
                } else if (type == "CHAT_SEND") {
                    handleChatMessage(session, msg, player, room)
                } else {
                    when (room?.game?.name) {
                        "Chess" -> ChessHandler.handle(msg, type, session, player, room)
                        "Go Fish" -> GoFishHandler.handle(msg, type, session, player, room)
                    }
                }
            }
        } finally {
            cleanUpRoom(room, player)
        }
    }

    fun generateRoomId(): String = (0..9999).random().toString().padStart(4, '0')

    suspend fun createRoom(
        session: DefaultWebSocketServerSession,
        game: Game,
        username: String,
    ): Pair<NetworkPlayer, Room> {
        val p = NetworkPlayer(UUID.randomUUID().toString(), session, 0, username)

        while (true) {
            val roomId = generateRoomId()
            val r =
                Room(
                    id = roomId,
                    players = mutableListOf(p),
                    game = game,
                    participantUsernames = mutableMapOf(0 to username),
                )

            if (rooms.putIfAbsent(roomId, r) == null) {
                session.send("""{"type":"ROOM_CREATED","roomId":"$roomId","playerIndex":0}""")
                session.send(buildChatHistoryMessage(r))
                broadcastRoomStatuses()
                return p to r
            }
        }
    }

    suspend fun joinGame(
        session: DefaultWebSocketServerSession,
        msg: JsonObject,
        player: NetworkPlayer?,
        room: Room?,
        username: String,
    ): Pair<NetworkPlayer?, Room?> {
        val roomId = msg["roomId"]?.jsonPrimitive?.content?.trim()
        if (roomId.isNullOrEmpty()) {
            session.send("""{"type":"JOIN_FAIL","reason":"Room code is required"}""")
            return player to room
        }
        val r = rooms[roomId]
        when {
            r == null -> {
                session.send("""{"type":"JOIN_FAIL","reason":"Room not found"}""")
            }

            else -> {
                val joinResult =
                    synchronized(r) {
                        when {
                            r.started -> {
                                JoinAttempt.Failure("Game already in progress")
                            }

                            r.players.size >= r.game!!.maxPlayers -> {
                                JoinAttempt.Failure("Room is full")
                            }

                            else -> {
                                val playerIndex = r.players.size
                                val p = NetworkPlayer(UUID.randomUUID().toString(), session, playerIndex, username)
                                r.players.add(p)
                                r.participantUsernames[playerIndex] = username
                                r.updatedAt = System.currentTimeMillis()
                                JoinAttempt.Success(p, buildChatHistoryMessage(r))
                            }
                        }
                    }

                when (joinResult) {
                    is JoinAttempt.Failure -> {
                        session.send("""{"type":"JOIN_FAIL","reason":"${joinResult.reason}"}""")
                    }

                    is JoinAttempt.Success -> {
                        val p = joinResult.player
                        val playerIndex = p.playerIndex
                        val playerCount = synchronized(r) { r.players.size }

                        session.send("""{"type":"JOIN_OK","roomId":"$roomId","playerIndex":$playerIndex}""")
                        session.send(joinResult.chatHistory)
                        broadcast(r, """{"type":"PLAYER_UPDATE","count":$playerCount}""")
                        broadcastRoomStatuses()
                        return p to r
                    }
                }
            }
        }
        return player to room
    }

    suspend fun cleanUpRoom(
        room: Room?,
        player: NetworkPlayer?,
    ) {
        room?.let { r ->
            val removedPlayer = player ?: return
            var endedByDisconnect = false
            val remainingPlayers =
                synchronized(r) {
                    r.players.removeIf { it.id == removedPlayer.id }
                    r.updatedAt = System.currentTimeMillis()
                    endedByDisconnect = r.started && !r.finished && r.players.isNotEmpty()
                    if (endedByDisconnect) {
                        r.finished = true
                    }
                    r.players.toList()
                }

            if (remainingPlayers.isEmpty()) {
                rooms.remove(r.id)
            } else if (endedByDisconnect) {
                val winner = if (remainingPlayers.size == 1) remainingPlayers.first().playerIndex else -1
                recordRoomHistory(r, winner)
                broadcast(remainingPlayers, buildPlayerLeftGameEndMessage(removedPlayer.username, remainingPlayers.size, winner))
            } else {
                broadcast(remainingPlayers, """{"type":"PLAYER_UPDATE","count":${remainingPlayers.size}}""")
            }
            broadcastRoomStatuses()
        }
    }

    private suspend fun handleChatMessage(
        session: DefaultWebSocketServerSession,
        msg: JsonObject,
        player: NetworkPlayer?,
        room: Room?,
    ) {
        val r = room ?: return
        val p = player ?: return
        val text =
            msg["text"]
                ?.jsonPrimitive
                ?.content
                ?.trim()
                ?.take(280)
                ?.takeIf { it.isNotBlank() }
                ?: return

        val chatMessage =
            synchronized(r) {
                r.updatedAt = System.currentTimeMillis()
                RoomChatMessage(author = p.username, text = text).also {
                    r.chatMessages.add(it)
                    while (r.chatMessages.size > CHAT_HISTORY_LIMIT) {
                        r.chatMessages.removeAt(0)
                    }
                }
            }

        broadcast(r, buildChatMessageMessage(chatMessage))
        session.send("""{"type":"CHAT_SENT"}""")
        broadcastRoomStatuses()
    }

    suspend fun handleStatusSubscription(session: DefaultWebSocketServerSession) {
        statusSubscribers.add(session)
        try {
            session.send(buildRoomStatusListMessage())
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
            }
        } finally {
            statusSubscribers.remove(session)
        }
    }

    suspend fun markRoomFinished(
        room: Room?,
        winner: Int = -1,
    ) {
        val targetRoom = room ?: return
        synchronized(targetRoom) {
            targetRoom.finished = true
            targetRoom.updatedAt = System.currentTimeMillis()
        }
        recordRoomHistory(targetRoom, winner)
        broadcastRoomStatuses()
    }

    suspend fun touchRoom(room: Room?) {
        val targetRoom = room ?: return
        synchronized(targetRoom) {
            targetRoom.updatedAt = System.currentTimeMillis()
        }
        broadcastRoomStatuses()
    }

    private fun resolveUsername(session: DefaultWebSocketServerSession): String {
        val token = session.call.request.cookies["AUTH_TOKEN"] ?: return fallbackUsername()
        return getUsernameByToken(token) ?: fallbackUsername()
    }

    private fun fallbackUsername(): String = "guest-${UUID.randomUUID().toString().take(8)}"

    private fun buildChatHistoryMessage(room: Room): String {
        val history =
            synchronized(room) {
                room.chatMessages.toList()
            }
        return buildJsonObject {
            put("type", "CHAT_HISTORY")
            put(
                "messages",
                buildJsonArray {
                    history.forEach { addChatMessage(it) }
                },
            )
        }.toString()
    }

    private fun buildChatMessageMessage(message: RoomChatMessage): String =
        buildJsonObject {
            put("type", "CHAT_MESSAGE")
            put(
                "message",
                buildJsonObject {
                    addChatMessage(message)
                },
            )
        }.toString()

    private fun buildRoomStatusListMessage(): String {
        val roomStatuses = getRoomStatusSnapshots()
        return buildJsonObject {
            put("type", "ROOM_STATUS_LIST")
            put(
                "rooms",
                buildJsonArray {
                    roomStatuses.forEach { room ->
                        add(
                            buildJsonObject {
                                put("roomId", room.roomId)
                                put("gameName", room.gameName)
                                put("statusLabel", room.statusLabel)
                                put("statusTone", room.statusTone)
                                put("hostUsername", room.hostUsername)
                                put("playerCount", room.playerCount)
                                put("maxPlayers", room.maxPlayers)
                                put("playerSummary", room.playerSummary)
                                put("playerNamesDisplay", room.playerNamesDisplay)
                                put("updatedAtLabel", room.updatedAtLabel)
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    internal fun buildPlayerLeftGameEndMessage(
        leavingUsername: String,
        remainingPlayerCount: Int,
        winner: Int,
    ): String {
        val message =
            if (remainingPlayerCount == 1) {
                "$leavingUsername left the room. You win by default."
            } else {
                "$leavingUsername left the room. The match has ended."
            }

        return buildJsonObject {
            put("type", "GAME_END")
            put("winner", winner)
            put("reason", "player_left")
            put("message", message)
        }.toString()
    }

    private suspend fun broadcastRoomStatuses() {
        val message = buildRoomStatusListMessage()
        val disconnected = mutableListOf<DefaultWebSocketServerSession>()
        statusSubscribers.forEach { session ->
            try {
                session.send(message)
            } catch (_: Throwable) {
                disconnected.add(session)
            }
        }
        if (disconnected.isNotEmpty()) {
            statusSubscribers.removeAll(disconnected.toSet())
        }
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addChatMessage(message: RoomChatMessage) {
        add(
            buildJsonObject {
                addChatMessage(message)
            },
        )
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.addChatMessage(message: RoomChatMessage) {
        put("author", message.author)
        put("text", message.text)
        put("sentAt", message.sentAt)
        put("sentAtLabel", formatChatTimestamp(message.sentAt))
    }

    private fun formatChatTimestamp(sentAt: Long): String =
        chatTimeFormatter.format(Instant.ofEpochMilli(sentAt).atZone(ZoneId.systemDefault()))

    private fun formatUpdatedAt(updatedAt: Long): String {
        val ageSeconds = ((System.currentTimeMillis() - updatedAt) / 1000).coerceAtLeast(0)
        return when {
            ageSeconds < 10 -> "Updated just now"
            ageSeconds < 60 -> "Updated ${ageSeconds}s ago"
            ageSeconds < 3600 -> "Updated ${ageSeconds / 60}m ago"
            else -> "Updated ${ageSeconds / 3600}h ago"
        }
    }

    private fun statusSortRank(statusLabel: String): Int =
        when (statusLabel) {
            "In Progress" -> 0
            "Waiting" -> 1
            "Finished" -> 2
            else -> 3
        }

    private fun recordRoomHistory(
        room: Room,
        winnerIndex: Int,
    ) {
        val gameResult =
            synchronized(room) {
                if (!room.started || room.historyRecorded) {
                    return
                }

                val gameName = room.game?.name ?: return
                val players =
                    room.participantUsernames
                        .toSortedMap()
                        .values
                        .map(String::trim)
                        .filter(String::isNotBlank)

                if (players.isEmpty()) {
                    return
                }

                room.historyRecorded = true
                val winner = if (winnerIndex >= 0) room.participantUsernames[winnerIndex].orEmpty() else ""
                Triple(gameName, winner, players)
            }

        recordGameResult(
            gameName = gameResult.first,
            winner = gameResult.second,
            players = gameResult.third,
        )
    }

    private sealed class JoinAttempt {
        data class Success(
            val player: NetworkPlayer,
            val chatHistory: String,
        ) : JoinAttempt()

        data class Failure(
            val reason: String,
        ) : JoinAttempt()
    }
}
