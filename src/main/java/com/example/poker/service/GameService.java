package com.example.poker.service;

import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import java.util.List;

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
        // 实现声明格式验证逻辑
        return claim.matches("^(BOMB|STRAIGHT|FLUSH|FULL_HOUSE|STRAIGHT_FLUSH|ROYAL_FLUSH)$") && cardCount <= 5 && cardCount >= 1;
    }
    
    public boolean validateCardCombination(List<Card> cards) {
        // 实现卡牌组合验证逻辑
        return cards.stream().allMatch(c -> c.getValue() != null);
    }
    
    public void transferCardDeck(String playerId, List<Card> cards) {
        // 实现卡牌转移逻辑
        activeGames.values().stream()
            .filter(g -> g.containsPlayer(playerId))
            .findFirst()
            .ifPresent(g -> g.addCardsToDeck(cards));
    }
    
    public String getCurrentPlayer() {
        // 实现当前玩家获取逻辑
        return activeGames.values().stream()
            .findFirst()
            .map(GameState::getCurrentPlayer)
            .orElse("system");
    }
    
    public boolean validateLastClaim(String playerId) {
        // 实现最后声明验证逻辑
        return activeGames.values().stream()
            .filter(g -> g.containsPlayer(playerId))
            .findFirst()
            .map(GameState::validateLastClaim)
            .orElse(false);
    }
}