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

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final SimpMessagingTemplate messagingTemplate;

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
            messagingTemplate.convertAndSend("/topic/players", adminService.getAllPlayers());
            
            return adminService.getAllRooms();
        } catch (Exception e) {
            log.error("踢出房间失败: " + e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 处理管理员请求数据
     */
    @MessageMapping("/admin/request-data")
    public void requestAdminData(Map<String, String> payload, Principal principal) {
        String requestType = payload.get("requestType");
        
        if (principal != null) {
            log.info("用户 {} 请求管理数据: {}", principal.getName(), requestType);
        } else {
            log.info("系统请求管理数据: {}", requestType);
        }
        
        try {
            // 获取最新数据
            List<Room> rooms = adminService.getAllRooms();
            List<Player> players = adminService.getAllPlayers();
            AdminSystemInfo systemInfo = adminService.getSystemInfo();
            
            // 将房间数据发送给所有订阅者
            messagingTemplate.convertAndSend("/topic/admin/rooms", rooms);
            
            // 将玩家数据发送给所有订阅者
            messagingTemplate.convertAndSend("/topic/admin/players", players);
            
            // 将系统信息发送给所有订阅者
            messagingTemplate.convertAndSend("/topic/admin/system", systemInfo);
            
            if (principal != null) {
                log.info("成功向用户 {} 发送管理数据", principal.getName());
                
                // 如果发生错误，只向请求用户发送错误消息
                messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/notifications",
                    Map.of(
                        "type", "ADMIN_DATA_UPDATED",
                        "message", "管理数据已更新"
                    )
                );
            } else {
                log.info("成功广播管理数据");
            }
        } catch (Exception e) {
            log.error("获取管理数据失败: " + e.getMessage(), e);
            
            // 如果有特定用户，向其发送错误消息
            if (principal != null) {
                messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    Map.of(
                        "success", false,
                        "message", "获取管理数据失败: " + e.getMessage()
                    )
                );
            }
        }
    }
} 