package com.example.poker.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.poker.model.*;
import com.example.poker.service.GameService;

import java.util.List;
import java.util.Map;

/**
 * 游戏REST控制器
 * 处理游戏相关的HTTP请求
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired
    private GameService gameService;

    /**
     * 创建游戏房间
     * @param request 包含房主ID和最大玩家数的请求
     * @return 创建的房间
     */
    @PostMapping("/room")
    public ResponseEntity<GameRoom> createRoom(@RequestBody Map<String, Object> request) {
        String hostId = (String) request.get("hostId");
        int maxPlayers = Integer.parseInt(request.get("maxPlayers").toString());
        return ResponseEntity.ok(gameService.createRoom(hostId, maxPlayers));
    }

    /**
     * 加入游戏房间
     * @param roomId 房间ID
     * @param request 包含玩家ID的请求
     * @return 加入的房间
     */
    @PostMapping("/room/{roomId}/join")
    public ResponseEntity<GameRoom> joinRoom(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        return ResponseEntity.ok(gameService.joinRoom(roomId, playerId));
    }

    /**
     * 离开游戏房间
     * @param roomId 房间ID
     * @param request 包含玩家ID的请求
     * @return 成功响应
     */
    @PostMapping("/room/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        gameService.leaveRoom(roomId, playerId);
        return ResponseEntity.ok().build();
    }

    /**
     * 玩家准备
     * @param roomId 房间ID
     * @param request 包含玩家ID的请求
     * @return 更新后的房间
     */
    @PostMapping("/room/{roomId}/ready")
    public ResponseEntity<GameRoom> playerReady(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        return ResponseEntity.ok(gameService.playerReady(roomId, playerId));
    }

    /**
     * 开始游戏
     * @param roomId 房间ID
     * @param request 包含房主ID和牌组数量的请求
     * @return 更新后的房间
     */
    @PostMapping("/room/{roomId}/start")
    public ResponseEntity<GameRoom> startGame(@PathVariable String roomId, @RequestBody Map<String, Object> request) {
        String playerId = (String) request.get("playerId");
        int deckCount = Integer.parseInt(request.get("deckCount").toString());
        return ResponseEntity.ok(gameService.startGame(roomId, playerId, deckCount));
    }

    /**
     * 出牌
     * @param roomId 房间ID
     * @param message 游戏消息
     * @return 更新后的房间
     */
    @PostMapping("/room/{roomId}/play")
    public ResponseEntity<GameRoom> playCards(@PathVariable String roomId, @RequestBody GameMessage message) {
        message.setRoomId(roomId);
        return ResponseEntity.ok(gameService.playCards(roomId, message));
    }

    /**
     * 质疑
     * @param roomId 房间ID
     * @param message 游戏消息
     * @return 更新后的房间
     */
    @PostMapping("/room/{roomId}/challenge")
    public ResponseEntity<GameRoom> challenge(@PathVariable String roomId, @RequestBody GameMessage message) {
        message.setRoomId(roomId);
        return ResponseEntity.ok(gameService.challenge(roomId, message));
    }

    /**
     * 过牌
     * @param roomId 房间ID
     * @param request 包含玩家ID的请求
     * @return 更新后的房间
     */
    @PostMapping("/room/{roomId}/pass")
    public ResponseEntity<GameRoom> pass(@PathVariable String roomId, @RequestBody Map<String, String> request) {
        String playerId = request.get("playerId");
        return ResponseEntity.ok(gameService.pass(roomId, playerId));
    }

    /**
     * 获取房间信息
     * @param roomId 房间ID
     * @return 房间信息
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<GameRoom> getRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(gameService.getRoom(roomId));
    }

    /**
     * 获取游戏状态
     * @param roomId 房间ID
     * @return 游戏状态
     */
    @GetMapping("/room/{roomId}/state")
    public ResponseEntity<GameState> getGameState(@PathVariable String roomId) {
        return ResponseEntity.ok(gameService.getGameState(roomId));
    }

    /**
     * 获取房间中的玩家列表
     * @param roomId 房间ID
     * @return 玩家状态列表
     */
    @GetMapping("/room/{roomId}/players")
    public ResponseEntity<List<PlayerState>> getPlayers(@PathVariable String roomId) {
        return ResponseEntity.ok(gameService.getPlayers(roomId));
    }
}