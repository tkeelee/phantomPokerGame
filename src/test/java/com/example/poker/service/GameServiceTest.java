package com.example.poker.service;

import com.example.poker.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private RoomManagementService roomManagementService;

    private String roomId;
    private String player1Id;
    private String player2Id;

    @BeforeEach
    void setUp() {
        player1Id = "player1";
        player2Id = "player2";
        GameRoom room = roomManagementService.createRoom(player1Id, 2);
        roomId = room.getId();
        roomManagementService.joinRoom(roomId, player2Id);
    }

    @Test
    void testCreateRoom() {
        GameRoom room = roomManagementService.createRoom("host", 2);
        assertNotNull(room);
        assertNotNull(room.getId());
        assertEquals("host", room.getHostId());
        assertEquals(2, room.getMaxPlayers());
        assertEquals(GameStatus.WAITING, room.getStatus());
    }

    @Test
    void testJoinRoom() {
        GameRoom room = roomManagementService.joinRoom(roomId, "player3");
        assertNotNull(room);
        assertTrue(room.getPlayers().contains("player3"));
    }

    @Test
    void testLeaveRoom() {
        roomManagementService.leaveRoom(roomId, player2Id);
        GameRoom room = roomManagementService.getRoom(roomId);
        assertFalse(room.getPlayers().contains(player2Id));
    }

    @Test
    void testPlayerReady() {
        GameRoom room = gameService.playerReady(roomId, player2Id);
        assertNotNull(room);
        // 验证玩家准备状态
    }

    @Test
    void testPlayCards() {
        GameMessage message = new GameMessage();
        message.setType("PLAY");
        message.setRoomId(roomId);
        message.setPlayerId(player1Id);
        message.setClaim("1 1");
        message.setCards(List.of(new Card(Card.Suit.SPADES, 1)));
        message.setDeclaredCount(1);
        message.setDeclaredValue("1");

        GameRoom room = gameService.playCards(roomId, message);
        assertNotNull(room);
        // 验证出牌结果
    }

    @Test
    void testChallenge() {
        GameMessage message = new GameMessage();
        message.setType("CHALLENGE");
        message.setRoomId(roomId);
        message.setPlayerId(player2Id);
        message.setTargetPlayerId(player1Id);

        GameRoom room = gameService.challenge(roomId, message);
        assertNotNull(room);
        // 验证质疑结果
    }

    @Test
    void testPass() {
        GameRoom room = gameService.pass(roomId, player2Id);
        assertNotNull(room);
        // 验证过牌结果
    }

    @Test
    void testGetRoom() {
        GameRoom room = roomManagementService.getRoom(roomId);
        assertNotNull(room);
        assertEquals(roomId, room.getId());
    }

    @Test
    void testGetGameState() {
        GameState state = roomManagementService.getGameState(roomId);
        assertNotNull(state);
        assertEquals(roomId, state.getRoomId());
    }

    @Test
    void testGetPlayers() {
        List<PlayerState> players = gameService.getPlayers(roomId);
        assertNotNull(players);
        assertEquals(2, players.size());
    }
} 