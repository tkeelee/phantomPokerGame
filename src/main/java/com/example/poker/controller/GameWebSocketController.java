package com.example.poker.controller;

import com.example.poker.model.*;
import com.example.poker.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 游戏WebSocket控制器
 * 处理实时游戏消息
 */
@Controller
public class GameWebSocketController {
    
    @Autowired
    private GameService gameService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * 处理游戏动作
     * @param message 游戏消息
     */
    @MessageMapping("/game/action")
    public void handleGameAction(GameMessage message) {
        try {
            switch (message.getType()) {
                case "PLAY":
                    handlePlayCards(message);
                    break;
                case "PASS":
                    handlePass(message);
                    break;
                case "CHALLENGE":
                    handleChallenge(message);
                    break;
                case "JOIN":
                    handleJoinRoom(message);
                    break;
                case "READY":
                    handlePlayerReady(message);
                    break;
                case "START":
                    handleStartGame(message);
                    break;
                case "LEAVE":
                    handleLeaveRoom(message);
                    break;
                case "CHAT":
                    handleChat(message);
                    break;
                default:
                    // 返回错误消息
                    message.setSuccess(false);
                    message.setMessage("未知的动作类型");
                    messagingTemplate.convertAndSendToUser(message.getPlayerId(), "/queue/errors", message);
                    break;
            }
        } catch (Exception e) {
            // 处理异常
            message.setSuccess(false);
            message.setMessage(e.getMessage());
            messagingTemplate.convertAndSendToUser(message.getPlayerId(), "/queue/errors", message);
        }
    }
    
    /**
     * 处理玩家出牌
     * @param message 游戏消息
     */
    private void handlePlayCards(GameMessage message) {
        gameService.playCards(message.getRoomId(), message);
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理玩家过牌
     * @param message 游戏消息
     */
    private void handlePass(GameMessage message) {
        gameService.pass(message.getRoomId(), message.getPlayerId());
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理玩家质疑
     * @param message 游戏消息
     */
    private void handleChallenge(GameMessage message) {
        gameService.challenge(message.getRoomId(), message);
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理玩家加入房间
     * @param message 游戏消息
     */
    private void handleJoinRoom(GameMessage message) {
        gameService.joinRoom(message.getRoomId(), message.getPlayerId());
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理玩家准备
     * @param message 游戏消息
     */
    private void handlePlayerReady(GameMessage message) {
        gameService.playerReady(message.getRoomId(), message.getPlayerId());
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理开始游戏
     * @param message 游戏消息
     */
    private void handleStartGame(GameMessage message) {
        int deckCount = message.getDeclaredCount() > 0 ? message.getDeclaredCount() : 1;
        gameService.startGame(message.getRoomId(), message.getPlayerId(), deckCount);
        
        // 发送游戏状态更新
        GameState state = gameService.getGameState(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
    }
    
    /**
     * 处理玩家离开房间
     * @param message 游戏消息
     */
    private void handleLeaveRoom(GameMessage message) {
        gameService.leaveRoom(message.getRoomId(), message.getPlayerId());
        
        // 如果房间还存在，发送游戏状态更新
        try {
            GameState state = gameService.getGameState(message.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
        } catch (Exception e) {
            // 房间可能已经不存在，忽略异常
        }
    }
    
    /**
     * 处理聊天消息
     * @param message 游戏消息
     */
    private void handleChat(GameMessage message) {
        // 简单地将聊天消息广播给房间内所有人
        GameMessage chatResponse = new GameMessage("CHAT", message.getPlayerId());
        chatResponse.setContent(message.getContent());
        chatResponse.setRoomId(message.getRoomId());
        messagingTemplate.convertAndSend("/topic/chat/" + message.getRoomId(), chatResponse);
    }
} 