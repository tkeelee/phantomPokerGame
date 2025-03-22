package com.example.poker.controller;

import com.example.poker.dto.AdminSystemInfo;
import com.example.poker.model.Player;
import com.example.poker.model.Room;
import com.example.poker.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketController webSocketController;

    /**
     * 获取所有房间信息
     */
    @MessageMapping("/admin/rooms")
    @SendTo("/topic/rooms")
    public List<Room> getAllRooms(Principal principal) {
        log.info("用户 {} 请求获取所有房间信息", principal.getName());
        return adminService.getAllRooms();
    }

    /**
     * 获取所有玩家信息
     */
    @MessageMapping("/admin/players")
    @SendTo("/topic/players")
    public List<Player> getAllPlayers(Principal principal) {
        log.info("用户 {} 请求获取所有玩家信息", principal.getName());
        return adminService.getAllPlayers();
    }

    /**
     * 获取系统信息
     */
    @MessageMapping("/admin/system")
    @SendTo("/topic/system")
    public AdminSystemInfo getSystemInfo(Principal principal) {
        log.info("用户 {} 请求获取系统信息", principal.getName());
        return adminService.getSystemInfo();
    }

    /**
     * 解散房间
     */
    @MessageMapping("/admin/rooms/dissolve")
    public void dissolveRoom(Map<String, String> payload, Principal principal) {
        String roomId = payload.get("roomId");
        
        if (principal != null) {
            log.info("用户 {} 请求解散房间: {}", principal.getName(), roomId);
        } else {
            log.info("系统请求解散房间: {}", roomId);
        }
        
        try {
            adminService.dissolveRoom(roomId);
            
            // 手动广播更新后的房间列表和玩家列表
            messagingTemplate.convertAndSend("/topic/admin/rooms", adminService.getAllRooms());
            messagingTemplate.convertAndSend("/topic/admin/players", adminService.getAllPlayers());
            messagingTemplate.convertAndSend("/topic/admin/system", adminService.getSystemInfo());
        } catch (Exception e) {
            log.error("解散房间失败: " + e.getMessage(), e);
            
            // 发送错误消息
            if (principal != null) {
                messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    Map.of(
                        "success", false,
                        "message", "解散房间失败: " + e.getMessage()
                    )
                );
            }
            
            throw e;
        }
    }

    /**
     * 踢出玩家
     */
    @MessageMapping("/players/kick")
    @SendTo("/topic/players")
    public List<Player> kickPlayer(Map<String, String> payload, Principal principal) {
        String playerId = payload.get("playerId");
        
        if (principal != null) {
            log.info("用户 {} 请求踢出玩家 {}", principal.getName(), playerId);
        } else {
            log.info("系统请求踢出玩家 {}", playerId);
        }
        
        try {
            adminService.kickPlayer(playerId);
            
            // 向普通玩家广播更新玩家列表，确保大厅能看到玩家被移除
            messagingTemplate.convertAndSend("/topic/players", webSocketController.getOnlinePlayersForBroadcast());
            
            return adminService.getAllPlayers();
        } catch (Exception e) {
            log.error("踢出玩家失败: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 将玩家踢出房间
     */
    @MessageMapping("/rooms/kickPlayer")
    @SendTo("/topic/rooms")
    public List<Room> kickFromRoom(Map<String, String> payload, Principal principal) {
        String playerId = payload.get("playerId");
        String roomId = payload.get("roomId");
        
        if (principal != null) {
            log.info("用户 {} 请求将玩家 {} 踢出房间 {}", principal.getName(), playerId, roomId);
        } else {
            log.info("系统请求将玩家 {} 踢出房间 {}", playerId, roomId);
        }
        
        try {
            adminService.kickPlayerFromRoom(playerId, roomId);
            
            // 更新相关数据并广播
            // 使用WebSocketController的方法获取最新的玩家列表，确保大厅能看到状态变化
            messagingTemplate.convertAndSend("/topic/players", webSocketController.getOnlinePlayersForBroadcast());
            
            return adminService.getAllRooms();
        } catch (Exception e) {
            log.error("踢出房间失败: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 请求管理员数据
     */
    @MessageMapping("/admin/data")
    public void requestAdminData(Map<String, String> payload, Principal principal) {
        String userId = principal != null ? principal.getName() : "系统";
        log.info("用户 {} 请求管理员数据刷新", userId);
        
        try {
            // 获取最新数据
            List<Room> rooms = adminService.getAllRooms();
            List<Player> players = adminService.getAllPlayers();
            AdminSystemInfo systemInfo = adminService.getSystemInfo();
            
            // 发送到相应主题
            messagingTemplate.convertAndSendToUser(
                userId, 
                "/queue/admin/data",
                Map.of(
                    "success", true,
                    "message", "数据已刷新"
                )
            );
            
            // 广播更新
            messagingTemplate.convertAndSend("/topic/admin/rooms", rooms);
            messagingTemplate.convertAndSend("/topic/admin/players", players);
            messagingTemplate.convertAndSend("/topic/admin/system", systemInfo);
            
            log.info("已向用户 {} 发送管理员数据更新", userId);
        } catch (Exception e) {
            log.error("获取管理员数据失败: " + e.getMessage(), e);
            
            // 发送错误消息
            if (principal != null) {
                messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    Map.of(
                        "success", false,
                        "message", "获取数据失败: " + e.getMessage()
                    )
                );
            }
        }
    }

    /**
     * 踢出玩家
     * @param playerId 玩家ID
     * @param reason 原因
     * @return 操作结果
     */
    @PostMapping("/admin/players/{playerId}/kick")
    public ResponseEntity<?> kickPlayer(
            @PathVariable String playerId,
            @RequestParam(required = false) String reason) {
        
        // 检查管理员权限
        if (!checkAdminAccess()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "需要管理员权限"));
        }
        
        try {
            log.info("管理员请求踢出玩家: {}, 原因: {}", playerId, reason);
            
            // 直接调用AdminService的kickPlayer方法执行踢出操作
            adminService.kickPlayer(playerId);
            
            // 发送额外的强制登出通知，包含详细原因
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "FORCE_LOGOUT");
            notification.put("playerId", playerId);
            notification.put("message", "您已被管理员踢出游戏");
            notification.put("reason", reason != null ? reason : "违反游戏规则");
            notification.put("timestamp", System.currentTimeMillis());
            
            // 通过WebSocket发送通知
            messagingTemplate.convertAndSendToUser(
                    playerId, 
                    "/queue/notifications", 
                    notification);
            
            return ResponseEntity.ok(Map.of(
                    "success", true, 
                    "message", "已成功踢出玩家: " + playerId));
            
        } catch (Exception e) {
            log.error("踢出玩家时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "踢出玩家失败: " + e.getMessage()));
        }
    }

    /**
     * 检查管理员权限
     * @return 是否有管理员权限
     */
    private boolean checkAdminAccess() {
        // 根据实际认证机制实现
        // 这里简化为始终返回true
        return true;
    }
} 