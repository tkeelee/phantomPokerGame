package com.example.poker.model;

import lombok.Data;
import javax.persistence.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏房间类，表示一个游戏房间的状态
 */
@Data
@Entity
public class GameRoom {
    @Id
    private String id;                        // 房间ID
    private String hostId;                    // 房主ID
    private int maxPlayers;                   // 最大玩家数
    @Enumerated(EnumType.STRING)
    private GameStatus status;                // 房间状态
    @ElementCollection
    private List<String> players;             // 玩家列表
    @ElementCollection
    private List<String> readyPlayers;        // 已准备玩家列表
    @ElementCollection
    private List<String> robotPlayers;        // 机器人玩家列表
    private int robotCount;                   // 机器人数量
    private String robotDifficulty;           // 机器人难度
    private int currentPlayerIndex;           // 当前玩家索引
    @Transient
    private List<Card> cardDeck;              // 牌堆
    @Transient
    private Map<String, List<Card>> playerHands; // 玩家手牌映射
    @Transient
    private List<Card> currentPile;           // 当前牌堆（打出的牌）
    private String lastClaim;                 // 最后声明

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
        this.robotCount = 0;
        this.robotDifficulty = "MEDIUM";
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
            //String robotName = "机器人" + (robotCount + i);
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
     * 处理玩家退出
     * @param playerId 退出的玩家ID
     */
    public void handlePlayerExit(String playerId) {
        removePlayer(playerId);
        
        // 如果是房主退出，转移房主权限
        if (playerId.equals(hostId) && !players.isEmpty()) {
            hostId = players.get(0);
        }
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
        if (players.isEmpty() || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }
    
    /**
     * 获取最后声明的内容
     * @return 最后声明的内容
     */
    public String getLastClaim() {
        return lastClaim;
    }
    
    /**
     * 设置最后声明的内容
     * @param lastClaim 最后声明的内容
     */
    public void setLastClaim(String lastClaim) {
        this.lastClaim = lastClaim;
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
} 