package com.example.poker.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Objects;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private WebSocketController webSocketController; // 注入WebSocketController

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String playerId = headerAccessor.getFirstNativeHeader("login");

        if (playerId != null) {
            logger.info("Player connected: " + playerId);
            // 将玩家ID存储到Session属性中，以便在断开连接时使用
            //headerAccessor.getSessionAttributes().put("playerId", playerId);
            // 在连接成功时调用WebSocketController中的处理方法
            //webSocketController.handlePlayerOnline(playerId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String playerId = (String) headerAccessor.getSessionAttributes().get("playerId");

        if (playerId != null) {
            logger.info("Player disconnected: " + playerId);
            // 在连接断开时调用WebSocketController中的处理方法
            webSocketController.playerOffline(playerId);
        }
    }
}