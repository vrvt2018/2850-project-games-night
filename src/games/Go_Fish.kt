//// AI-assisted: Game engine structure, book-tracking logic, and state serialisation (Gemini)
//// Go Fish game logic: manages hands, card asking, book collection, and turn flow
//package com.example.games
//
///**
// * Go Fish card game engine.
// *
// * Supports 2-4 players. Players ask each other for cards of a particular rank.
// * When a player collects all 4 cards of a rank, they form a "book".
// * The game ends when all 13 books have been formed or the deck is empty and no
// * player can make a move.
// */
//class GoFish : Game("Go Fish", maxPlayers = 4) {
//
//    private val deck = Deck()
//    private val drawPile = mutableListOf<Card>()
//    private val hands = mutableListOf<MutableList<Card>>()    // hands[playerIndex]
//    private val books = mutableListOf<Int>()                   // books[playerIndex] = count
//    private var turn: Int = 0
//    private var winner: Int = -2  // -2 = not over, -1 = draw
//
//    override fun addPlayer(): Boolean {
//        if (numPlayers >= maxPlayers) return false
//        numPlayers++
//        return true
//    }
//
//    override fun startGame() {
//        // Shuffle and create draw pile
//        drawPile.clear()
//        drawPile.addAll(deck.cards.toList().shuffled())
//
//        // Initialize hands and books
//        hands.clear()
//        books.clear()
//        val cardsPerPlayer = if (numPlayers <= 3) 7 else 5
//        for (i in 0 until numPlayers) {
//            val hand = mutableListOf<Card>()
//            repeat(cardsPerPlayer) {
//                if (drawPile.isNotEmpty()) hand.add(drawPile.removeFirst())
//            }
//            hands.add(hand)
//            books.add(0)
//        }
//
//        turn = 0
//        winner = -2
//        started = true
//    }
//
//    /**
//     * Current player asks [targetPlayer] for cards of [rank].
//     * @return true if the target had matching cards (player gets another turn).
//     */
//    fun askForCard(targetPlayer: Int, rank: String): Boolean {
//        if (targetPlayer < 0 || targetPlayer >= numPlayers || targetPlayer == turn) return false
//
//        val targetHand = hands[targetPlayer]
//        val matching = targetHand.filter { it.rankString() == rank }
//
//        return if (matching.isNotEmpty()) {
//            // Transfer cards from target to current player
//            targetHand.removeAll(matching)
//            hands[turn].addAll(matching)
//            checkBooks(turn)
//            true // Player gets another turn
//        } else {
//            // "Go Fish" - draw a card from the pile
//            if (drawPile.isNotEmpty()) {
//                hands[turn].add(drawPile.removeFirst())
//                checkBooks(turn)
//            }
//            false // Turn ends
//        }
//    }
//
//    /**
//     * Advances to the next player's turn. Called after a failed ask (Go Fish).
//     */
//    fun endTurn() {
//        // Skip players with empty hands (if draw pile also empty)
//        var attempts = 0
//        do {
//            turn = (turn + 1) % numPlayers
//            attempts++
//        } while (hands[turn].isEmpty() && drawPile.isEmpty() && attempts < numPlayers)
//
//        checkGameOver()
//    }
//
//    /**
//     * Checks if the player at [playerIndex] has completed any books (4 of a rank).
//     */
//    private fun checkBooks(playerIndex: Int) {
//        val hand = hands[playerIndex]
//        val rankGroups = hand.groupBy { it.rankString() }
//        for ((rank, cards) in rankGroups) {
//            if (cards.size == 4) {
//                hand.removeAll { it.rankString() == rank }
//                books[playerIndex]++
//            }
//        }
//    }
//
//    private fun checkGameOver() {
//        val totalBooks = books.sum()
//        if (totalBooks >= 13) {
//            // All books formed
//            winner = books.indexOf(books.max())
//            return
//        }
//        // Check if all hands are empty and draw pile is empty
//        if (drawPile.isEmpty() && hands.all { it.isEmpty() }) {
//            winner = if (books.max() > 0) books.indexOf(books.max()) else -1
//        }
//    }
//
//    fun currentPlayer(): Int = turn
//
//    override fun isGameOver(): Boolean = winner != -2
//
//    override fun getWinner(): Int = winner
//
//    override fun getState(playerIndex: Int): Map<String, Any?> {
//        val myHand = if (playerIndex in 0 until numPlayers) {
//            hands[playerIndex].map { it.imageUrl() }
//        } else emptyList()
//
//        val myHandRanks = if (playerIndex in 0 until numPlayers) {
//            hands[playerIndex].map { it.rankString() }
//        } else emptyList()
//
//        return mapOf(
//            "turn" to turn,
//            "myHand" to myHand,
//            "myHandRanks" to myHandRanks,
//            "handSizes" to hands.map { it.size },
//            "books" to books.toList(),
//            "deckSize" to drawPile.size,
//            "gameOver" to isGameOver(),
//            "winner" to if (isGameOver()) winner else null,
//            "playerIndex" to playerIndex,
//            "numPlayers" to numPlayers
//        )
//    }
//}
