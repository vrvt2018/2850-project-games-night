package com.example.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckTest {
    @Test
    fun testDeckContains52Cards() {
        val deck = Deck()
        assertEquals(52, deck.cards.size)
    }

    @Test
    fun testCardRankStrings() {
        val deck = Deck()
        assertEquals("A", deck.cards[0].rankString()) // Ace
        assertEquals("2", deck.cards[1].rankString())
        assertEquals("10", deck.cards[9].rankString())
        assertEquals("Jack", deck.cards[10].rankString())
        assertEquals("Queen", deck.cards[11].rankString())
        assertEquals("King", deck.cards[12].rankString())
    }

    @Test
    fun testCardToString() {
        val card = Card(Suit.Hearts, 1)
        assertEquals("A of Hearts", card.toString())
    }

    @Test
    fun testImageUrl() {
        val card = Card(Suit.Spades, 1)
        assertTrue(card.imageUrl().contains("ace_of_spades"))
        assertTrue(card.imageUrl().endsWith(".png"))
    }
}
