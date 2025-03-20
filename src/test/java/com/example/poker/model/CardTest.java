package com.example.poker.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CardTest {

    @Test
    void testCardCreation() {
        Card card = new Card(Card.Suit.SPADES, 1);
        assertEquals(Card.Suit.SPADES, card.getSuit());
        assertEquals(1, card.getValue());
        assertFalse(card.isJoker());
    }

    @Test
    void testJokerCreation() {
        Card bigJoker = new Card(true);
        Card smallJoker = new Card(false);

        assertNull(bigJoker.getSuit());
        assertNull(smallJoker.getSuit());
        assertEquals(16, bigJoker.getValue());
        assertEquals(15, smallJoker.getValue());
        assertTrue(bigJoker.isJoker());
        assertTrue(smallJoker.isJoker());
    }

    @Test
    void testCardFromString() {
        Card card = Card.fromString("♠1");
        assertNotNull(card);
        assertEquals(Card.Suit.SPADES, card.getSuit());
        assertEquals(1, card.getValue());
        assertFalse(card.isJoker());

        Card bigJoker = Card.fromString("大王");
        assertNotNull(bigJoker);
        assertNull(bigJoker.getSuit());
        assertEquals(16, bigJoker.getValue());
        assertTrue(bigJoker.isJoker());

        Card smallJoker = Card.fromString("小王");
        assertNotNull(smallJoker);
        assertNull(smallJoker.getSuit());
        assertEquals(15, smallJoker.getValue());
        assertTrue(smallJoker.isJoker());

        assertNull(Card.fromString("invalid"));
        assertNull(Card.fromString("♠"));
        assertNull(Card.fromString("1"));
    }

    @Test
    void testCardToString() {
        Card card = new Card(Card.Suit.SPADES, 1);
        assertEquals("♠1", card.toString());

        Card bigJoker = new Card(true);
        assertEquals("大王", bigJoker.toString());

        Card smallJoker = new Card(false);
        assertEquals("小王", smallJoker.toString());
    }

    @Test
    void testCardEquals() {
        Card card1 = new Card(Card.Suit.SPADES, 1);
        Card card2 = new Card(Card.Suit.SPADES, 1);
        Card card3 = new Card(Card.Suit.HEARTS, 1);
        Card card4 = new Card(Card.Suit.SPADES, 2);

        assertEquals(card1, card2);
        assertNotEquals(card1, card3);
        assertNotEquals(card1, card4);

        Card bigJoker1 = new Card(true);
        Card bigJoker2 = new Card(true);
        Card smallJoker = new Card(false);

        assertEquals(bigJoker1, bigJoker2);
        assertNotEquals(bigJoker1, smallJoker);
    }

    @Test
    void testCardHashCode() {
        Card card1 = new Card(Card.Suit.SPADES, 1);
        Card card2 = new Card(Card.Suit.SPADES, 1);
        Card card3 = new Card(Card.Suit.HEARTS, 1);

        assertEquals(card1.hashCode(), card2.hashCode());
        assertNotEquals(card1.hashCode(), card3.hashCode());

        Card bigJoker1 = new Card(true);
        Card bigJoker2 = new Card(true);
        Card smallJoker = new Card(false);

        assertEquals(bigJoker1.hashCode(), bigJoker2.hashCode());
        assertNotEquals(bigJoker1.hashCode(), smallJoker.hashCode());
    }

    @Test
    void testCardGetRank() {
        Card card = new Card(Card.Suit.SPADES, 1);
        assertEquals("1", card.getRank());

        Card bigJoker = new Card(true);
        assertEquals("大王", bigJoker.getRank());

        Card smallJoker = new Card(false);
        assertEquals("小王", smallJoker.getRank());
    }
} 