package com.example.poker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 线程池配置类
 */
@Configuration
public class ExecutorConfig {
    
    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);
    private ScheduledExecutorService scheduledExecutorService;

    /**
     * 创建定时任务线程池
     * @return ScheduledExecutorService实例
     */
    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        return scheduledExecutorService;
    }
    
    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (scheduledExecutorService != null) {
            try {
                log.info("正在关闭定时任务线程池...");
                scheduledExecutorService.shutdown();
                if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    scheduledExecutorService.shutdownNow();
                    if (!scheduledExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                        log.error("线程池无法终止！");
                    }
                }
                log.info("定时任务线程池已关闭");
            } catch (InterruptedException e) {
                log.error("关闭线程池时发生错误: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
} 