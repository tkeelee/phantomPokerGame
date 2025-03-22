package com.example.poker.controller;

import com.example.poker.model.*;
import com.example.poker.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏WebSocket控制器
 * 处理实时游戏消息
 */
@Controller
public class GameWebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketController.class);
    
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
                    messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), createErrorNotification(message.getPlayerId(), "未知的动作类型"));
                    break;
            }
        } catch (Exception e) {
            // 记录异常日志
            logger.error("处理游戏动作时发生错误: {}", e.getMessage(), e);
            
            // 处理异常
            message.setSuccess(false);
            message.setMessage(e.getMessage());
            messagingTemplate.convertAndSendToUser(message.getPlayerId(), "/queue/errors", message);
            
            // 发送通知给房间中的所有玩家
            messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), createErrorNotification(message.getPlayerId(), e.getMessage()));
        }
    }
    
    /**
     * 处理玩家出牌
     * @param message 游戏消息
     */
    private void handlePlayCards(GameMessage message) {
        try {
            gameService.playCards(message.getRoomId(), message);
            
            // 发送游戏状态更新
            GameState state = gameService.getGameState(message.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
            
            // 发送出牌通知
            GameNotification notification = new GameNotification();
            notification.setType("PLAY");
            notification.setPlayerId(message.getPlayerId());
            notification.setContent("玩家 " + message.getPlayerId() + " 打出了 " + message.getDeclaredCount() + " 张 " + message.getDeclaredValue());
            messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
            
            // 检查游戏是否结束
            GameRoom room = gameService.getRoom(message.getRoomId());
            if (room.getStatus() == GameStatus.FINISHED) {
                // 游戏结束，发送结束通知
                GameNotification endNotification = new GameNotification();
                endNotification.setType("GAME_END");
                // 构建排名信息
                StringBuilder rankInfo = new StringBuilder("游戏结束！排名：");
                // 首先列出获胜者
                for (int i = 0; i < state.getWinners().size(); i++) {
                    rankInfo.append("\n第").append(i + 1).append("名: 玩家").append(state.getWinners().get(i));
                }
                // 如果还有剩下的玩家，他们就是最后一名
                if (!state.getPlayers().isEmpty()) {
                    rankInfo.append("\n最后一名: 玩家").append(state.getPlayers().get(0));
                }
                endNotification.setContent(rankInfo.toString());
                messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), endNotification);
            }
        } catch (Exception e) {
            logger.error("处理出牌动作时发生错误: {}", e.getMessage(), e);
            throw e; // 重新抛出异常，让全局异常处理器处理
        }
    }
    
    /**
     * 处理玩家过牌
     * @param message 游戏消息
     */
    private void handlePass(GameMessage message) {
        try {
            gameService.pass(message.getRoomId(), message.getPlayerId());
            
            // 发送游戏状态更新
            GameState state = gameService.getGameState(message.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
            
            // 发送过牌通知
            GameNotification notification = new GameNotification();
            notification.setType("PASS");
            notification.setPlayerId(message.getPlayerId());
            notification.setContent("玩家 " + message.getPlayerId() + " 选择了过牌");
            messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
        } catch (Exception e) {
            logger.error("处理过牌动作时发生错误: {}", e.getMessage(), e);
            throw e; // 重新抛出异常，让全局异常处理器处理
        }
    }
    
    /**
     * 处理玩家质疑
     * @param message 游戏消息
     */
    private void handleChallenge(GameMessage message) {
        try {
            gameService.challenge(message.getRoomId(), message);
            
            // 发送游戏状态更新
            GameState state = gameService.getGameState(message.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + message.getRoomId(), state);
            
            // 发送质疑通知
            GameNotification notification = new GameNotification();
            notification.setType("CHALLENGE");
            notification.setPlayerId(message.getPlayerId());
            notification.setContent("玩家 " + message.getPlayerId() + " 对玩家 " + message.getTargetPlayerId() + " 的声明提出质疑");
            messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
            
            // 检查游戏是否结束
            GameRoom room = gameService.getRoom(message.getRoomId());
            if (room.getStatus() == GameStatus.FINISHED) {
                // 游戏结束，发送结束通知
                GameNotification endNotification = new GameNotification();
                endNotification.setType("GAME_END");
                // 构建排名信息
                StringBuilder rankInfo = new StringBuilder("游戏结束！排名：");
                // 首先列出获胜者
                for (int i = 0; i < state.getWinners().size(); i++) {
                    rankInfo.append("\n第").append(i + 1).append("名: 玩家").append(state.getWinners().get(i));
                }
                // 如果还有剩下的玩家，他们就是最后一名
                if (!state.getPlayers().isEmpty()) {
                    rankInfo.append("\n最后一名: 玩家").append(state.getPlayers().get(0));
                }
                endNotification.setContent(rankInfo.toString());
                messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), endNotification);
            }
        } catch (Exception e) {
            logger.error("处理质疑动作时发生错误: {}", e.getMessage(), e);
            throw e; // 重新抛出异常，让全局异常处理器处理
        }
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
        
        // 发送加入通知
        GameNotification notification = new GameNotification();
        notification.setType("JOIN");
        notification.setPlayerId(message.getPlayerId());
        notification.setContent("玩家 " + message.getPlayerId() + " 加入了房间");
        messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
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
        
        // 发送准备通知
        GameNotification notification = new GameNotification();
        notification.setType("READY");
        notification.setPlayerId(message.getPlayerId());
        notification.setContent("玩家 " + message.getPlayerId() + " 已准备");
        messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
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
        
        // 发送开始游戏通知
        GameNotification notification = new GameNotification();
        notification.setType("START");
        notification.setPlayerId(message.getPlayerId());
        notification.setContent("游戏开始！玩家 " + state.getCurrentPlayer() + " 先手");
        messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
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
            
            // 发送离开通知
            GameNotification notification = new GameNotification();
            notification.setType("LEAVE");
            notification.setPlayerId(message.getPlayerId());
            notification.setContent("玩家 " + message.getPlayerId() + " 离开了房间");
            messagingTemplate.convertAndSend("/topic/game/notification/" + message.getRoomId(), notification);
        } catch (Exception e) {
            // 房间可能已经被删除，忽略异常
            logger.info("房间 {} 可能已经被删除", message.getRoomId());
        }
    }
    
    /**
     * 处理聊天消息
     * @param message 游戏消息
     */
    private void handleChat(GameMessage message) {
        // 发送聊天消息
        GameNotification notification = new GameNotification();
        notification.setType("CHAT");
        notification.setPlayerId(message.getPlayerId());
        notification.setContent(message.getContent());
        messagingTemplate.convertAndSend("/topic/game/chat/" + message.getRoomId(), notification);
    }
    
    /**
     * 创建错误通知
     * @param playerId 玩家ID
     * @param errorMessage 错误信息
     * @return 通知对象
     */
    private GameNotification createErrorNotification(String playerId, String errorMessage) {
        GameNotification notification = new GameNotification();
        notification.setType("ERROR");
        notification.setPlayerId(playerId);
        notification.setContent(errorMessage);
        return notification;
    }
    
    /**
     * 处理添加机器人的请求
     * @param request 包含机器人数量和难度的请求
     */
    @MessageMapping("/game/robots/add")
    public void handleAddRobots(RobotRequest request) {
        try {
            logger.info("收到添加机器人请求 - 房间: {}, 数量: {}, 难度: {}", 
                    request.getRoomId(), request.getCount(), request.getDifficulty());
            
            gameService.addRobotsToRoom(request.getRoomId(), request.getCount(), request.getDifficulty(), request.getPlayerId());
            
            // 发送游戏状态更新
            GameState state = gameService.getGameState(request.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + request.getRoomId(), state);
            
            // 发送添加机器人通知
            GameNotification notification = new GameNotification();
            notification.setType("ROBOTS_ADDED");
            notification.setContent("房主添加了 " + request.getCount() + " 个机器人（难度：" + 
                    request.getDifficulty() + "）");
            messagingTemplate.convertAndSend("/topic/game/notification/" + request.getRoomId(), notification);
            
        } catch (Exception e) {
            logger.error("添加机器人失败: {}", e.getMessage(), e);
            
            // 发送错误通知给房主
            GameNotification errorNotification = createErrorNotification(request.getPlayerId(), 
                    "添加机器人失败: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(request.getPlayerId(), 
                    "/queue/errors", errorNotification);
        }
    }
    
    /**
     * 处理移除机器人的请求
     * @param request 包含房间ID的请求
     */
    @MessageMapping("/game/robots/remove")
    public void handleRemoveRobots(RobotRequest request) {
        try {
            logger.info("收到移除机器人请求 - 房间: {}", request.getRoomId());
            
            gameService.removeRobotsFromRoom(request.getRoomId(), request.getPlayerId());
            
            // 发送游戏状态更新
            GameState state = gameService.getGameState(request.getRoomId());
            messagingTemplate.convertAndSend("/topic/game/state/" + request.getRoomId(), state);
            
            // 发送移除机器人通知
            GameNotification notification = new GameNotification();
            notification.setType("ROBOTS_REMOVED");
            notification.setContent("房主移除了所有机器人");
            messagingTemplate.convertAndSend("/topic/game/notification/" + request.getRoomId(), notification);
            
        } catch (Exception e) {
            logger.error("移除机器人失败: {}", e.getMessage(), e);
            
            // 发送错误通知给房主
            GameNotification errorNotification = createErrorNotification(request.getPlayerId(), 
                    "移除机器人失败: " + e.getMessage());
            messagingTemplate.convertAndSendToUser(request.getPlayerId(), 
                    "/queue/errors", errorNotification);
        }
    }
    
    /**
     * 机器人请求对象
     */
    public static class RobotRequest {
        private String roomId;
        private String playerId;
        private int count;
        private String difficulty;
        
        // Getters and Setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getDifficulty() { return difficulty; }
        public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    }
} 