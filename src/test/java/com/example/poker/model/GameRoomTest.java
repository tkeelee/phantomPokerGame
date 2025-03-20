package com.example.poker.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameRoomTest {

    private GameRoom gameRoom;
    private String player1Id;
    private String player2Id;

    @BeforeEach
    void setUp() {
        gameRoom = new GameRoom();
        player1Id = "player1";
        player2Id = "player2";
    }

    @Test
    void testGameRoomCreation() {
        assertNotNull(gameRoom.getId());
        assertNotNull(gameRoom.getPlayers());
        assertNotNull(gameRoom.getCardDeck());
        assertNotNull(gameRoom.getPlayerHands());
        assertNotNull(gameRoom.getCurrentPile());
        assertEquals(GameStatus.WAITING, gameRoom.getStatus());
        assertEquals(0, gameRoom.getCurrentPlayerIndex());
    }

    @Test
    void testSetAndGetId() {
        String id = "test-room-id";
        gameRoom.setId(id);
        assertEquals(id, gameRoom.getId());
    }

    @Test
    void testSetAndGetHostId() {
        String hostId = "host-player";
        gameRoom.setHostId(hostId);
        assertEquals(hostId, gameRoom.getHostId());
    }

    @Test
    void testSetAndGetMaxPlayers() {
        int maxPlayers = 4;
        gameRoom.setMaxPlayers(maxPlayers);
        assertEquals(maxPlayers, gameRoom.getMaxPlayers());
    }

    @Test
    void testSetAndGetStatus() {
        gameRoom.setStatus(GameStatus.PLAYING);
        assertEquals(GameStatus.PLAYING, gameRoom.getStatus());
    }

    @Test
    void testSetAndGetPlayers() {
        List<String> players = Arrays.asList(player1Id, player2Id);
        gameRoom.setPlayers(players);
        assertEquals(players, gameRoom.getPlayers());
    }

    @Test
    void testSetAndGetCurrentPlayerIndex() {
        int currentPlayerIndex = 1;
        gameRoom.setCurrentPlayerIndex(currentPlayerIndex);
        assertEquals(currentPlayerIndex, gameRoom.getCurrentPlayerIndex());
    }

    @Test
    void testSetAndGetCardDeck() {
        List<Card> cardDeck = Arrays.asList(
            new Card(Card.Suit.SPADES, 1),
            new Card(Card.Suit.HEARTS, 2)
        );
        gameRoom.setCardDeck(cardDeck);
        assertEquals(cardDeck, gameRoom.getCardDeck());
    }

    @Test
    void testSetAndGetPlayerHands() {
        List<Card> player1Hand = Arrays.asList(
            new Card(Card.Suit.SPADES, 1),
            new Card(Card.Suit.HEARTS, 2)
        );
        List<Card> player2Hand = Arrays.asList(
            new Card(Card.Suit.DIAMONDS, 3),
            new Card(Card.Suit.CLUBS, 4)
        );
        gameRoom.getPlayerHands().put(player1Id, player1Hand);
        gameRoom.getPlayerHands().put(player2Id, player2Hand);

        assertEquals(player1Hand, gameRoom.getPlayerHands().get(player1Id));
        assertEquals(player2Hand, gameRoom.getPlayerHands().get(player2Id));
    }

    @Test
    void testSetAndGetLastClaim() {
        String lastClaim = "2 2";
        gameRoom.setLastClaim(lastClaim);
        assertEquals(lastClaim, gameRoom.getLastClaim());
    }

    @Test
    void testSetAndGetCurrentPile() {
        List<Card> currentPile = Arrays.asList(
            new Card(Card.Suit.SPADES, 1),
            new Card(Card.Suit.HEARTS, 2)
        );
        gameRoom.setCurrentPile(currentPile);
        assertEquals(currentPile, gameRoom.getCurrentPile());
    }

    @Test
    void testCheckGameEnd() {
        // 设置玩家手牌
        gameRoom.getPlayerHands().put(player1Id, Arrays.asList());
        gameRoom.getPlayerHands().put(player2Id, Arrays.asList(
            new Card(Card.Suit.SPADES, 1)
        ));

        assertTrue(gameRoom.checkGameEnd());

        // 重置玩家手牌
        gameRoom.getPlayerHands().put(player1Id, Arrays.asList(
            new Card(Card.Suit.SPADES, 1)
        ));
        gameRoom.getPlayerHands().put(player2Id, Arrays.asList(
            new Card(Card.Suit.HEARTS, 2)
        ));

        assertFalse(gameRoom.checkGameEnd());
    }
} 