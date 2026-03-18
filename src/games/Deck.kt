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
        if(_rank in 1..10)
            return _rank
        else
            return PictureCard.entries[_rank - 11] // If picture card, return its name from enum
    }

    override fun toString() : String
    {
        return "${rank.toString()} of ${suit.toString()}"
    }
}