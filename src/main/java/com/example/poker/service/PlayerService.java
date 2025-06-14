package com.example.poker.service;

import com.example.poker.model.GameState;
import com.example.poker.model.Player;
import java.util.List;
import java.util.ArrayList;
import com.example.poker.model.Card;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家状态管理服务
 * 负责处理玩家相关的状态管理，包括：
 * 1. 玩家退出处理
 * 2. 房主转移机制
 * 3. 玩家手牌重新分配
 * 4. 游戏状态维护
 */
@Service
public class PlayerService {
    private static final Logger logger = LoggerFactory.getLogger(PlayerService.class);
    
    private final RoomManagementService roomManagementService;
    private final WebSocketService webSocketService;
    private final AdminService adminService;
    
    /** 玩家数据缓存，用于存储玩家信息，避免频繁查询数据库 */
    private final Map<String, Player> playerCache = new HashMap<>();
    
    /** 被禁止的玩家 */
    private final Map<String, Instant> bannedPlayers = new ConcurrentHashMap<>();
    
    /** 活跃玩家列表 */
    private final Map<String, Player> activePlayers = new ConcurrentHashMap<>();

    /**
     * 构造函数
     * @param gameService 游戏服务实例
     * @param webSocketService WebSocket服务实例
     * @param adminService 管理服务实例
     */
    public PlayerService(RoomManagementService roomManagementService,WebSocketService webSocketService, AdminService adminService) {
        this.roomManagementService = roomManagementService;
        this.webSocketService = webSocketService;
        this.adminService = adminService;
    }

    /**
     * 通知玩家游戏开始
     * @param playerId 玩家ID
     * @param state 游戏状态
     */
    public void notifyGameStart(String playerId, GameState state) {
        logger.debug("通知玩家游戏开始 - 玩家ID: {}, 房间ID: {}", playerId, state.getRoomId());
        webSocketService.sendGameStartNotification(state.getRoomId(), playerId);
    }

    /**
     * 通知玩家出牌结果
     * @param playerId 玩家ID
     * @param state 游戏状态
     * @param playedPlayerId 出牌玩家ID
     * @param cards 打出的牌
     * @param declaredValue 声明的牌值
     * @param nextPlayerId 下一个玩家ID
     */
    public void notifyPlayResult(String playerId, GameState state, String playedPlayerId, 
                               List<Card> cards, String declaredValue, String nextPlayerId) {
        logger.debug("通知玩家出牌结果 - 玩家ID: {}, 房间ID: {}, 出牌玩家: {}", 
            playerId, state.getRoomId(), playedPlayerId);
        webSocketService.sendPlayNotification(state.getRoomId(), playedPlayerId, cards, declaredValue);
    }

    /**
     * 通知玩家挑战结果
     * @param playerId 玩家ID
     * @param state 游戏状态
     * @param challengerId 挑战者ID
     * @param targetId 被挑战者ID
     * @param isLying 是否说谎
     */
    public void notifyChallengeResult(String playerId, GameState state, String challengerId, 
                                    String targetId, boolean isLying) {
        logger.debug("通知玩家挑战结果 - 玩家ID: {}, 房间ID: {}, 挑战者: {}, 被挑战者: {}", 
            playerId, state.getRoomId(), challengerId, targetId);
        webSocketService.sendChallengeNotification(state.getRoomId(), challengerId, targetId, isLying);
    }

    /**
     * 获取玩家信息
     * @param playerId 玩家ID
     * @return 玩家对象
     */
    public Player getPlayer(String playerId) {
        return playerCache.getOrDefault(playerId, null);
    }
    
    /**
     * 获取所有玩家
     * @return 玩家列表
     */
    public List<Player> getAllPlayers() {
        return new ArrayList<>(playerCache.values());
    }
    
    /**
     * 将玩家加入禁用名单
     * @param playerId 玩家ID
     * @param seconds 禁用时长（秒）
     */
    public void banPlayer(String playerId, int seconds) {
        Instant banUntil = Instant.now().plusSeconds(seconds);
        bannedPlayers.put(playerId, banUntil);
        logger.info("玩家 {} 已被禁用至 {}", playerId, banUntil);
    }

    /**
     * 踢出玩家并清理其数据
     * @param playerId 要踢出的玩家ID
     * @return 操作是否成功
     */
    public boolean kickPlayer(String playerId) {
        try {
            logger.info("开始踢出玩家 {}", playerId);
            
            // 1. 获取玩家当前所在的房间（如果有）
            String currentRoomId = null;
            Player player = playerCache.get(playerId);
            if (player != null) {
                currentRoomId = player.getRoomId();
            }
            
            // 2. 如果玩家在房间中，先处理房间相关的清理
            if (currentRoomId != null) {
                try {
                    // 从房间中移除玩家
                    roomManagementService.leaveRoom(currentRoomId, playerId);
                    logger.info("已将玩家 {} 从房间 {} 中移除", playerId, currentRoomId);
                    
                    // 广播房间状态更新
                    webSocketService.broadcastGameState(currentRoomId);
                } catch (Exception e) {
                    logger.error("从房间移除玩家时出错: {}", e.getMessage(), e);
                }
            }
            
            // 3. 清理玩家的所有会话
            webSocketService.cleanupPlayerSession(playerId);
            logger.info("已清理玩家 {} 的所有会话", playerId);
            
            // 4. 从在线玩家列表中移除
            removeFromActivePlayers(playerId);
            logger.info("已从在线玩家列表移除玩家 {}", playerId);
            
            // 5. 从玩家缓存中移除
            playerCache.remove(playerId);
            logger.info("已从玩家缓存中移除玩家 {}", playerId);
            
            // 6. 将玩家加入临时黑名单（10秒）
            banPlayer(playerId, 10);
            logger.info("已将玩家 {} 加入临时黑名单", playerId);
            
            // 7. 发送强制下线通知
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "FORCE_LOGOUT");
            notification.put("message", "您已被管理员踢出游戏");
            notification.put("reason", "ADMIN_KICK");
            notification.put("timestamp", System.currentTimeMillis());
            
            try {
                webSocketService.sendNotification(playerId, notification);
                logger.info("已发送踢出通知给玩家 {}", playerId);
            } catch (Exception e) {
                logger.error("发送踢出通知时出错: {}", e.getMessage(), e);
            }
            
            // 8. 广播玩家状态更新到所有客户端
            broadcastPlayerStatusUpdate();
            
            // 9. 强制触发客户端刷新
            Map<String, Object> refreshNotification = new HashMap<>();
            refreshNotification.put("type", "FORCE_REFRESH");
            refreshNotification.put("timestamp", System.currentTimeMillis());
            webSocketService.broadcastPlayerStatusUpdate(getAllPlayers());
            
            return true;
        } catch (Exception e) {
            logger.error("踢出玩家时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从活跃玩家列表中移除玩家
     */
    private void removeFromActivePlayers(String playerId) {
        try {
            // 1. 从活跃玩家Map中移除
            Player player = activePlayers.remove(playerId);
            if (player != null) {
                logger.info("已从活跃玩家列表移除玩家: {}", playerId);
                
                // 2. 通知WebSocket服务
                webSocketService.notifyPlayerOffline(playerId);
                
                // 3. 通知管理服务将玩家标记为离线
                adminService.markPlayerOffline(playerId);
                
                // 4. 清理玩家相关的所有数据
                cleanupPlayerData(playerId);
            }
        } catch (Exception e) {
            logger.error("移除玩家时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 清理玩家相关的所有数据
     */
    private void cleanupPlayerData(String playerId) {
        try {
            // 1. 清理玩家的游戏状态
            roomManagementService.cleanupPlayerGameState(playerId);
            
            // 2. 清理玩家的房间关联
            roomManagementService.removePlayerFromAllRooms(playerId);
            
            // 3. 清理玩家的WebSocket会话
            webSocketService.cleanupPlayerSession(playerId);
            
            logger.info("已清理玩家 {} 的所有相关数据", playerId);
        } catch (Exception e) {
            logger.error("清理玩家数据时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 广播玩家状态更新
     */
    private void broadcastPlayerStatusUpdate() {
        List<Player> players = getAllPlayers();
        webSocketService.broadcastPlayerStatusUpdate(players);
    }
}