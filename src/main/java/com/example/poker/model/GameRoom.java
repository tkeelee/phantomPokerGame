package com.example.poker.model;

import lombok.Data;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;

/**
 * 游戏房间类，表示一个游戏房间的状态
 * 合并了原GameRoom和GameState的功能
 */
@Data
@Entity
public class GameRoom {
    // 基本房间信息（持久化）
    @Id
    private String id;                        // 房间ID
    private String hostId;                    // 房主ID
    private int maxPlayers;                   // 最大玩家数
    private String roomName;                  // 房间名称
    @Enumerated(EnumType.STRING)
    private GameStatus status;                // 房间状态
    private String gameStatus;                // 游戏状态字符串表示
    @ElementCollection
    private List<String> players;             // 玩家列表
    @ElementCollection
    private List<String> readyPlayers;        // 已准备玩家列表
    @ElementCollection
    private List<String> robotPlayers;        // 机器人玩家列表
    private int robotCount;                   // 机器人数量
    private String robotDifficulty;           // 机器人难度
    
    // 游戏状态（非持久化）
    @Transient
    private int currentPlayerIndex;           // 当前玩家索引
    @Transient
    private String currentPlayer;             // 当前玩家ID
    @Transient
    private List<Card> cardDeck;              // 牌堆
    @Transient
    private Map<String, List<Card>> playerHands; // 玩家手牌映射
    @Transient
    private List<Card> currentPile;           // 当前牌堆（打出的牌）
    @Transient
    private String lastClaim;                 // 最后声明
    @Transient
    private List<Card> selectedCards;         // 选中的牌
    @Transient
    private String declaredValue;             // 声明的牌值
    @Transient
    private List<String> passedPlayers;       // 已经过牌的玩家
    @Transient
    private String lastPlayerId;              // 上次出牌的玩家
    @Transient
    private List<String> winners;             // 已打完手牌的玩家列表（按顺序）
    @Transient
    private String winner;                    // 最终获胜者
    
    // 上一次出牌信息（非持久化）
    @Transient
    private List<Card> lastPlayedCards;       // 上一次出的牌
    @Transient
    private String lastPlayedValue;           // 上一次声明的值
    @Transient
    private String lastPlayedPlayer;          // 上一次出牌的玩家
    @Transient
    private Date lastPlayedTime;              // 上一次出牌的时间
    
    // 上一次挑战信息（非持久化）
    @Transient
    private Date lastChallengeTime;           // 上一次挑战的时间
    @Transient
    private String lastChallengePlayer;       // 上一次挑战的玩家
    @Transient
    private String lastChallengeResult;       // 上一次挑战的结果
    @Transient
    private List<Card> lastChallengeCards;    // 上一次挑战的牌
    @Transient
    private String lastChallengeValue;        // 上一次挑战的值
    @Transient
    private Boolean lastChallengeSuccess;     // 上一次挑战是否成功
    @Transient
    private List<Card> lastChallengePile;     // 上一次挑战时的牌堆
    @Transient
    private Map<String, List<Card>> lastChallengeHands; // 上一次挑战时的手牌

    /**
     * 默认构造函数
     */
    public GameRoom() {
        this.players = new ArrayList<>();
        this.readyPlayers = new ArrayList<>();
        this.robotPlayers = new ArrayList<>();
        this.cardDeck = new ArrayList<>();
        this.playerHands = new HashMap<>();
        this.currentPile = new ArrayList<>();
        this.status = GameStatus.WAITING;
        this.gameStatus = "WAITING";
        this.robotCount = 0;
        this.robotDifficulty = "MEDIUM";
        this.currentPlayerIndex = 0;
        this.selectedCards = new ArrayList<>();
        this.passedPlayers = new ArrayList<>();
        this.winners = new ArrayList<>();
        this.lastChallengeHands = new HashMap<>();
    }

    /**
     * 添加机器人到房间
     * @param count 要添加的机器人数量
     * @param difficulty 机器人难度
     */
    public void addRobots(int count, String difficulty) {
        if (status != GameStatus.WAITING) {
            throw new IllegalStateException("只能在等待状态添加机器人");
        }
        
        if (players.size() + count > maxPlayers) {
            throw new IllegalStateException("添加机器人后超过最大玩家数");
        }

        this.robotDifficulty = difficulty;
        for (int i = 0; i < count; i++) {
            String robotId = "robot_" + (robotCount + i);
            robotPlayers.add(robotId);
            players.add(robotId);
            readyPlayers.add(robotId); // 机器人自动准备
        }
        robotCount += count;
    }

    /**
     * 移除所有机器人
     */
    public void removeAllRobots() {
        players.removeAll(robotPlayers);
        readyPlayers.removeAll(robotPlayers);
        robotPlayers.clear();
        robotCount = 0;
    }

    /**
     * 检查玩家是否是机器人
     * @param playerId 玩家ID
     * @return 是否是机器人
     */
    public boolean isRobot(String playerId) {
        return robotPlayers.contains(playerId);
    }

    /**
     * 获取机器人难度
     * @return 机器人难度
     */
    public String getRobotDifficulty() {
        return robotDifficulty;
    }

    /**
     * 设置机器人难度
     * @param difficulty 机器人难度
     */
    public void setRobotDifficulty(String difficulty) {
        this.robotDifficulty = difficulty;
    }

    /**
     * 获取机器人数量
     * @return 机器人数量
     */
    public int getRobotCount() {
        return robotCount;
    }

    /**
     * 添加玩家到房间
     * @param playerId 玩家ID
     */
    public void addPlayer(String playerId) {
        if (!players.contains(playerId)) {
            players.add(playerId);
        }
    }

    /**
     * 从房间移除玩家
     * @param playerId 玩家ID
     */
    public void removePlayer(String playerId) {
        players.remove(playerId);
        readyPlayers.remove(playerId);
    }

    /**
     * 检查所有玩家是否都已准备
     * @return 是否所有玩家都已准备
     */
    public boolean areAllPlayersReady() {
        return players.size() >= 2 && readyPlayers.size() == players.size();
    }

    /**
     * 获取下一个玩家的索引
     * @return 下一个玩家的索引
     */
    public int getNextPlayerIndex() {
        if (players == null || players.isEmpty()) {
            return 0;
        }
        return (currentPlayerIndex + 1) % players.size();
    }

    /**
     * 获取下一个玩家的ID
     * @return 下一个玩家的ID
     */
    public String getNextPlayerId() {
        if (players.isEmpty()) {
            return null;
        }
        return players.get(getNextPlayerIndex());
    }

    /**
     * 获取当前玩家的ID
     * @return 当前玩家的ID
     */
    public String getCurrentPlayerId() {
        if (currentPlayer != null) {
            return currentPlayer;
        }
        if (players.isEmpty() || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    /**
     * 设置当前玩家
     * @param playerId 玩家ID
     */
    public void setCurrentPlayer(String playerId) {
        this.currentPlayer = playerId;
        // 更新玩家索引
        if (players != null && players.contains(playerId)) {
            this.currentPlayerIndex = players.indexOf(playerId);
        }
    }
    
    /**
     * 添加一个过牌的玩家
     * @param playerId 过牌的玩家ID
     */
    public void addPassedPlayer(String playerId) {
        if (passedPlayers == null) {
            passedPlayers = new ArrayList<>();
        }
        if (!passedPlayers.contains(playerId)) {
            passedPlayers.add(playerId);
        }
    }

    /**
     * 清空过牌玩家列表
     */
    public void clearPassedPlayers() {
        if (passedPlayers != null) {
            passedPlayers.clear();
        }
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

    /**
     * 检查游戏是否结束（有玩家手牌为空）
     * @return 游戏是否结束
     */
    public boolean checkGameEnd() {
        if (playerHands == null || playerHands.isEmpty()) {
            return false;
        }
        
        // 游戏结束条件：只剩下1名玩家有手牌
        int playersWithCards = 0;
        for (String playerId : players) {
            List<Card> hand = playerHands.get(playerId);
            if (hand != null && !hand.isEmpty()) {
                playersWithCards++;
            }
        }
        
        return playersWithCards <= 1;
    }
    
    /**
     * 验证最后声明是否有效
     * @return 是否有效
     */
    public boolean validateLastClaim() {
        // 最后声明验证逻辑
        return currentPile.stream().allMatch(card -> 
            card.getRank().equals(declaredValue)
        );
    }
    
    /**
     * 创建一个用于前端显示的游戏状态对象
     * 保持与原GameState兼容
     * @return GameState对象
     */
    public GameState toGameState() {
        GameState state = new GameState();
        state.setRoomId(this.id);
        state.setHostId(this.hostId);
        state.setStatus(this.status);
        state.setGameStatus(this.gameStatus);
        state.setPlayers(this.players);
        state.setCurrentPlayerIndex(this.currentPlayerIndex);
        state.setCurrentPlayer(this.currentPlayer);
        state.setPlayerHands(this.playerHands);
        state.setCurrentPile(this.currentPile);
        state.setLastClaim(this.lastClaim);
        state.setSelectedCards(this.selectedCards);
        state.setDeclaredValue(this.declaredValue);
        state.setPassedPlayers(this.passedPlayers);
        state.setLastPlayerId(this.lastPlayerId);
        state.setWinners(this.winners);
        state.setWinner(this.winner);
        state.setRoomName(this.roomName);
        state.setMaxPlayers(this.maxPlayers);
        state.setRobotCount(this.robotCount);
        state.setLastPlayedCards(this.lastPlayedCards);
        state.setLastPlayedValue(this.lastPlayedValue);
        state.setLastPlayedPlayer(this.lastPlayedPlayer);
        state.setLastPlayedTime(this.lastPlayedTime);
        state.setLastChallengeTime(this.lastChallengeTime);
        state.setLastChallengePlayer(this.lastChallengePlayer);
        state.setLastChallengeResult(this.lastChallengeResult);
        state.setLastChallengeCards(this.lastChallengeCards);
        state.setLastChallengeValue(this.lastChallengeValue);
        state.setLastChallengeSuccess(this.lastChallengeSuccess);
        state.setLastChallengePile(this.lastChallengePile);
        state.setLastChallengeHands(this.lastChallengeHands);
        state.setReadyPlayers(this.readyPlayers);
        return state;
    }
    
    /**
     * 从GameState更新当前对象
     * 用于兼容现有代码
     * @param state GameState对象
     */
    public void updateFromGameState(GameState state) {
        this.currentPlayerIndex = state.getCurrentPlayerIndex();
        this.currentPlayer = state.getCurrentPlayer();
        this.playerHands = state.getPlayerHands();
        this.currentPile = state.getCurrentPile();
        this.lastClaim = state.getLastClaim();
        this.selectedCards = state.getSelectedCards();
        this.declaredValue = state.getDeclaredValue();
        this.passedPlayers = state.getPassedPlayers();
        this.lastPlayerId = state.getLastPlayerId();
        this.winners = state.getWinners();
        this.winner = state.getWinner();
        this.lastPlayedCards = state.getLastPlayedCards();
        this.lastPlayedValue = state.getLastPlayedValue();
        this.lastPlayedPlayer = state.getLastPlayedPlayer();
        this.lastPlayedTime = state.getLastPlayedTime();
        this.lastChallengeTime = state.getLastChallengeTime();
        this.lastChallengePlayer = state.getLastChallengePlayer();
        this.lastChallengeResult = state.getLastChallengeResult();
        this.lastChallengeCards = state.getLastChallengeCards();
        this.lastChallengeValue = state.getLastChallengeValue();
        this.lastChallengeSuccess = state.getLastChallengeSuccess();
        this.lastChallengePile = state.getLastChallengePile();
        this.lastChallengeHands = state.getLastChallengeHands();
    }
} 