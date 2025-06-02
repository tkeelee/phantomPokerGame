package com.example.poker.model;

import java.util.List;

/**
 * 机器人策略接口，定义机器人的决策行为
 */
public interface RobotStrategy {
    /**
     * 决定是否质疑上一个玩家
     * @param lastClaim 上一个玩家的声明
     * @param currentPile 当前牌堆
     * @param hand 机器人的手牌
     * @return 是否质疑
     */
    boolean decideToChallenge(String lastClaim, List<Card> currentPile, List<Card> hand);
    
    /**
     * 选择要打出的牌
     * @param lastClaim 上一个玩家的声明
     * @param hand 机器人的手牌
     * @return 选择的牌列表
     */
    List<Card> selectCardsToPlay(String lastClaim, List<Card> hand);
    
    /**
     * 生成声明
     * @param selectedCards 选择的牌
     * @param lastClaim 上一个玩家的声明
     * @return 声明内容
     */
    String generateClaim(List<Card> selectedCards, String lastClaim);
} 