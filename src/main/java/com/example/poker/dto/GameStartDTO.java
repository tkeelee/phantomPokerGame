package com.example.poker.dto;

import java.util.List;
import java.util.Map;
import com.example.poker.model.Card;
public class GameStartDto {
    private String currentPlayer;
    private List<String> players;
    private Map<String, List<Card>> playerHands;
    private List<Card> currentPile;
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
    public void setGameStatus(String gameStatus) { this.gameStatus = gameStatus; }
}