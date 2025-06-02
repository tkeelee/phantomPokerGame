package com.example.poker.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * 玩家动作类，表示玩家在游戏中可以执行的各种操作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerAction {
    /**
     * 动作类型
     */
    public enum ActionType {
        PLAY,       // 出牌
        PASS,       // 过牌
        CHALLENGE,  // 质疑
        READY,      // 准备
        START,      // 开始游戏
        JOIN,       // 加入房间
        LEAVE,      // 离开房间
        CHAT        // 聊天
    }

    private String playerId;       // 玩家ID
    private ActionType type;       // 动作类型
    private String roomId;         // 房间ID
    private List<Card> cards;      // 出的牌
    private int declaredCount;     // 声明的牌数
    private String declaredValue;  // 声明的牌值
    private String targetPlayerId; // 目标玩家ID（针对质疑操作）
    private String message;        // 消息内容（用于聊天）

    /**
     * 创建指定类型的玩家动作
     * @param playerId 玩家ID
     * @param type 动作类型
     */
    public PlayerAction(String playerId, ActionType type) {
        this.playerId = playerId;
        this.type = type;
    }
}