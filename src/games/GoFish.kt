package com.example.games

/**
* Go Fish card game engine.
*
* Supports 2-4 players. Players ask each other for cards of a particular rank.
* When a player collects all 4 cards of a rank, they form a "book".
* The game ends when all 13 books have been formed or the deck is empty and no
* player can make a move.
*/

class GoFish : Game("Go Fish", 4, 2) {
    enum class AskOutcome {
        HIT,
        MISS,
        INVALID,
    }

    private val deck = Deck()
    private val drawPile = mutableListOf<Card>()
    private val hands = mutableListOf<MutableList<Card>>()
    private val books = mutableListOf<Int>()

    private var turn: Int = 0
    private var winner: Int = -2
    private var mustEndTurn: Boolean = false

    override fun addPlayer(): Boolean {
        if (numPlayers >= maxPlayers) return false
        numPlayers++
        return true
    }

    override fun startGame() {
        started = true
        drawPile.clear()
        drawPile.addAll(deck.cards.toList().shuffled())

        hands.clear()
        books.clear()

        val cardsPerPlayer = if (numPlayers <= 3) 7 else 5

        repeat(numPlayers) {
            val hand = mutableListOf<Card>()
            repeat(cardsPerPlayer) {
                if (drawPile.isNotEmpty()) {
                    hand.add(drawPile.removeFirst())
                }
            }
            hands.add(hand)
            books.add(0)
        }
        repeat(numPlayers) { checkBooks(it) }

        turn = 0
        winner = -2
        mustEndTurn = false
        drawIfHandIsEmpty(turn)
    }

    fun currentPlayer(): Int = turn

    fun isWaitingForEndTurn(): Boolean = mustEndTurn

    override fun buildState(
        type: String,
        game: Game,
        playerIndex: Int,
    ): String {
        val state = game.getState(playerIndex)
        val myHand = (state["myHand"] as List<*>).joinToString("\",\"", "\"", "\"")
        val myHandRanks = (state["myHandRanks"] as List<*>).joinToString("\",\"", "\"", "\"")
        val books = (state["books"] as List<*>).joinToString(",")
        val handSizes = (state["handSizes"] as List<*>).joinToString(",")

        return buildString {
            append("""{"type":"$type"""")
            append(""","turn":${state["turn"]}""")
            append(""","deckSize":${state["deckSize"]}""")
            append(""","numPlayers":${state["numPlayers"]}""")
            append(""","gameOver":${state["gameOver"]}""")
            append(""","winner":${state["winner"] ?: "null"}""")
            append(""","mustEndTurn":${state["mustEndTurn"]}""")
            append(""","books":[$books]""")
            append(""","handSizes":[$handSizes]""")
            append(""","myHand":[$myHand]""")
            append(""","myHandRanks":[$myHandRanks]}""")
        }
    }

    fun askForCard(
        targetPlayer: Int,
        rank: String,
    ): Boolean = askForCardOutcome(targetPlayer, rank) == AskOutcome.HIT

    fun askForCardOutcome(
        targetPlayer: Int,
        rank: String,
    ): AskOutcome {
        if (isGameOver()) return AskOutcome.INVALID
        if (mustEndTurn) return AskOutcome.INVALID
        if (targetPlayer !in 0 until numPlayers || targetPlayer == turn) return AskOutcome.INVALID

        val currentHand = hands[turn]
        if (currentHand.none { it.rankString() == rank }) return AskOutcome.INVALID

        val targetHand = hands[targetPlayer]
        if (targetHand.isEmpty()) return AskOutcome.INVALID

        val matching = targetHand.filter { it.rankString() == rank }

        return if (matching.isNotEmpty()) {
            targetHand.removeAll(matching)
            hands[turn].addAll(matching)
            checkBooks(turn)
            drawIfHandIsEmpty(turn)
            checkGameOver()
            AskOutcome.HIT
        } else {
            if (drawPile.isNotEmpty()) {
                hands[turn].add(drawPile.removeFirst())
                checkBooks(turn)
            }
            mustEndTurn = true
            checkGameOver()
            AskOutcome.MISS
        }
    }

    fun endTurn() {
        if (isGameOver()) return
        mustEndTurn = false

        var attempts = 0
        do {
            turn = (turn + 1) % numPlayers
            attempts++
        } while (
            hands[turn].isEmpty() &&
            drawPile.isEmpty() &&
            attempts < numPlayers
        )

        drawIfHandIsEmpty(turn)
        checkGameOver()
    }

    private fun checkBooks(playerIndex: Int) {
        val hand = hands[playerIndex]
        val grouped = hand.groupBy { it.rankString() }

        grouped.forEach { (rank, cards) ->
            if (cards.size == 4) {
                hand.removeAll { it.rankString() == rank }
                books[playerIndex]++
            }
        }
    }

    private fun checkGameOver() {
        val totalBooks = books.sum()

        if (totalBooks >= 13) {
            winner = determineWinner()
            return
        }

        if (drawPile.isEmpty() && activePlayerCount() < 2) {
            winner = determineWinner()
        }
    }

    private fun drawIfHandIsEmpty(playerIndex: Int) {
        if (playerIndex in hands.indices && hands[playerIndex].isEmpty() && drawPile.isNotEmpty()) {
            hands[playerIndex].add(drawPile.removeFirst())
            checkBooks(playerIndex)
        }
    }

    private fun activePlayerCount(): Int = hands.count { it.isNotEmpty() }

    private fun determineWinner(): Int {
        val max = books.maxOrNull() ?: 0
        if (max == 0) return -1
        val leaders = books.withIndex().filter { it.value == max }
        return if (leaders.size == 1) leaders.first().index else -1
    }

    override fun isGameOver(): Boolean = winner != -2

    override fun getWinner(): Int = winner

    override fun getState(playerIndex: Int): Map<String, Any?> {
        val myHand = hands.getOrNull(playerIndex)?.map { it.imageUrl() } ?: emptyList()
        val myHandRanks = hands.getOrNull(playerIndex)?.map { it.rankString() } ?: emptyList()
        return mapOf(
            "turn" to turn,
            "deckSize" to drawPile.size,
            "numPlayers" to numPlayers,
            "gameOver" to isGameOver(),
            "winner" to if (isGameOver()) winner else null,
            "mustEndTurn" to mustEndTurn,
            "books" to books.toList(),
            "handSizes" to hands.map { it.size },
            "myHand" to myHand,
            "myHandRanks" to myHandRanks,
        )
    }
}
