package com.example.poker.dto;

public class GameStartDTO {
    private int deckCount;
    private String hostId;
    private int playerCount;
    /**
     * 游戏状态：WAITING-等待准备，READY-已准备，IN_PROGRESS-进行中
     */
    private String gameStatus;

    public int getDeckCount() {
        return deckCount;
    }

    public void setDeckCount(int deckCount) {
        this.deckCount = deckCount;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public int getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
    }
    
    public String getGameStatus() {
        return gameStatus;
    }
    
    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }
}