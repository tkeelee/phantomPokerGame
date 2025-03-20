package com.example.poker.service;

import com.example.poker.model.GameState;
import java.util.List;
import com.example.poker.model.Card;

/**
 * 玩家状态管理服务
 * 处理玩家掉线时的牌堆重新分配和房主转移机制
 */
public class PlayerService {
    private final RoomService roomService;

    public PlayerService(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * 处理玩家退出事件
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void handlePlayerExit(String roomId, String playerId) {
        GameState state = roomService.getRoomState(roomId);
        if (state != null) {
            // 转移房主（如果需要）
            if (playerId.equals(state.getHostId())) {
                roomService.transferHost(roomId);
            }

            // 将玩家手牌转移到底盘区域
            List<Card> exitPlayerHands = state.getPlayerHands().remove(playerId);
            if (exitPlayerHands != null) {
                state.getCurrentPile().addAll(exitPlayerHands);
            }

            // 从玩家列表中移除
            state.getPlayers().remove(playerId);

            // 如果游戏进行中且当前玩家退出，转移出牌权
            if (state.getGameStatus().equals("IN_PROGRESS") && playerId.equals(state.getCurrentPlayer())) {
                int currentIndex = state.getPlayers().indexOf(playerId);
                int nextIndex = (currentIndex + 1) % state.getPlayers().size();
                state.setCurrentPlayer(state.getPlayers().get(nextIndex));
            }
        }
    }
}