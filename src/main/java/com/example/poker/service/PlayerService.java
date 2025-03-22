package com.example.poker.service;

import com.example.poker.model.GameState;
import com.example.poker.model.Player;
import java.util.List;
import java.util.ArrayList;
import com.example.poker.model.Card;
import com.example.poker.exception.GameException;
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
    
    private final GameService gameService;
    private final WebSocketService webSocketService;
    
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
     */
    public PlayerService(GameService gameService, WebSocketService webSocketService) {
        this.gameService = gameService;
        this.webSocketService = webSocketService;
    }

    /**
     * 处理玩家退出事件
     * 包括以下处理：
     * 1. 房主转移（如果退出的是房主）
     * 2. 玩家手牌转移到底盘
     * 3. 从玩家列表中移除
     * 4. 转移出牌权（如果退出的是当前玩家）
     * 
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void handlePlayerExit(String roomId, String playerId) {
        logger.info("处理玩家退出 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        
        GameState state = gameService.getGameState(roomId);
        if (state == null) {
            logger.error("游戏状态不存在 - 房间ID: {}", roomId);
            throw new GameException("游戏状态不存在", "GAME_STATE_NOT_FOUND");
        }
        
        // 如果是房主退出，转移房主
        if (playerId.equals(state.getHostId())) {
            transferHost(roomId, playerId);
        }
        
        // 将玩家手牌放入底盘
        List<Card> playerCards = state.getPlayerHands().remove(playerId);
        if (playerCards != null && !playerCards.isEmpty()) {
            state.getCurrentPile().addAll(playerCards);
        }
        
        // 从玩家列表中移除
        state.getPlayers().remove(playerId);
        
        // 如果是当前玩家退出，轮到下一个玩家
        if (playerId.equals(state.getCurrentPlayer()) && !state.getPlayers().isEmpty()) {
            int nextIndex = (state.getPlayers().indexOf(state.getCurrentPlayer()) + 1) % state.getPlayers().size();
            state.setCurrentPlayer(state.getPlayers().get(nextIndex));
        }
        
        // 广播更新后的游戏状态
        webSocketService.broadcastGameState(roomId);
        
        logger.info("玩家退出处理完成 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
    }

    /**
     * 转移房主
     * @param roomId 房间ID
     * @param oldHostId 原房主ID
     */
    private void transferHost(String roomId, String oldHostId) {
        logger.info("转移房主 - 房间ID: {}, 原房主: {}", roomId, oldHostId);
        
        GameState state = gameService.getGameState(roomId);
        if (state == null || state.getPlayers().isEmpty()) {
            logger.error("无法转移房主 - 房间ID: {}", roomId);
            return;
        }
        
        // 选择新房主（通常是玩家列表中的第一个玩家）
        String newHostId = state.getPlayers().get(0);
        state.setHostId(newHostId);
        
        // 广播房主变更通知
        webSocketService.sendHostTransferNotification(roomId, oldHostId, newHostId);
        
        logger.info("房主转移完成 - 房间ID: {}, 新房主: {}", roomId, newHostId);
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
     * 更新玩家缓存
     * @param player 玩家对象
     */
    private void updatePlayerCache(Player player) {
        if (player != null && player.getId() != null) {
            playerCache.put(player.getId(), player);
        }
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
     * @param reason 踢出原因
     * @return 操作是否成功
     */
    public boolean kickPlayer(String playerId, String reason) {
        try {
            logger.info("开始踢出玩家 {} - 原因: {}", playerId, reason);
            
            // 查找玩家并检查其状态
            Player player = getPlayer(playerId);
            if (player == null) {
                logger.warn("尝试踢出不存在的玩家: {}", playerId);
                return false;
            }
            
            // 查找玩家所在房间
            String roomId = player.getRoomId();
            if (roomId != null && !roomId.isEmpty()) {
                try {
                    // 让玩家离开房间
                    gameService.leaveRoom(roomId, playerId);
                    logger.info("已将玩家 {} 从房间 {} 中移除", playerId, roomId);
                } catch (Exception e) {
                    logger.error("从房间移除玩家时出错: {}", e.getMessage(), e);
                }
            }
            
            // 标记玩家为离线
            player.setStatus("OFFLINE");
            player.setRoomId(null);
            player.setActive(false);
            player.setLastActiveTime(Instant.now());
            
            // 更新缓存
            updatePlayerCache(player);
            
            // 如果需要，添加到黑名单
            if (reason != null && reason.equals("BAN")) {
                banPlayer(playerId, 10); // 默认禁用10秒
            }
            
            // 通知客户端被踢出
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "FORCE_LOGOUT");
            notification.put("playerId", playerId);
            notification.put("message", "您已被管理员强制登出");
            notification.put("reason", reason);
            notification.put("timestamp", System.currentTimeMillis());
            
            try {
                // 发送WebSocket通知
                webSocketService.sendNotification(playerId, notification);
                logger.info("已发送踢出通知给玩家 {}", playerId);
            } catch (Exception e) {
                logger.error("发送踢出通知时出错: {}", e.getMessage(), e);
            }
            
            // 广播玩家状态更新
            broadcastPlayerStatusUpdate();
            
            // 从在线玩家列表中彻底移除
            removeFromActivePlayers(playerId);
            
            return true;
        } catch (Exception e) {
            logger.error("踢出玩家时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从活跃玩家列表中彻底移除玩家
     * @param playerId 玩家ID
     */
    private void removeFromActivePlayers(String playerId) {
        // 根据实际存储方式实现
        // 例如，从内存中的活跃玩家Map中移除
        activePlayers.remove(playerId);
        
        // 如果使用数据库，可能还需要更新数据库记录
    }

    /**
     * 广播玩家状态更新
     */
    private void broadcastPlayerStatusUpdate() {
        List<Player> players = getAllPlayers();
        webSocketService.broadcastPlayerStatusUpdate(players);
    }
}