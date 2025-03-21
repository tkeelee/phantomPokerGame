package com.example.poker.service;

import com.example.poker.model.GameState;
import java.util.List;
import com.example.poker.model.Card;
import com.example.poker.exception.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
}