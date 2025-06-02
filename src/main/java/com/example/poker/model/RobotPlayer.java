package com.example.poker.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 机器人玩家类，使用策略模式实现AI决策
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RobotPlayer extends AbstractPlayer {
    private RobotStrategy strategy;
    private String difficulty; // EASY, MEDIUM, HARD

    /**
     * 默认构造函数
     */
    public RobotPlayer() {
        super();
        this.difficulty = "MEDIUM";
        this.strategy = new DefaultRobotStrategy(difficulty);
    }

    /**
     * 使用ID和名称构造机器人玩家
     * @param id 玩家ID
     * @param name 玩家名称
     * @param difficulty 难度级别
     */
    public RobotPlayer(String id, String name, String difficulty) {
        super(id, name);
        this.difficulty = difficulty;
        this.strategy = new DefaultRobotStrategy(difficulty);
    }

    /**
     * 设置机器人难度
     * @param difficulty 难度级别
     */
    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
        this.strategy = new DefaultRobotStrategy(difficulty);
    }

    /**
     * 决定是否质疑上一个玩家
     * @param lastClaim 上一个玩家的声明
     * @param currentPile 当前牌堆
     * @return 是否质疑
     */
    public boolean decideToChallenge(String lastClaim, List<Card> currentPile) {
        return strategy.decideToChallenge(lastClaim, currentPile, getHand());
    }

    /**
     * 选择要打出的牌
     * @param lastClaim 上一个玩家的声明
     * @return 选择的牌列表
     */
    public List<Card> selectCardsToPlay(String lastClaim) {
        return strategy.selectCardsToPlay(lastClaim, getHand());
    }

    /**
     * 生成声明
     * @param selectedCards 选择的牌
     * @param lastClaim 上一个玩家的声明
     * @return 声明内容
     */
    public String generateClaim(List<Card> selectedCards, String lastClaim) {
        return strategy.generateClaim(selectedCards, lastClaim);
    }

    @Override
    public boolean isRobot() {
        return true;
    }
} 