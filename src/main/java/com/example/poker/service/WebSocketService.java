package com.example.poker.service;

import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import com.example.poker.model.Player;
import com.example.poker.constant.GameConstants;
import com.example.poker.dto.GameStartDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

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
    
    // 用户会话管理
    private final Map<String, Set<String>> userSessionIds = new ConcurrentHashMap<>();
    private final Map<String, String> sessionUserIds = new ConcurrentHashMap<>();

    public WebSocketService(SimpMessagingTemplate messagingTemplate, RoomManagementService roomManagementService) {
        this.messagingTemplate = messagingTemplate;
        this.roomManagementService = roomManagementService;
    }

    /**
     * 注册用户会话
     */
    public void registerUserSession(String userId, String sessionId) {
        userSessionIds.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        sessionUserIds.put(sessionId, userId);
        logger.debug("已注册用户会话 - 用户: {}, 会话: {}", userId, sessionId);
    }
    
    /**
     * 注销用户会话
     */
    public void unregisterUserSession(String sessionId) {
        String userId = sessionUserIds.remove(sessionId);
        if (userId != null) {
            Set<String> userSessions = userSessionIds.get(userId);
            if (userSessions != null) {
                userSessions.remove(sessionId);
                if (userSessions.isEmpty()) {
                    userSessionIds.remove(userId);
                }
            }
            logger.debug("已注销用户会话 - 用户: {}, 会话: {}", userId, sessionId);
        }
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

    /**
     * 发送个人通知消息
     * @param playerId 玩家ID
     * @param notification 通知内容
     */
    public void sendNotification(String playerId, Map<String, Object> notification) {
        if (playerId == null || playerId.isEmpty()) {
            logger.warn("尝试向无效的玩家ID发送通知");
            return;
        }
        
        try {
            messagingTemplate.convertAndSendToUser(
                playerId,
                "/queue/notifications",
                notification
            );
            logger.debug("已向玩家 {} 发送通知", playerId);
        } catch (Exception e) {
            logger.error("向玩家 {} 发送通知失败: {}", playerId, e.getMessage(), e);
        }
    }
    
    /**
     * 广播玩家状态更新
     * @param players 玩家列表
     */
    public void broadcastPlayerStatusUpdate(List<Player> players) {
        try {
            // 1. 广播玩家列表更新
            messagingTemplate.convertAndSend("/topic/players", players);
            
            // 2. 广播强制刷新命令
            Map<String, Object> refreshCommand = new HashMap<>();
            refreshCommand.put("type", "REFRESH_PLAYERS");
            refreshCommand.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/system/refresh", refreshCommand);
            
            // 3. 广播在线玩家状态
            Map<String, Object> onlineStatus = new HashMap<>();
            onlineStatus.put("type", "ONLINE_PLAYERS");
            onlineStatus.put("players", players.stream()
                .filter(Player::isActive)
                .map(Player::getId)
                .collect(Collectors.toList()));
            onlineStatus.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/players/online", onlineStatus);
            
            logger.debug("已广播玩家状态更新 - 总玩家数: {}, 在线玩家数: {}", 
                players.size(), 
                players.stream().filter(Player::isActive).count());
        } catch (Exception e) {
            logger.error("广播玩家状态更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 通知玩家离线
     * @param playerId 玩家ID
     */
    public void notifyPlayerOffline(String playerId) {
        try {
            // 1. 广播玩家离线状态
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "PLAYER_OFFLINE");
            notification.put("playerId", playerId);
            notification.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/players/status", notification);
            
            // 2. 发送强制刷新命令
            Map<String, Object> refreshCommand = new HashMap<>();
            refreshCommand.put("type", "FORCE_REFRESH");
            refreshCommand.put("reason", "PLAYER_OFFLINE");
            refreshCommand.put("playerId", playerId);
            refreshCommand.put("timestamp", System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/system/refresh", refreshCommand);
            
            logger.debug("已广播玩家离线状态和刷新命令 - 玩家: {}", playerId);
        } catch (Exception e) {
            logger.error("广播玩家离线状态失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清理玩家的WebSocket会话
     * @param playerId 玩家ID
     */
    public void cleanupPlayerSession(String playerId) {
        try {
            // 获取玩家的所有会话
            Set<String> sessionIds = userSessionIds.get(playerId);
            if (sessionIds != null) {
                // 关闭所有会话
                for (String sessionId : sessionIds) {
                    try {
                        messagingTemplate.convertAndSend("/user/" + sessionId + "/queue/notifications",
                            Map.of(
                                "type", "SESSION_CLOSED",
                                "message", "您的会话已被关闭",
                                "timestamp", System.currentTimeMillis()
                            ));
                    } catch (Exception e) {
                        logger.error("发送会话关闭通知失败: {}", e.getMessage());
                    }
                }
                
                // 清理会话记录
                userSessionIds.remove(playerId);
                sessionIds.forEach(sessionUserIds::remove);
            }
            
            // 发送离线通知
            notifyPlayerOffline(playerId);
            
            logger.info("已清理玩家 {} 的WebSocket会话", playerId);
        } catch (Exception e) {
            logger.error("清理玩家WebSocket会话时出错: {}", e.getMessage(), e);
        }
    }
} 