package com.example.games

/**
 * Represents the four suits of a standard playing card deck.
 */
enum class Suit { Hearts, Diamonds, Spades, Clubs }

/**
 * Represents face/picture cards that have a name rather than a number.
 */
enum class PictureCard { Jack, Queen, King }

/**
 * Represents a single playing card.
 *
 * @param suit The suit of the card (Hearts, Diamonds, Spades, Clubs).
 * @param _rank Internal integer rank (1–13). 1=Ace, 11=Jack, 12=Queen, 13=King.
 */
class Card(val suit: Suit, private val _rank: Int) {

    /**
     * The rank of this card: an Int (1–10) for numbered cards, or a [PictureCard] for face cards.
     * Ace is represented as 1.
     */
    val rank: Any
        get() = if (_rank in 1..10) _rank else PictureCard.entries[_rank - 11]

    /**
     * Human-readable rank string for WebSocket messaging (e.g. "A", "Jack", "7").
     */
    fun rankString(): String = when (_rank) {
        1 -> "A"
        11 -> "Jack"
        12 -> "Queen"
        13 -> "King"
        else -> _rank.toString()
    }

    /**
     * Returns the URL path to this card's image asset.
     * Expects card images at /resources/assets/cards/<rank>_of_<suit>.svg
     */
    fun imageUrl(): String {
        val rankPart = when (_rank) {
            1 -> "ace"
            11 -> "jack"
            12 -> "queen"
            13 -> "king"
            else -> _rank.toString()
        }
        return "/resources/assets/cards/${rankPart}_of_${suit.name.lowercase()}.svg"
    }

    override fun toString(): String = "${rankString()} of ${suit.name}"
}

/**
 * A standard 52-card deck.
 * Cards are ordered: Ace through King for each suit (Hearts, Diamonds, Spades, Clubs).
 */
class Deck {
    /** All 52 cards in the deck. Shuffle before dealing. */
    val cards: Array<Card> = Array(52) { Card(Suit.entries[it / 13], it % 13 + 1) }
}