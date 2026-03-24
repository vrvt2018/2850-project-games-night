package com.example.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class DeckTest {
    @Test
    fun testDeckContains52Cards() {
        val deck = Deck()
        assertEquals(52, deck.cards.size)
    }

    @Test
    fun testCardRankStrings() {
        val ace = Card(Suit.Spades, 1)
        assertEquals("A", ace.rankString())
        
        val ten = Card(Suit.Hearts, 10)
        assertEquals("10", ten.rankString())

        val king = Card(Suit.Clubs, 13)
        assertEquals("King", king.rankString())
    }

    @Test
    fun testCardToString() {
        val card = Card(Suit.Diamonds, 12)
        assertEquals("Queen of Diamonds", card.toString())
    }

    @Test
    fun testImageUrl() {
        val card = Card(Suit.Hearts, 11)
        assertEquals("/resources/assets/cards/jack_of_hearts.svg", card.imageUrl())
    }
}
