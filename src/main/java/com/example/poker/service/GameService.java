package com.example.poker.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import java.util.List;
import com.example.poker.repository.GameRoomRepository;

@Service
public class GameService {
    private final HashMap<String, GameState> activeGames = new HashMap<>();
    
    @Autowired
    private GameRoomRepository gameRoomRepository;
    private final Queue<String> waitingPlayers = new LinkedList<>();
    private final Random random = new Random();

    public synchronized String initializeGame(String playerId) {
        if (waitingPlayers.isEmpty()) {
            waitingPlayers.add(playerId);
            return "WAITING";
        } else {
            String opponentId = waitingPlayers.poll();
            String roomId = generateRoomId();
            activeGames.put(roomId, new GameState(playerId, opponentId));
            return roomId;
        }
    }

    private String generateRoomId() {
        return String.format("%04d", random.nextInt(10000));
    }

    public GameState getGameState(String roomId) {
        return activeGames.get(roomId);
    }

    public void handleChallengeResult(String challengerId, String targetPlayerId, boolean isValid) {
        // 实现挑战结果处理逻辑
        GameState state = activeGames.values().stream()
            .filter(s -> s.containsPlayer(challengerId))
            .findFirst()
            .orElseThrow();
        
        if(isValid) {
            state.penalizePlayer(targetPlayerId);
            state.nextPlayer(); // 挑战成功后推进回合
        } else {
            state.penalizePlayer(challengerId);
            state.nextPlayer(); // 挑战失败后推进回合
        }
    }

    public boolean validateClaimFormat(String claim, int cardCount) {
        // 验证声明格式与出牌数量是否匹配
        if ("SINGLE".equals(claim)) {
            return cardCount == 1;
        } else if ("PAIR".equals(claim)) {
            return cardCount == 2;
        } else if ("TRIPLE".equals(claim)) {
            return cardCount == 3;
        } else if ("STRAIGHT".equals(claim)) {
            return cardCount >= 5;
        } else {
            return false;
        }
    }

    public boolean validateCardCombination(List<Card> cards) {
        // 验证卡牌组合是否符合游戏规则
        return cards.stream().allMatch(card -> 
            card.getRank() != null && 
            card.getSuit() != null && 
            card.getColor() != null
        );
    }

    public synchronized void transferCardDeck(String playerId, List<Card> cards) {
    activeGames.values().stream()
        .filter(game -> game.containsPlayer(playerId))
        .findFirst()
        .ifPresent(game -> {
            game.getCurrentPile().addAll(cards);
            game.getPlayerHands().get(playerId).removeAll(cards);
        });
}

public String getCurrentPlayer() {
    return activeGames.values().stream()
        .findFirst()
        .map(GameState::getCurrentPlayer)
        .orElse(null);
}

public String getNextPlayer() {
    return activeGames.values().stream()
        .findFirst()
        .map(state -> {
            String current = state.getCurrentPlayer();
            return state.getPlayers().stream()
                .filter(p -> !p.equals(current))
                .findFirst()
                .orElse(current);
        })
        .orElse(null);
}

public boolean validateLastClaim(String playerId) {
        // 验证玩家最后声明的牌型真实性
        return activeGames.values().stream()
            .filter(game -> game.containsPlayer(playerId))
            .findFirst()
            .map(GameState::validateLastClaim)
            .orElse(false);
    }
}