package com.example.poker.model;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 抽象玩家类，实现IPlayer接口的基本功能
 */
@Data
public abstract class AbstractPlayer implements IPlayer {
    private String id;
    private String name;
    private List<Card> hand;
    private int score;
    private boolean active;
    private String status; // ONLINE, PLAYING, OFFLINE
    private String roomId;
    private Instant lastActiveTime;
    private boolean ready;
    private boolean host;

    /**
     * 默认构造函数
     */
    public AbstractPlayer() {
        this.hand = new ArrayList<>();
        this.score = 0;
        this.active = true;
        this.ready = false;
        this.host = false;
    }

    /**
     * 使用ID和名称构造玩家
     * @param id 玩家ID
     * @param name 玩家名称
     */
    public AbstractPlayer(String id, String name) {
        this();
        this.id = id;
        this.name = name;
    }

    @Override
    public void addToHand(Card card) {
        if (card != null) {
            if (hand == null) {
                hand = new ArrayList<>();
            }
            hand.add(card);
        }
    }

    @Override
    public boolean removeFromHand(Card card) {
        if (hand != null && card != null) {
            return hand.remove(card);
        }
        return false;
    }

    @Override
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

    @Override
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

    @Override
    public void updateScore(int delta) {
        score += delta;
    }

    @Override
    public boolean isRobot() {
        return false; // 默认不是机器人，子类可以覆盖
    }
} 