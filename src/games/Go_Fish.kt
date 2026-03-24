package com.example.games

/**
 * Go Fish card game implementation for 2–4 players.
 *
 * Rules:
 * - Each player is dealt 7 cards at the start.
 * - On their turn, a player asks another player for cards of a specific rank.
 * - If the asked player has matching cards, they hand them all over (player goes again if successful).
 * - If not, the current player draws a card ("Go Fish!") and the turn passes.
 * - When a player collects all 4 cards of a rank, they score a "book".
 * - The game ends when the deck is empty and all hands are empty.
 * - The winner has the most books.
 */
class GoFish(name: String = "Go Fish") : Game(name, 4) {

    private val deck = Deck().cards.toMutableList()
    private val hands = mutableListOf<MutableList<Card>>()
    private val bookCounts = mutableListOf<Int>()
    private var turn = 0
    /** Whether the current player got cards from their last ASK (they get another turn). */
    var lastAskSuccess: Boolean = false
        private set

    init {
        deck.shuffle()
    }

    /**
     * Adds a player slot to the game.
     * @return false if the game is already at max capacity.
     */
    override fun addPlayer(): Boolean {
        if (numPlayers >= maxPlayers) return false
        hands.add(mutableListOf())
        bookCounts.add(0)
        numPlayers++
        return true
    }

    /**
     * Deals 7 cards to each player and marks the game as started.
     */
    override fun startGame() {
        repeat(7) {
            hands.forEach { hand ->
                if (deck.isNotEmpty()) hand.add(deck.removeAt(0))
            }
        }
        started = true
    }

    /** Returns the index of the player whose turn it currently is. */
    fun currentPlayer(): Int = turn

    /** Advances to the next player's turn. */
    fun nextTurn() {
        turn = (turn + 1) % numPlayers
    }

    /**
     * The current player [from] asks player [to] for all cards with a given [rankString].
     * @param from Index of asking player.
     * @param to   Index of player being asked.
     * @param rankString Rank description (e.g. "A", "7", "Queen").
     * @return true if any cards were received; false if "Go Fish".
     */
    fun ask(from: Int, to: Int, rankString: String): Boolean {
        require(from != to) { "Cannot ask yourself" }
        require(from in hands.indices && to in hands.indices) { "Invalid player index" }

        val targetHand = hands[to]
        val matches = targetHand.filter { it.rankString() == rankString }
        return if (matches.isNotEmpty()) {
            hands[from].addAll(matches)
            targetHand.removeAll(matches.toSet())
            checkBooks(from)
            lastAskSuccess = true
            true
        } else {
            goFish(from)
            lastAskSuccess = false
            false
        }
    }

    /** Draws a card from the deck for [player] and checks for a book. */
    private fun goFish(player: Int) {
        if (deck.isNotEmpty()) {
            hands[player].add(deck.removeAt(0))
        }
        checkBooks(player)
    }

    /** Checks if [player] has collected all 4 cards of any rank and scores books accordingly. */
    private fun checkBooks(player: Int) {
        val hand = hands[player]
        val groups = hand.groupBy { it.rankString() }
        for ((_, cards) in groups) {
            if (cards.size == 4) {
                hand.removeAll(cards.toSet())
                bookCounts[player]++
            }
        }
    }

    /** Returns the cards in [player]'s hand as image URLs. */
    fun getHandUrls(player: Int): List<String> = hands[player].map { it.imageUrl() }

    /** Returns the cards in [player]'s hand as rank strings (for UI). */
    fun getHandRanks(player: Int): List<String> = hands[player].map { it.rankString() }

    /** Returns the number of books scored by [player]. */
    fun getBooks(player: Int): Int = bookCounts.getOrElse(player) { 0 }

    /** Returns the number of cards remaining in [player]'s hand. */
    fun getHandSize(player: Int): Int = hands.getOrElse(player) { emptyList<Card>() }.size

    /** Returns the number of cards left in the deck. */
    fun deckSize(): Int = deck.size

    /**
     * The game is over when the deck is empty and no player has any cards left.
     */
    override fun isGameOver(): Boolean = deck.isEmpty() && hands.all { it.isEmpty() }

    /**
     * Returns the index of the player with the most books.
     * Returns -1 on a tie (multiple players tied for first).
     */
    override fun getWinner(): Int {
        val maxBooks = bookCounts.maxOrNull() ?: 0
        val winners = bookCounts.indices.filter { bookCounts[it] == maxBooks }
        return if (winners.size == 1) winners.first() else -1
    }

    /**
     * Returns a sanitised game state map for [playerIndex].
     * The player's own hand is shown fully; other players only show card count.
     */
    override fun getState(playerIndex: Int): Map<String, Any?> = mapOf(
        "turn" to turn,
        "deckSize" to deck.size,
        "myHand" to if (playerIndex >= 0) getHandUrls(playerIndex) else emptyList<String>(),
        "myHandRanks" to if (playerIndex >= 0) getHandRanks(playerIndex) else emptyList<String>(),
        "books" to bookCounts.toList(),
        "handSizes" to hands.map { it.size },
        "gameOver" to isGameOver(),
        "winner" to if (isGameOver()) getWinner() else null
    )
}