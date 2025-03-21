package com.example.poker.model;

/**
 * 游戏状态枚举
 * 用于表示游戏房间的当前状态
 */
public enum GameStatus {
    /**
     * 等待中状态，玩家可以加入房间
     */
    WAITING,    // 等待玩家加入
    
    /**
     * 准备状态，所有玩家都已准备好，等待房主开始游戏
     */
    READY,      // 所有玩家已准备，可以开始游戏
    
    /**
     * 游戏开始中状态，正在初始化游戏
     */
    STARTING,   // 游戏开始中
    
    /**
     * 游戏进行中状态
     */
    PLAYING,    // 游戏进行中
    
    /**
     * 游戏结束状态
     */
    FINISHED,   // 游戏结束
    
    /**
     * 游戏进行中状态
     */
    IN_PROGRESS  // 游戏进行中
} 