package com.example.poker.controller;

import com.example.poker.model.*;
import com.example.poker.service.RoomManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomManagementService roomManagementService;

    @Autowired
    private ObjectMapper objectMapper;

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
    void testCreateRoom() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("hostId", "host");
        request.put("maxPlayers", 2);

        mockMvc.perform(post("/api/game/room")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostId").value("host"))
                .andExpect(jsonPath("$.maxPlayers").value(2));
    }

    @Test
    void testJoinRoom() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("playerId", "player3");

        mockMvc.perform(post("/api/game/room/{roomId}/join", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players").isArray())
                .andExpect(jsonPath("$.players[2]").value("player3"));
    }

    @Test
    void testLeaveRoom() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("playerId", player2Id);

        mockMvc.perform(post("/api/game/room/{roomId}/leave", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testPlayerReady() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("playerId", player2Id);

        mockMvc.perform(post("/api/game/room/{roomId}/ready", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testPlayCards() throws Exception {
        GameMessage message = new GameMessage();
        message.setType("PLAY");
        message.setRoomId(roomId);
        message.setPlayerId(player1Id);
        message.setClaim("1 1");
        message.setDeclaredCount(1);
        message.setDeclaredValue("1");

        mockMvc.perform(post("/api/game/room/{roomId}/play", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(message)))
                .andExpect(status().isOk());
    }

    @Test
    void testChallenge() throws Exception {
        GameMessage message = new GameMessage();
        message.setType("CHALLENGE");
        message.setRoomId(roomId);
        message.setPlayerId(player2Id);
        message.setTargetPlayerId(player1Id);

        mockMvc.perform(post("/api/game/room/{roomId}/challenge", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(message)))
                .andExpect(status().isOk());
    }

    @Test
    void testPass() throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("playerId", player2Id);

        mockMvc.perform(post("/api/game/room/{roomId}/pass", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetRoom() throws Exception {
        mockMvc.perform(get("/api/game/room/{roomId}", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(roomId));
    }

    @Test
    void testGetGameState() throws Exception {
        mockMvc.perform(get("/api/game/room/{roomId}/state", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));
    }

    @Test
    void testGetPlayers() throws Exception {
        mockMvc.perform(get("/api/game/room/{roomId}/players", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
} 