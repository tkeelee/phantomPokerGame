package com.example.poker.model;

import java.time.Instant;
import java.util.List;

/**
 * 玩家接口，定义所有玩家类型共有的基本行为
 */
public interface IPlayer {
    /**
     * 获取玩家ID
     * @return 玩家ID
     */
    String getId();
    
    /**
     * 获取玩家名称
     * @return 玩家名称
     */
    String getName();
    
    /**
     * 设置玩家名称
     * @param name 玩家名称
     */
    void setName(String name);
    
    /**
     * 获取玩家手牌
     * @return 手牌列表
     */
    List<Card> getHand();
    
    /**
     * 设置玩家手牌
     * @param hand 手牌列表
     */
    void setHand(List<Card> hand);
    
    /**
     * 向手牌添加一张牌
     * @param card 要添加的牌
     */
    void addToHand(Card card);
    
    /**
     * 从手牌中移除一张牌
     * @param card 要移除的牌
     * @return 是否成功移除
     */
    boolean removeFromHand(Card card);
    
    /**
     * 从手牌中移除多张牌
     * @param cards 要移除的牌列表
     * @return 成功移除的牌列表
     */
    List<Card> removeCards(List<Card> cards);
    
    /**
     * 检查玩家是否拥有指定的牌
     * @param cards 要检查的牌列表
     * @return 是否拥有所有指定的牌
     */
    boolean hasCards(List<Card> cards);
    
    /**
     * 获取玩家分数
     * @return 玩家分数
     */
    int getScore();
    
    /**
     * 设置玩家分数
     * @param score 分数
     */
    void setScore(int score);
    
    /**
     * 更新玩家分数
     * @param delta 分数变化量
     */
    void updateScore(int delta);
    
    /**
     * 检查玩家是否活跃
     * @return 是否活跃
     */
    boolean isActive();
    
    /**
     * 设置玩家活跃状态
     * @param active 活跃状态
     */
    void setActive(boolean active);
    
    /**
     * 检查玩家是否准备好
     * @return 是否准备好
     */
    boolean isReady();
    
    /**
     * 设置玩家准备状态
     * @param ready 准备状态
     */
    void setReady(boolean ready);
    
    /**
     * 检查玩家是否是房主
     * @return 是否是房主
     */
    boolean isHost();
    
    /**
     * 设置玩家房主状态
     * @param host 房主状态
     */
    void setHost(boolean host);
    
    /**
     * 获取玩家所在房间ID
     * @return 房间ID
     */
    String getRoomId();
    
    /**
     * 设置玩家所在房间ID
     * @param roomId 房间ID
     */
    void setRoomId(String roomId);
    
    /**
     * 获取玩家最后活跃时间
     * @return 最后活跃时间
     */
    Instant getLastActiveTime();
    
    /**
     * 设置玩家最后活跃时间
     * @param lastActiveTime 最后活跃时间
     */
    void setLastActiveTime(Instant lastActiveTime);
    
    /**
     * 获取玩家状态
     * @return 玩家状态
     */
    String getStatus();
    
    /**
     * 设置玩家状态
     * @param status 玩家状态
     */
    void setStatus(String status);
    
    /**
     * 检查玩家是否是机器人
     * @return 是否是机器人
     */
    boolean isRobot();
} 