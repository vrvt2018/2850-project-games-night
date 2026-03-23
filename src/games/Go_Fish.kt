package com.example.games

class GoFish(name: String = "Go Fish") : Game(name, 4) {

    private val deck = Deck().cards.toMutableList()
    private val players = mutableListOf<MutableList<Card>>()
    private val books = mutableListOf<Int>()
    private var turn = 0

    init {
        deck.shuffle()
    }

    fun addPlayer() {
        players.add(mutableListOf())
    }

    fun startGame() {
        repeat(7) {
            players.forEach { hand ->
                if (deck.isNotEmpty()) {
                    hand.add(deck.removeAt(0))
                }
            }
        }
    }

    fun currentPlayer(): Int {
        return turn
    }

    fun nextTurn() {
        turn = (turn + 1) % players.size
    }

    fun ask(from: Int, to: Int, rank: Any): Boolean {
        val targetHand = players[to]
        val matches = targetHand.filter { it.rank.toString() == rank.toString() }
        return if (matches.isNotEmpty()) {
            players[from].addAll(matches)
            targetHand.removeAll(matches)
            checkBook(from)
            true
        } else {
            goFish(from)
            false
        }
    }

    private fun goFish(player: Int) {
        if (deck.isNotEmpty()) {
            val card = deck.removeAt(0)
            players[player].add(card)
        }
        checkBook(player)
    }

    private fun checkBook(player: Int) {
        val hand = players[player]
        val groups = hand.groupBy { it.rank }
        for ((rank, cards) in groups) {
            if (cards.size == 4) {
                hand.removeAll(cards)
                books.add(player)
            }
        }
    }

    fun getHand(player: Int): List<Card> {
        return players[player]
    }

    fun isGameOver(): Boolean {
        return deck.isEmpty() && players.all { it.isEmpty() }
    }

    fun getWinner(): Int {
    val scores = players.map { hand ->
        hand.groupBy { it.rank }.count { it.value.size == 4 }
    }
    return scores.indices.maxByOrNull { scores[it] } ?: 0
}
}