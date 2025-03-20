package com.example.poker.model;

import java.util.Map;
import java.util.List;

public class GameMessage {
    private String actionType;
    private String playerId;
    private String claim;  // 新增声明字段
    private Object payload;

    public GameMessage(String actionType, String playerId, Object payload) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.payload = payload;
    }

    // Getters and Setters
    public String getActionType() { return actionType; }
    public String getPlayerId() { return playerId; }
    public Object getPayload() { return payload; }

    public void setActionType(String actionType) { this.actionType = actionType; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public void setPayload(Object payload) { this.payload = payload; }

    // 挑战操作构造函数
    public GameMessage(String actionType, String challengerId, boolean isValid, String nextPlayer) {
        this.actionType = actionType;
        this.playerId = challengerId;
        this.payload = Map.of("isValid", isValid, "nextPlayer", nextPlayer);
    }

    // 跟牌操作构造函数
    // 错误信息构造函数
    public GameMessage(String actionType, String playerId, String message) {
        this.actionType = actionType;
        this.playerId = playerId;
        this.payload = Map.of("error", message);
    }

    public GameMessage(String actionType, String followerId, List<String> cards, String nextPlayer) {
        this.actionType = actionType;
        this.playerId = followerId;
        this.payload = Map.of("cards", cards, "nextPlayer", nextPlayer);
    }
}