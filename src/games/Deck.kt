package com.example.games

enum class Suit
{
    Hearts,
    Diamonds,
    Spades,
    Clubs
}



class Deck {
    // Initialise cards as full deck of cards
    var cards = Array<Card>(52) {
        Card(Suit.entries[it/13], it%13+1)
    }
}

enum class PictureCard
{
    Jack,
    Queen,
    King
}

class Card(val suit : Suit, private val _rank : Int)
{
    val rank : Any
        get()
    {
        if(_rank == 1) return "A"
        if(_rank in 2..10) return _rank
        else return PictureCard.entries[_rank - 11] // If picture card, return its name from enum
    }

    fun imageUrl(): String {
        val r = when (_rank) {
            1 -> "ace"
            11 -> "jack"
            12 -> "queen"
            13 -> "king"
            else -> _rank.toString()
        }
        val s = suit.name.lowercase()
        // Assuming images are named rank_of_suit.png in a cards folder or external
        return "https://raw.githubusercontent.com/hayeah/playing-cards-assets/master/png/${r}_of_${s}.png" 
    }

    override fun toString() : String
    {
        return "${rank.toString()} of ${suit.toString()}"
    }
}