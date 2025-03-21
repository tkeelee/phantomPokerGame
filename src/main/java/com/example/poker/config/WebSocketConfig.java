package com.example.poker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 用于配置WebSocket连接和消息代理
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理
        config.enableSimpleBroker("/topic", "/queue");
        // 设置应用程序目标前缀
        config.setApplicationDestinationPrefixes("/app");
        // 设置用户目标前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，添加SockJS回退选项
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String username = accessor.getFirstNativeHeader("login");
                    if (username != null) {
                        System.out.println("[DEBUG] 用户连接 - 用户名: " + username);
                        System.out.println("[DEBUG] 所有头信息: " + accessor.toString());
                        accessor.setUser(() -> username);
                    } else {
                        System.out.println("[WARN] 用户连接但未提供用户名");
                        System.out.println("[DEBUG] 连接头信息: " + accessor.toString());
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    // 记录订阅信息
                    String destination = accessor.getDestination();
                    System.out.println("[DEBUG] 用户订阅 - 目标: " + destination);
                    if (accessor.getUser() != null) {
                        System.out.println("[DEBUG] 订阅用户: " + accessor.getUser().getName());
                    } else {
                        System.out.println("[WARN] 无法识别订阅用户");
                    }
                } else if (StompCommand.SEND.equals(accessor.getCommand())) {
                    // 记录发送消息信息
                    String destination = accessor.getDestination();
                    System.out.println("[DEBUG] 用户发送消息 - 目标: " + destination);
                    if (accessor.getUser() != null) {
                        System.out.println("[DEBUG] 发送用户: " + accessor.getUser().getName());
                    }
                }
                
                return message;
            }
        });
    }
}