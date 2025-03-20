package com.example.poker.model;

/**
 * 游戏通知模型
 * 用于发送游戏中的各种通知和事件
 */
public class GameNotification {
    private String type;       // 通知类型
    private String playerId;   // 相关玩家ID
    private String content;    // 通知内容
    private String roomId;     // 房间ID
    private boolean success;   // 是否成功
    
    public GameNotification() {
        this.success = true;
    }
    
    public GameNotification(String type, String playerId, String content) {
        this.type = type;
        this.playerId = playerId;
        this.content = content;
        this.success = true;
    }
    
    // Getters and setters
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
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getRoomId() {
        return roomId;
    }
    
    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
} 