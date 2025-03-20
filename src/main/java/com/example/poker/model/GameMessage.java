package com.example.poker.model;

import java.util.List;
import lombok.Data;

/**
 * 游戏消息类，用于WebSocket通信
 */
@Data
public class GameMessage {
    private String type;            // 消息类型，如 PLAY, PASS, CHALLENGE, CHAT 等
    private String playerId;        // 发送消息的玩家ID
    private String roomId;          // 房间ID
    private List<Card> cards;       // 操作的牌
    private String targetPlayerId;  // 目标玩家ID
    private int declaredCount;      // 声明的牌数
    private String declaredValue;   // 声明的牌值
    private String content;         // 消息内容（如聊天内容）
    private String claim;           // 出牌声明
    private boolean success;        // 操作是否成功
    private String message;         // 消息说明
    private GameState gameState;    // 游戏状态

    public GameMessage() {
    }

    public GameMessage(String type, String playerId) {
        this.type = type;
        this.playerId = playerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setCards(List<Card> cards) {
        this.cards = cards;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public int getDeclaredCount() {
        return declaredCount;
    }

    public void setDeclaredCount(int declaredCount) {
        this.declaredCount = declaredCount;
    }

    public String getDeclaredValue() {
        return declaredValue;
    }

    public void setDeclaredValue(String declaredValue) {
        this.declaredValue = declaredValue;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getClaim() {
        return claim;
    }

    public void setClaim(String claim) {
        this.claim = claim;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }
}