package com.example.poker.model;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class GameState {
    private String roomId;  // 房间ID
    private String hostId;  // 房主ID
    private GameStatus status;  // 游戏状态
    private String gameStatus;  // 游戏状态字符串表示
    private List<String> players;  // 玩家列表
    private int currentPlayerIndex;  // 当前玩家索引
    private String currentPlayer;  // 当前玩家ID
    private Map<String, List<Card>> playerHands;  // 玩家手牌
    private List<Card> currentPile;  // 当前牌堆
    private String lastClaim;  // 最后声明
    private List<Card> selectedCards;  // 选中的牌
    private String declaredValue;  // 声明的牌值
    private List<String> passedPlayers;  // 已经过牌的玩家
    private String lastPlayerId;  // 上次出牌的玩家
    private List<String> winners;  // 已打完手牌的玩家列表（按顺序）
    private String winner;
    private String roomName;  // 房间名称
    private int maxPlayers;  // 最大玩家数
    private int robotCount;  // 机器人数量
    
    // 上一次出牌信息
    private List<Card> lastPlayedCards;
    private String lastPlayedValue;
    private String lastPlayedPlayer;
    private Date lastPlayedTime;
    
    // 上一次挑战信息
    private Date lastChallengeTime;
    private String lastChallengePlayer;
    private String lastChallengeResult;
    private List<Card> lastChallengeCards;
    private String lastChallengeValue;
    private Boolean lastChallengeSuccess;
    private List<Card> lastChallengePile;
    private Map<String, List<Card>> lastChallengeHands;

    public GameState() {
        this.players = new ArrayList<>();
        this.playerHands = new HashMap<>();
        this.currentPile = new ArrayList<>();
        this.status = GameStatus.WAITING;
        this.gameStatus = "WAITING";
        this.currentPlayerIndex = 0;
        this.selectedCards = new ArrayList<>();
        this.passedPlayers = new ArrayList<>();
        this.winners = new ArrayList<>();
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public List<String> getPlayers() {
        return players;
    }

    public void setPlayers(List<String> players) {
        this.players = players;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public Map<String, List<Card>> getPlayerHands() {
        return playerHands;
    }

    public void setPlayerHands(Map<String, List<Card>> playerHands) {
        this.playerHands = playerHands;
    }

    public List<Card> getCurrentPile() {
        return currentPile;
    }

    public void setCurrentPile(List<Card> currentPile) {
        this.currentPile = currentPile;
    }

    public String getLastClaim() {
        return lastClaim;
    }

    public void setLastClaim(String lastClaim) {
        this.lastClaim = lastClaim;
    }

    public String getHostId() {
        return hostId;
    }

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public String getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    public void setCurrentPlayer(String playerId) {
        this.currentPlayer = playerId;
        // 更新玩家索引
        if (players != null && players.contains(playerId)) {
            this.currentPlayerIndex = players.indexOf(playerId);
        }
    }

    public String getCurrentPlayer() {
        if (currentPlayer != null) {
            return currentPlayer;
        }
        if (players != null && !players.isEmpty()) {
            return players.get(currentPlayerIndex);
        }
        return null;
    }

    public List<Card> getSelectedCards() {
        return selectedCards;
    }

    public void setSelectedCards(List<Card> selectedCards) {
        this.selectedCards = selectedCards;
    }

    public String getDeclaredValue() {
        return declaredValue;
    }

    public void setDeclaredValue(String declaredValue) {
        this.declaredValue = declaredValue;
    }

    public List<String> getPassedPlayers() {
        return passedPlayers;
    }

    public void setPassedPlayers(List<String> passedPlayers) {
        this.passedPlayers = passedPlayers;
    }

    public String getLastPlayerId() {
        return lastPlayerId;
    }

    public void setLastPlayerId(String lastPlayerId) {
        this.lastPlayerId = lastPlayerId;
    }

    /**
     * 添加一个过牌的玩家
     * @param playerId 过牌的玩家ID
     */
    public void addPassedPlayer(String playerId) {
        if (!passedPlayers.contains(playerId)) {
            passedPlayers.add(playerId);
        }
    }

    /**
     * 清空过牌玩家列表
     */
    public void clearPassedPlayers() {
        passedPlayers.clear();
    }

    /**
     * 检查是否所有玩家都已过牌（除当前玩家外）
     * @return 是否所有其他玩家都已过牌
     */
    public boolean haveAllPlayersPassed() {
        // 如果没有其他玩家，则返回false
        if (players.size() <= 1) {
            return false;
        }
        
        // 检查除当前玩家外的所有玩家是否都已过牌
        for (String player : players) {
            if (!player.equals(currentPlayer) && !passedPlayers.contains(player)) {
                return false;
            }
        }
        
        return true;
    }

    public String getNextPlayer() {
        if (players != null && !players.isEmpty()) {
            int nextIndex = (currentPlayerIndex + 1) % players.size();
            return players.get(nextIndex);
        }
        return null;
    }

    public void penalizePlayer(String playerId) {
        // 扣除玩家积分逻辑
        // 实际扣分规则需要根据游戏规则实现
        log.info("玩家 " + playerId + " 受到处罚");
    }

    public boolean containsPlayer(String playerId) {
        return players.contains(playerId);
    }

    public boolean validateLastClaim() {
        // 最后声明验证逻辑
        return currentPile.stream().allMatch(card -> 
            card.getRank().equals(lastClaim)
        );
    }

    public List<String> getWinners() {
        return winners;
    }

    public void setWinners(List<String> winners) {
        this.winners = winners;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public List<Card> getLastPlayedCards() {
        return lastPlayedCards;
    }

    public void setLastPlayedCards(List<Card> lastPlayedCards) {
        this.lastPlayedCards = lastPlayedCards;
    }

    public String getLastPlayedValue() {
        return lastPlayedValue;
    }

    public void setLastPlayedValue(String lastPlayedValue) {
        this.lastPlayedValue = lastPlayedValue;
    }

    public String getLastPlayedPlayer() {
        return lastPlayedPlayer;
    }

    public void setLastPlayedPlayer(String lastPlayedPlayer) {
        this.lastPlayedPlayer = lastPlayedPlayer;
    }

    public Date getLastPlayedTime() {
        return lastPlayedTime;
    }

    public void setLastPlayedTime(Date lastPlayedTime) {
        this.lastPlayedTime = lastPlayedTime;
    }

    public Date getLastChallengeTime() {
        return lastChallengeTime;
    }

    public void setLastChallengeTime(Date lastChallengeTime) {
        this.lastChallengeTime = lastChallengeTime;
    }

    public String getLastChallengePlayer() {
        return lastChallengePlayer;
    }

    public void setLastChallengePlayer(String lastChallengePlayer) {
        this.lastChallengePlayer = lastChallengePlayer;
    }

    public String getLastChallengeResult() {
        return lastChallengeResult;
    }

    public void setLastChallengeResult(String lastChallengeResult) {
        this.lastChallengeResult = lastChallengeResult;
    }

    public List<Card> getLastChallengeCards() {
        return lastChallengeCards;
    }

    public void setLastChallengeCards(List<Card> lastChallengeCards) {
        this.lastChallengeCards = lastChallengeCards;
    }

    public String getLastChallengeValue() {
        return lastChallengeValue;
    }

    public void setLastChallengeValue(String lastChallengeValue) {
        this.lastChallengeValue = lastChallengeValue;
    }

    public Boolean getLastChallengeSuccess() {
        return lastChallengeSuccess;
    }

    public void setLastChallengeSuccess(Boolean lastChallengeSuccess) {
        this.lastChallengeSuccess = lastChallengeSuccess;
    }

    public List<Card> getLastChallengePile() {
        return lastChallengePile;
    }

    public void setLastChallengePile(List<Card> lastChallengePile) {
        this.lastChallengePile = lastChallengePile;
    }

    public Map<String, List<Card>> getLastChallengeHands() {
        return lastChallengeHands;
    }

    public void setLastChallengeHands(Map<String, List<Card>> lastChallengeHands) {
        this.lastChallengeHands = lastChallengeHands;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public int getRobotCount() {
        return robotCount;
    }

    public void setRobotCount(int robotCount) {
        this.robotCount = robotCount;
    }
}