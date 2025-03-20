package com.example.poker.model;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GameState {
    public GameState() {
        this.gameStatus = "WAITING";
        this.players = new ArrayList<>();
        this.playerHands = new HashMap<>();
    }

    public GameState(String player1, String player2) {
        this.players.add(player1);
        this.players.add(player2);
        this.currentPlayer = player1;
        this.gameStatus = "IN_PROGRESS";
    }

    private String currentPlayer;
    private List<String> players = new ArrayList<>();
    private Map<String, List<Card>> playerHands = new HashMap<>();
    private List<Card> currentPile = new ArrayList<>();
    private List<Card> selectedCards;
    private String declaredValue;
    private String hostId;
    private String gameStatus;

    // Getters and setters
    public String getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(String currentPlayer) { this.currentPlayer = currentPlayer; }

    public List<String> getPlayers() { return players; }
    public void setPlayers(List<String> players) { this.players = players; }

    public Map<String, List<Card>> getPlayerHands() { return playerHands; }
    public void setPlayerHands(Map<String, List<Card>> playerHands) { this.playerHands = playerHands; }

    public List<Card> getCurrentPile() { return currentPile; }
    public void setCurrentPile(List<Card> currentPile) { this.currentPile = currentPile; }

    public List<Card> getSelectedCards() { return selectedCards; }
    public void setSelectedCards(List<Card> selectedCards) { this.selectedCards = selectedCards; }

    public String getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(String declaredValue) { this.declaredValue = declaredValue; }

    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }

    public String getGameStatus() { return gameStatus; }

    public void penalizePlayer(String playerId) {
        // 扣除玩家积分逻辑
        // 实际扣分规则需要根据游戏规则实现
        System.out.println("玩家 " + playerId + " 受到处罚");
    }

    public void nextPlayer() {
        int currentIndex = players.indexOf(currentPlayer);
        if(currentIndex != -1) {
            currentIndex = (currentIndex + 1) % players.size();
            currentPlayer = players.get(currentIndex);
        }
    }

    public boolean containsPlayer(String playerId) {
        return players.contains(playerId);
    }

    public boolean validateLastClaim() {
        // 最后声明验证逻辑
        return currentPile.stream().allMatch(card -> 
            card.getRank().equals(declaredValue)
        );
    }
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }
}