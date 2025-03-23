package com.example.poker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.poker.service.GameService;
import com.example.poker.service.AdminService;
import com.example.poker.model.Player;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private GameService gameService;

    @Autowired
    private AdminService adminService;

    // 存储房间信息
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();
    // 存储在线玩家
    private final Map<String, PlayerInfo> onlinePlayers = new ConcurrentHashMap<>();

    @MessageMapping("/rooms/list")
    @SendTo("/topic/rooms")
    public List<RoomInfo> getRoomList() {
        return new ArrayList<>(rooms.values());
    }

    @MessageMapping("/rooms/create")
    public void createRoom(CreateRoomRequest request) {
        try {
            log.info("收到创建房间请求");
            log.info("房主ID: " + request.getHostId());
            log.info("房间名称: " + request.getRoomName());
            log.info("最大玩家数: " + request.getMaxPlayers());
            
            // 检查玩家是否已在其他房间
            if (isPlayerInAnyRoom(request.getHostId())) {
                log.info("玩家已在其他房间中");
                
                // 发送错误响应
                JoinResponse response = new JoinResponse(false, "您已经在另一个房间中", null);
                messagingTemplate.convertAndSendToUser(request.getHostId(), "/queue/joinRoom", response);
                return;
            }
            
            // 创建新房间
            RoomInfo room = new RoomInfo();
            room.setId("room_" + System.currentTimeMillis());
            room.setName(request.getRoomName());
            room.setMaxPlayers(request.getMaxPlayers());
            room.setHostId(request.getHostId());
            room.getPlayers().add(request.getHostId());
            rooms.put(room.getId(), room);
            
            log.info("已创建房间");
            log.info("房间ID: " + room.getId());
            log.info("房间名称: " + room.getName());
            
            // 同步到GameService
            try {
                log.info("同步房间信息到GameService");
                gameService.createRoomWithId(room.getId(), request.getHostId(), request.getMaxPlayers(), room.getName());
                log.info("房间同步成功");
            } catch (Exception e) {
                log.error("同步房间到GameService失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 更新玩家状态为游戏中
            PlayerInfo player = onlinePlayers.get(request.getHostId());
            if (player != null) {
                player.setStatus("PLAYING");
                log.info("更新玩家状态");
                log.info("玩家: " + player.getName());
                log.info("状态: PLAYING");
                broadcastPlayerList();
            }

            // 发送成功响应给房主
            JoinResponse response = new JoinResponse(true, "创建成功", room.getId());
            log.info("发送创建成功响应");
            log.info("房主: " + request.getHostId());
            log.info("房间ID: " + room.getId());
            messagingTemplate.convertAndSendToUser(request.getHostId(), "/queue/joinRoom", response);
            
            // 广播更新后的房间列表
            broadcastRoomList();
            
            // 向管理页面广播更新的房间和玩家信息
            notifyAdminUpdate();
            
        } catch (Exception e) {
            log.error("创建房间失败: " + e.getMessage());
            e.printStackTrace();
            
            // 发送错误响应
            JoinResponse response = new JoinResponse(false, "创建房间失败: " + e.getMessage(), null);
            messagingTemplate.convertAndSendToUser(request.getHostId(), "/queue/joinRoom", response);
        }
    }

    @MessageMapping("/rooms/join")
    public void joinRoom(JoinRequest request) {
        log.info("收到加入房间请求");
        log.info("玩家: " + request.getPlayerId());
        log.info("房间: " + request.getRoomId());
        
        // 检查玩家是否已在同一房间
        RoomInfo targetRoom = rooms.get(request.getRoomId());
        if (targetRoom != null && targetRoom.getPlayers().contains(request.getPlayerId())) {
            log.info("玩家已在此房间中，直接发送成功响应");
            // 发送成功响应给玩家，允许重新加入自己的房间
            JoinResponse response = new JoinResponse(true, "已在房间中", request.getRoomId());
            messagingTemplate.convertAndSendToUser(
                request.getPlayerId(),
                "/queue/joinRoom",
                response
            );
            return;
        }

        // 检查玩家是否在其他房间
        for (RoomInfo room : rooms.values()) {
            if (!room.getId().equals(request.getRoomId()) && room.getPlayers().contains(request.getPlayerId())) {
                log.info("加入房间失败 - 玩家已在其他房间中");
                log.info("房间名: " + room.getName());
                messagingTemplate.convertAndSendToUser(
                    request.getPlayerId(),
                    "/queue/joinRoom",
                    new JoinResponse(false, "您已在房间 '" + room.getName() + "' 中")
                );
                return;
            }
        }

        if (targetRoom != null && targetRoom.getPlayerCount() < targetRoom.getMaxPlayers()) {
            targetRoom.setPlayerCount(targetRoom.getPlayerCount() + 1);
            targetRoom.getPlayers().add(request.getPlayerId());
            log.info("玩家成功加入房间");
            log.info("玩家: " + request.getPlayerId());
            log.info("房间: " + targetRoom.getName());
            
            // 更新玩家状态为游戏中
            PlayerInfo player = onlinePlayers.get(request.getPlayerId());
            if (player != null) {
                player.setStatus("PLAYING");
                log.info("更新玩家状态");
                log.info("玩家: " + player.getName());
                log.info("状态: PLAYING");
                broadcastPlayerList();
            }

            // 发送成功响应给请求加入的玩家
            JoinResponse response = new JoinResponse(true, "加入成功", request.getRoomId());
            log.info("准备发送加入成功响应到用户队列");
            log.info("目标用户: " + request.getPlayerId());
            log.info("响应内容: " + response);

            messagingTemplate.convertAndSendToUser(
                request.getPlayerId(),
                "/queue/joinRoom",
                response
            );

            log.info("响应发送完成");
            
            // 广播房间列表更新
            log.info("广播房间列表更新");
            messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));

            // 向管理页面广播更新的房间和玩家信息
            notifyAdminUpdate();
            
        } else {
            String errorMsg = targetRoom == null ? "房间不存在" : "房间已满";
            log.info("加入房间失败 - " + errorMsg);
            // 发送失败响应
            messagingTemplate.convertAndSendToUser(
                request.getPlayerId(),
                "/queue/joinRoom",
                new JoinResponse(false, errorMsg)
            );
        }
    }

    @MessageMapping("/players/online")
    public void playerOnline(PlayerInfo player) {
        // 检查玩家是否被禁用
        if (adminService.isPlayerBanned(player.getId())) {
            // 玩家被禁用，发送通知
            Instant banUntil = adminService.getBannedUntilTime(player.getId());
            String banTimeStr = banUntil != null ? banUntil.toString() : "未知时间";
            
            log.info("禁用玩家尝试登录 - 玩家: {}, 禁用至: {}", player.getId(), banTimeStr);
            
            messagingTemplate.convertAndSendToUser(
                player.getId(),
                "/queue/notifications",
                Map.of(
                    "type", "FORCE_LOGOUT",
                    "message", "您的账号已被暂时禁用，请稍后再试",
                    "action", "LOGOUT",
                    "reason", "BANNED",
                    "bannedUntil", banTimeStr
                )
            );
            return;
        }
        
        // 玩家未被禁用，正常处理
        onlinePlayers.put(player.getId(), player);
        broadcastPlayerList();
        
        // 确保管理后台也收到新玩家登录的通知
        try {
            // 创建一个Player对象并添加到管理服务
            Player adminPlayer = new Player();
            adminPlayer.setId(player.getId());
            adminPlayer.setName(player.getName());
            adminPlayer.setStatus("ONLINE");
            adminPlayer.setActive(true);
            adminPlayer.setLastActiveTime(Instant.now());
            
            // 在AdminService中注册玩家
            adminService.registerPlayer(adminPlayer);
            
            // 广播更新
            notifyAdminUpdate();
            log.info("已通知管理后台更新玩家列表 - 玩家: {}", player.getName());
        } catch (Exception e) {
            log.error("通知管理后台更新玩家列表失败: {}", e.getMessage());
        }
    }

    @MessageMapping("/players/offline")
    public void playerOffline(String playerId) {
        log.info("收到玩家离线请求 - 玩家: {}", playerId);
        
        // 尝试去除可能的引号
        if (playerId != null && playerId.startsWith("\"") && playerId.endsWith("\"")) {
            playerId = playerId.substring(1, playerId.length() - 1);
        }
        
        // 从在线玩家列表中移除
        PlayerInfo removedPlayer = onlinePlayers.remove(playerId);
        if (removedPlayer != null) {
            log.info("玩家已从在线列表移除 - 玩家: {}", removedPlayer.getName());
        }
        
        // 从所有房间中移除该玩家
        for (RoomInfo room : new ArrayList<>(rooms.values())) {
            if (room.getPlayers().contains(playerId)) {
                // 处理玩家离开房间
                handlePlayerLeaveRoom(room.getId(), playerId);
                log.info("已将玩家从房间移除 - 玩家: {}, 房间: {}", playerId, room.getName());
            }
        }
        
        // 广播更新后的玩家列表
        broadcastPlayerList();
        
        // 通知管理页面更新
        notifyAdminUpdate();
        
        // 向玩家发送需要重新登录的通知
        try {
            messagingTemplate.convertAndSendToUser(
                playerId, 
                "/queue/notifications", 
                Map.of(
                    "type", "FORCE_LOGOUT",
                    "message", "您已离线，请重新登录",
                    "action", "LOGOUT"
                )
            );
            log.info("已向玩家 {} 发送强制登出通知", playerId);
        } catch (Exception e) {
            log.error("发送强制登出通知失败: {}", e.getMessage());
        }
    }

    /**
     * 处理玩家离开房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    private void handlePlayerLeaveRoom(String roomId, String playerId) {
        RoomInfo room = rooms.get(roomId);
        if (room == null || !room.getPlayers().contains(playerId)) {
            return;
        }
        
        room.getPlayers().remove(playerId);
        room.setPlayerCount(room.getPlayerCount() - 1);
        log.info("玩家已从房间移除 - 玩家: " + playerId + ", 房间: " + room.getName() + ", 剩余玩家数: " + room.getPlayerCount());
        
        // 如果房间空了，删除房间
        if (room.getPlayerCount() == 0) {
            rooms.remove(room.getId());
            log.info("空房间已删除 - 房间ID: " + room.getId());
        }
        // 如果是房主退出，转移房主权
        else if (playerId.equals(room.getHostId()) && !room.getPlayers().isEmpty()) {
            String newHostId = room.getPlayers().get(0);
            room.setHostId(newHostId);
            log.info("房主权已转移 - 新房主: " + newHostId);
        }
        
        // 同步到游戏服务
        try {
            gameService.leaveRoom(roomId, playerId);
        } catch (Exception e) {
            log.error("同步玩家离开房间到GameService失败: " + e.getMessage());
        }
        
        // 广播房间列表更新
        messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
        
        // 广播玩家列表更新
        broadcastPlayerList();

        // 向管理页面广播更新的房间和玩家信息
        notifyAdminUpdate();
    }

    private void broadcastPlayerList() {
        messagingTemplate.convertAndSend("/topic/players", new ArrayList<>(onlinePlayers.values()));
    }

    /**
     * 获取在线玩家列表，用于广播
     * @return 在线玩家列表
     */
    public List<PlayerInfo> getOnlinePlayersForBroadcast() {
        return new ArrayList<>(onlinePlayers.values());
    }

    private String generateRoomId() {
        return "room_" + System.currentTimeMillis();
    }

    // 内部类定义
    public static class RoomInfo {
        private String id;
        private String name;
        private int maxPlayers;
        private int playerCount;
        private String hostId;
        private List<String> players = new ArrayList<>();

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
        public int getPlayerCount() { return playerCount; }
        public void setPlayerCount(int playerCount) { this.playerCount = playerCount; }
        public String getHostId() { return hostId; }
        public void setHostId(String hostId) { this.hostId = hostId; }
        public List<String> getPlayers() { return players; }
        public void setPlayers(List<String> players) { this.players = players; }
    }

    public static class PlayerInfo {
        private String id;
        private String name;
        private String status;

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class CreateRoomRequest {
        private String roomName;
        private int maxPlayers;
        private String hostId;

        // Getters and Setters
        public String getRoomName() { return roomName; }
        public void setRoomName(String roomName) { this.roomName = roomName; }
        public int getMaxPlayers() { return maxPlayers; }
        public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
        public String getHostId() { return hostId; }
        public void setHostId(String hostId) { this.hostId = hostId; }
    }

    public static class JoinRequest {
        private String roomId;
        private String playerId;

        // Getters and Setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
    }

    public static class JoinResponse {
        private boolean success;
        private String message;
        private String roomId;

        public JoinResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public JoinResponse(boolean success, String message, String roomId) {
            this.success = success;
            this.message = message;
            this.roomId = roomId;
        }

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
    }

    // 添加错误响应类
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    @MessageMapping("/admin/request-data-ws")
    public void handleAdminRequest() {
        log.info("收到管理员WebSocket请求数据");
        // 向管理员广播当前的系统状态
        adminService.broadcastSystemData();
    }

    @MessageMapping("/lobby/game/action")
    public void handleGameAction(GameAction action) {
        log.info("收到游戏大厅动作: {}", action.getType());
        
        if ("LEAVE".equals(action.getType())) {
            // 处理玩家离开房间
            handlePlayerLeaveRoom(action.getRoomId(), action.getPlayerId());
            
            // 发送玩家离开通知给房间其他玩家
            messagingTemplate.convertAndSend("/topic/room/" + action.getRoomId() + "/notify", 
                Map.of("type", "PLAYER_LEFT", "playerId", action.getPlayerId()));
            
            log.info("玩家 {} 离开房间 {}", action.getPlayerId(), action.getRoomId());
        }
        // 其他游戏动作处理...
    }

    // GameAction内部类
    public static class GameAction {
        private String type;
        private String roomId;
        private String playerId;
        private Map<String, Object> data;
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }

    @MessageMapping("/rooms/leave")
    public void leaveRoom(LeaveRoomRequest request) {
        log.info("收到离开房间请求 - 玩家: {}, 房间: {}", request.getPlayerId(), request.getRoomId());
        
        // 处理玩家离开房间
        handlePlayerLeaveRoom(request.getRoomId(), request.getPlayerId());
        
        // 发送确认消息给玩家
        messagingTemplate.convertAndSendToUser(
            request.getPlayerId(),
            "/queue/notifications",
            Map.of("type", "ROOM_LEFT", "message", "您已成功离开房间")
        );
        
        // 更新玩家状态为在线
        PlayerInfo player = onlinePlayers.get(request.getPlayerId());
        if (player != null) {
            player.setStatus("ONLINE");
            log.info("更新玩家状态为在线 - 玩家: {}", player.getName());
        }
        
        // 广播玩家列表更新
        broadcastPlayerList();
    }

    // 离开房间请求类
    public static class LeaveRoomRequest {
        private String roomId;
        private String playerId;
        
        // Getters and Setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
    }

    @MessageMapping("/lobby/rooms/dissolve")
    public void dissolveRoom(DissolveRoomRequest request) {
        log.info("收到解散房间请求 - 房间: {}", request.getRoomId());
        
        // 从本地缓存中获取房间
        RoomInfo room = rooms.get(request.getRoomId());
        
        if (room == null) {
            log.warn("尝试解散不存在的房间: {}", request.getRoomId());
            return;
        }
        
        try {
            // 获取房间中的所有玩家
            List<String> playersInRoom = new ArrayList<>(room.getPlayers());
            
            // 通知所有在房间中的玩家房间已解散
            for (String playerId : playersInRoom) {
                // 发送房间解散通知
                messagingTemplate.convertAndSendToUser(
                    playerId, 
                    "/queue/notifications", 
                    Map.of(
                        "type", "FORCE_ROOM_EXIT",
                        "message", "房间已被管理员解散，您已被退回大厅",
                        "action", "RETURN_LOBBY"
                    )
                );
                
                // 更新玩家状态
                PlayerInfo player = onlinePlayers.get(playerId);
                if (player != null) {
                    player.setStatus("ONLINE");
                }
            }
            
            // 从房间列表中移除房间
            rooms.remove(request.getRoomId());
            
            log.info("房间已解散: {}, 影响玩家数: {}", request.getRoomId(), playersInRoom.size());
            
            // 调用管理员服务解散游戏服务中的房间
            try {
                adminService.dissolveRoom(request.getRoomId());
            } catch (Exception e) {
                log.error("调用管理员服务解散房间失败: {}", e.getMessage());
            }
            
            // 广播房间列表更新
            messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
            
            // 广播玩家列表更新
            broadcastPlayerList();

            // 向管理页面广播更新的房间和玩家信息
            notifyAdminUpdate();
            
        } catch (Exception e) {
            log.error("解散房间时发生错误: {}", e.getMessage(), e);
        }
    }

    // 解散房间请求类
    public static class DissolveRoomRequest {
        private String roomId;
        
        // Getters and Setters
        public String getRoomId() { return roomId; }
        public void setRoomId(String roomId) { this.roomId = roomId; }
    }

    // 添加广播房间列表方法
    private void broadcastRoomList() {
        try {
            messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
            log.info("广播房间列表更新成功");
        } catch (Exception e) {
            log.error("广播房间列表失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 检查玩家是否在任何房间中
    private boolean isPlayerInAnyRoom(String playerId) {
        for (RoomInfo room : rooms.values()) {
            if (room.getPlayers().contains(playerId)) {
                return true;
            }
        }
        return false;
    }

    // 通知管理后台更新方法
    private void notifyAdminUpdate() {
        try {
            messagingTemplate.convertAndSend("/topic/admin/rooms", adminService.getAllRooms());
            messagingTemplate.convertAndSend("/topic/admin/players", adminService.getAllPlayers());
            log.info("已向管理页面广播房间和玩家更新");
        } catch (Exception e) {
            log.error("向管理页面广播更新失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 