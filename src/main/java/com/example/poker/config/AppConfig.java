package com.example.poker.config;

import com.example.poker.service.RoomManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import javax.annotation.PostConstruct;

/**
 * 应用配置类
 * 负责应用启动时的初始化工作
 */
@Configuration
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    
    @Autowired
    @Lazy
    private RoomManagementService roomManagementService;
    
    /**
     * 应用启动后初始化
     * 确保房间数据在所有服务间同步
     */
    @PostConstruct
    public void init() {
        logger.info("应用启动，初始化房间数据同步...");
        try {
            roomManagementService.init();
            logger.info("房间数据同步初始化完成");
        } catch (Exception e) {
            logger.error("房间数据同步初始化失败: {}", e.getMessage(), e);
        }
    }
} 