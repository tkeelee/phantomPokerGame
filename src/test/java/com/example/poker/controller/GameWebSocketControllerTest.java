package com.example.poker.controller;

import com.example.poker.model.*;
import com.example.poker.service.RoomManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketControllerTest {

    @Autowired
    private RoomManagementService roomManagementService;


    private WebSocketStompClient stompClient;
    private CompletableFuture<GameState> gameStateFuture;
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

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        gameStateFuture = new CompletableFuture<>();
    }

    @Test
    void testJoinGame() throws Exception {
        StompSession session = stompClient.connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {}).get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/game/state", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameState.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                gameStateFuture.complete((GameState) payload);
            }
        });

        GameMessage message = new GameMessage();
        message.setType("JOIN");
        message.setRoomId(roomId);
        message.setPlayerId("player3");

        session.send("/app/game/action", message);

        GameState state = gameStateFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(state);
        assertTrue(state.getPlayers().contains("player3"));
    }

    @Test
    void testPlayCards() throws Exception {
        StompSession session = stompClient.connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {}).get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/game/state", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameState.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                gameStateFuture.complete((GameState) payload);
            }
        });

        GameMessage message = new GameMessage();
        message.setType("PLAY");
        message.setRoomId(roomId);
        message.setPlayerId(player1Id);
        message.setClaim("1 1");
        message.setDeclaredCount(1);
        message.setDeclaredValue("1");

        session.send("/app/game/action", message);

        GameState state = gameStateFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(state);
        assertEquals(roomId, state.getRoomId());
    }

    @Test
    void testChallenge() throws Exception {
        StompSession session = stompClient.connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {}).get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/game/state", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameState.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                gameStateFuture.complete((GameState) payload);
            }
        });

        GameMessage message = new GameMessage();
        message.setType("CHALLENGE");
        message.setRoomId(roomId);
        message.setPlayerId(player2Id);
        message.setTargetPlayerId(player1Id);

        session.send("/app/game/action", message);

        GameState state = gameStateFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(state);
        assertEquals(roomId, state.getRoomId());
    }

    @Test
    void testPass() throws Exception {
        StompSession session = stompClient.connect("ws://localhost:8080/ws", new StompSessionHandlerAdapter() {}).get(1, TimeUnit.SECONDS);

        session.subscribe("/topic/game/state", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameState.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                gameStateFuture.complete((GameState) payload);
            }
        });

        GameMessage message = new GameMessage();
        message.setType("PASS");
        message.setRoomId(roomId);
        message.setPlayerId(player2Id);

        session.send("/app/game/action", message);

        GameState state = gameStateFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(state);
        assertEquals(roomId, state.getRoomId());
    }
} 