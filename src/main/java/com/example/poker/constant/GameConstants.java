package com.example.poker.constant;

/**
 * 游戏相关常量定义
 */
public class GameConstants {
    /**
     * WebSocket 消息类型
     */
    public static final String WS_TYPE_JOIN = "JOIN";
    public static final String WS_TYPE_LEAVE = "LEAVE";
    public static final String WS_TYPE_READY = "READY";
    public static final String WS_TYPE_START = "START";
    public static final String WS_TYPE_PLAY = "PLAY";
    public static final String WS_TYPE_CHALLENGE = "CHALLENGE";
    public static final String WS_TYPE_PASS = "PASS";
    public static final String WS_TYPE_CHAT = "CHAT";

    /**
     * 游戏状态
     */
    public static final String GAME_STATUS_WAITING = "WAITING";
    public static final String GAME_STATUS_READY = "READY";
    public static final String GAME_STATUS_PLAYING = "PLAYING";
    public static final String GAME_STATUS_FINISHED = "FINISHED";

    /**
     * WebSocket 主题
     */
    public static final String TOPIC_GAME_STATE = "/topic/game/state/";
    public static final String TOPIC_GAME_NOTIFICATION = "/topic/game/notification/";
    public static final String TOPIC_GAME_CHAT = "/topic/game/chat/";

    /**
     * 默认值
     */
    public static final int DEFAULT_MAX_PLAYERS = 4;
    public static final int DEFAULT_DECK_COUNT = 1;
} 