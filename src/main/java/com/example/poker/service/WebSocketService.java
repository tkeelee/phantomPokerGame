package com.example.poker.service;

import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import com.example.poker.constant.GameConstants;
import com.example.poker.dto.GameStartDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * WebSocket通信服务
 * 负责处理游戏中的实时通信，包括：
 * 1. 广播游戏状态
 * 2. 发送通知消息
 * 3. 处理聊天消息
 */
@Service
public class WebSocketService {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomManagementService roomManagementService;

    public WebSocketService(SimpMessagingTemplate messagingTemplate, RoomManagementService roomManagementService) {
        this.messagingTemplate = messagingTemplate;
        this.roomManagementService = roomManagementService;
    }

    /**
     * 广播游戏状态
     * @param roomId 房间ID
     */
    public void broadcastGameState(String roomId) {
        logger.debug("广播游戏状态 - 房间ID: {}", roomId);
        
        GameState state = roomManagementService.getGameState(roomId);
        String destination = GameConstants.TOPIC_GAME_STATE + roomId;
        
        messagingTemplate.convertAndSend(destination, state);
    }

    /**
     * 发送游戏开始通知
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void sendGameStartNotification(String roomId, String playerId) {
        logger.info("发送游戏开始通知 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        
        GameState state = roomManagementService.getGameState(roomId);
        GameStartDto gameStartDto = createGameStartDto(state);
        
        String destination = GameConstants.TOPIC_GAME_NOTIFICATION + roomId;
        messagingTemplate.convertAndSend(destination, gameStartDto);
    }

    /**
     * 发送玩家出牌通知
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @param cards 打出的牌
     * @param declaredValue 声明的牌值
     */
    public void sendPlayNotification(String roomId, String playerId, List<Card> cards, String declaredValue) {
        logger.info("发送玩家出牌通知 - 房间ID: {}, 玩家ID: {}, 牌数: {}, 声明值: {}", 
            roomId, playerId, cards.size(), declaredValue);
        
        String message = String.format("玩家 %s 打出了 %d 张 %s", playerId, cards.size(), declaredValue);
        String destination = GameConstants.TOPIC_GAME_NOTIFICATION + roomId;
        
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 发送挑战通知
     * @param roomId 房间ID
     * @param challengerId 挑战者ID
     * @param targetId 被挑战者ID
     * @param isLying 是否说谎
     */
    public void sendChallengeNotification(String roomId, String challengerId, String targetId, boolean isLying) {
        logger.info("发送挑战通知 - 房间ID: {}, 挑战者: {}, 被挑战者: {}, 是否说谎: {}", 
            roomId, challengerId, targetId, isLying);
        
        String message = String.format("玩家 %s 挑战了玩家 %s 的上一手牌，%s", 
            challengerId, targetId, isLying ? "挑战成功！" : "挑战失败！");
        String destination = GameConstants.TOPIC_GAME_NOTIFICATION + roomId;
        
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 发送游戏结束通知
     * @param roomId 房间ID
     * @param winnerId 获胜者ID
     */
    public void sendGameEndNotification(String roomId, String winnerId) {
        logger.info("发送游戏结束通知 - 房间ID: {}, 获胜者: {}", roomId, winnerId);
        
        String message = String.format("游戏结束！玩家 %s 获胜！", winnerId);
        String destination = GameConstants.TOPIC_GAME_NOTIFICATION + roomId;
        
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 发送聊天消息
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @param message 消息内容
     */
    public void sendChatMessage(String roomId, String playerId, String message) {
        logger.debug("发送聊天消息 - 房间ID: {}, 玩家ID: {}, 消息: {}", roomId, playerId, message);
        
        String chatMessage = String.format("%s: %s", playerId, message);
        String destination = GameConstants.TOPIC_GAME_CHAT + roomId;
        
        messagingTemplate.convertAndSend(destination, chatMessage);
    }

    /**
     * 发送房主转移通知
     * @param roomId 房间ID
     * @param oldHostId 原房主ID
     * @param newHostId 新房主ID
     */
    public void sendHostTransferNotification(String roomId, String oldHostId, String newHostId) {
        logger.info("发送房主转移通知 - 房间ID: {}, 原房主: {}, 新房主: {}", roomId, oldHostId, newHostId);
        
        String message = String.format("房主 %s 已离开，%s 成为新房主", oldHostId, newHostId);
        String destination = GameConstants.TOPIC_GAME_NOTIFICATION + roomId;
        
        messagingTemplate.convertAndSend(destination, message);
    }

    /**
     * 创建游戏开始数据传输对象
     */
    private GameStartDto createGameStartDto(GameState state) {
        GameStartDto dto = new GameStartDto();
        dto.setCurrentPlayer(state.getCurrentPlayer());
        dto.setPlayers(state.getPlayers());
        dto.setPlayerHands(state.getPlayerHands());
        dto.setCurrentPile(state.getCurrentPile());
        dto.setSelectedCards(state.getLastPlayedCards());
        dto.setDeclaredValue(state.getLastPlayedValue());
        dto.setHostId(state.getHostId());
        dto.setGameStatus(state.getGameStatus());
        return dto;
    }
} 