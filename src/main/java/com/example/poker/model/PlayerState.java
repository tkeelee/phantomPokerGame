package com.example.poker.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家状态类，用于记录游戏中玩家的状态信息
 */
@Data
public class PlayerState {
    private String playerId;        // 玩家ID
    private boolean active;         // 是否活跃
    private boolean ready;          // 是否准备好开始游戏
    private List<Card> hand;        // 手牌
    private boolean isHost;         // 是否是房主
    private int ranking;            // 排名
    private boolean winner;         // 是否胜利者
    private String lastAction;      // 最后一次动作
    private long lastActionTime;    // 最后一次动作时间
    
    /**
     * 默认构造函数
     */
    public PlayerState() {
        this.hand = new ArrayList<>();
        this.active = true;
        this.ready = false;
        this.isHost = false;
        this.winner = false;
        this.ranking = 0;
    }
    
    /**
     * 使用玩家ID创建状态
     * @param playerId 玩家ID
     */
    public PlayerState(String playerId) {
        this();
        this.playerId = playerId;
    }
    
    /**
     * 确认玩家是否已准备好
     * @return 准备状态
     */
    public boolean isReady() {
        return ready;
    }
    
    /**
     * 设置玩家准备状态
     * @param ready 准备状态
     */
    public void setReady(boolean ready) {
        this.ready = ready;
    }
    
    /**
     * 获取玩家手牌
     * @return 手牌列表
     */
    public List<Card> getHand() {
        return hand;
    }
    
    /**
     * 设置玩家手牌
     * @param hand 手牌列表
     */
    public void setHand(List<Card> hand) {
        this.hand = hand;
    }
    
    /**
     * 添加牌到手牌
     * @param card 要添加的牌
     */
    public void addCard(Card card) {
        if (hand == null) {
            hand = new ArrayList<>();
        }
        hand.add(card);
    }
    
    /**
     * 从手牌中移除指定的牌
     * @param cards 要移除的牌
     * @return 成功移除的牌列表
     */
    public List<Card> removeCards(List<Card> cards) {
        List<Card> removed = new ArrayList<>();
        if (hand != null && cards != null) {
            for (Card card : cards) {
                if (hand.remove(card)) {
                    removed.add(card);
                }
            }
        }
        return removed;
    }
    
    /**
     * 检查玩家是否有指定的牌
     * @param cards 要检查的牌
     * @return 是否拥有所有指定的牌
     */
    public boolean hasCards(List<Card> cards) {
        if (hand == null || cards == null) {
            return false;
        }
        
        // 创建手牌的副本，用于检查
        List<Card> handCopy = new ArrayList<>(hand);
        
        for (Card card : cards) {
            if (!handCopy.remove(card)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 手牌是否为空
     * @return 是否为空
     */
    public boolean isHandEmpty() {
        return hand == null || hand.isEmpty();
    }
    
    /**
     * 获取手牌数量
     * @return 手牌数量
     */
    public int getHandSize() {
        return hand == null ? 0 : hand.size();
    }
} 