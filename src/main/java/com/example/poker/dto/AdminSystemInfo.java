package com.example.poker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 管理员系统信息DTO
 * <p>
 * 此类用于封装管理员页面需要展示的系统状态信息，包括：
 * - 服务器启动时间
 * - 当前连接数
 * - 房间数量
 * - 玩家数量
 * - 系统版本
 * - 服务器状态
 * </p>
 * <p>
 * 通过AdminController返回给管理员页面，用于展示系统概况。
 * </p>
 * 
 * @author phantom
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSystemInfo {
    /**
     * 服务器启动时间
     */
    private Instant startTime;
    
    /**
     * 当前WebSocket连接数
     */
    private int connectionCount;
    
    /**
     * 当前游戏房间数量
     */
    private int roomCount;
    
    /**
     * 当前在线玩家数量
     */
    private int playerCount;
    
    /**
     * 系统版本号
     */
    private String version;
    
    /**
     * 服务器运行状态描述
     */
    private String serverStatus;
} 