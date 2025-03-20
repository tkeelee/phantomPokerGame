package com.example.poker.service;

import com.example.poker.model.*;
import java.util.List;

/**
 * 回合处理服务
 * 1. 处理出牌/过/判操作
 * 2. 验证牌型声明有效性
 * 3. 执行质疑判定后的牌堆转移
 */
public class TurnHandlerService {
    private final RoomService roomService;

    public TurnHandlerService(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * 处理出牌操作
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @param selectedCards 选中卡牌列表
     * @param declaredValue 声明牌值
     */
    public void handlePlay(String roomId, String playerId, List<Card> selectedCards, String declaredValue) {
        GameState state = roomService.getRoomState(roomId);
        if (state != null && state.getCurrentPlayer().equals(playerId)) {
            state.setSelectedCards(selectedCards);
            state.setDeclaredValue(declaredValue);
            state.getCurrentPile().addAll(selectedCards);
            transferTurn(state);
        }
    }

    /**
     * 执行质疑判定
     * @param roomId 房间ID
     * @param challengerId 质疑者ID
     */
    public void handleChallenge(String roomId, String challengerId) {
        GameState state = roomService.getRoomState(roomId);
        if (state != null) {
            boolean isValid = validateDeclaration(state.getSelectedCards(), state.getDeclaredValue());
            // 牌堆转移逻辑
            if (isValid) {
                punishPlayer(state, challengerId);
            } else {
                punishPlayer(state, state.getCurrentPlayer());
            }
            clearRoundState(state);
        }
    }

    private boolean validateDeclaration(List<Card> actualCards, String declaredValue) {
        return actualCards.stream().allMatch(card -> card.getValue().startsWith(declaredValue));
    }

    private void transferTurn(GameState state) {
        int currentIndex = state.getPlayers().indexOf(state.getCurrentPlayer());
        int nextIndex = (currentIndex + 1) % state.getPlayers().size();
        state.setCurrentPlayer(state.getPlayers().get(nextIndex));
    }

    private void punishPlayer(GameState state, String punishedPlayer) {
        state.getPlayerHands().get(punishedPlayer).addAll(state.getCurrentPile());
        state.getCurrentPile().clear();
    }

    private void clearRoundState(GameState state) {
        state.setSelectedCards(null);
        state.setDeclaredValue(null);
    }
}