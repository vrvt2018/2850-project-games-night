package com.example.games

/**
* Go Fish card game engine.
*
* Supports 2-4 players. Players ask each other for cards of a particular rank.
* When a player collects all 4 cards of a rank, they form a "book".
* The game ends when all 13 books have been formed or the deck is empty and no
* player can make a move.
*/

call.respond(
    PebbleContent(
        "gofish.peb",
        mapOf(
            "title" to "Go Fish",
            "maxPlayers" to 4
        )
    )
)

class GoFish : Game("Go Fish", 4, 2) {
    private val deck = Deck()
    private val drawPile = mutableListOf<Card>()
    private val hands = mutableListOf<MutableList<Card>>()
    private val books = mutableListOf<Int>()

    private var turn: Int = 0
    private var winner: Int = -2

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

        turn = 0
        winner = -2
    }

    fun currentPlayer(): Int = turn

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
            append(""","books":[$books]""")
            append(""","handSizes":[$handSizes]""")
            append(""","myHand":[$myHand]""")
            append(""","myHandRanks":[$myHandRanks]}""")
        }
    }

    fun askForCard(
        targetPlayer: Int,
        rank: String,
    ): Boolean {
        if (isGameOver()) return false
        if (targetPlayer !in 0 until numPlayers || targetPlayer == turn) return false

        val currentHand = hands[turn]
        if (currentHand.none { it.rankString() == rank }) return false

        val targetHand = hands[targetPlayer]
        val matching = targetHand.filter { it.rankString() == rank }

        return if (matching.isNotEmpty()) {
            targetHand.removeAll(matching)
            hands[turn].addAll(matching)
            checkBooks(turn)
            true
        } else {
            if (drawPile.isNotEmpty()) {
                hands[turn].add(drawPile.removeFirst())
                checkBooks(turn)
            }
            false
        }
    }

    fun endTurn() {
        if (isGameOver()) return

        var attempts = 0
        do {
            turn = (turn + 1) % numPlayers
            attempts++
        } while (
            hands[turn].isEmpty() &&
            drawPile.isEmpty() &&
            attempts < numPlayers
        )

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
            winner = books.indices.maxByOrNull { books[it] } ?: -1
            return
        }

        if (drawPile.isEmpty() && hands.all { it.isEmpty() }) {
            val max = books.maxOrNull() ?: 0
            winner = if (max > 0) books.indexOf(max) else -1
        }
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
            "books" to books.toList(),
            "handSizes" to hands.map { it.size },
            "myHand" to myHand,
            "myHandRanks" to myHandRanks,
        )
    }
}
