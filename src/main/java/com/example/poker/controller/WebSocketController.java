package com.example.poker.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

@Controller
public class WebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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
    public void createRoom(RoomRequest request) {
        System.out.println("[DEBUG] 收到创建房间请求");
        System.out.println("[DEBUG] 房主: " + request.getHostId());
        System.out.println("[DEBUG] 房间名: " + request.getName());
        System.out.println("[DEBUG] 最大玩家数: " + request.getMaxPlayers());
        
        // 检查玩家是否已在其他房间
        for (RoomInfo existingRoom : rooms.values()) {
            if (existingRoom.getPlayers().contains(request.getHostId())) {
                System.out.println("[DEBUG] 创建房间失败 - 玩家已在其他房间中");
                System.out.println("[DEBUG] 房间名: " + existingRoom.getName());
                messagingTemplate.convertAndSendToUser(
                    request.getHostId(),
                    "/queue/createRoom",
                    new JoinResponse(false, "您已在房间 '" + existingRoom.getName() + "' 中")
                );
                return;
            }
        }

        RoomInfo room = new RoomInfo();
        room.setId(generateRoomId());
        room.setName(request.getName());
        room.setMaxPlayers(request.getMaxPlayers());
        room.setHostId(request.getHostId());
        room.setPlayerCount(1);
        room.getPlayers().add(request.getHostId()); // 将房主添加到玩家列表

        rooms.put(room.getId(), room);
        System.out.println("[DEBUG] 房间创建成功");
        System.out.println("[DEBUG] 房间ID: " + room.getId());
        System.out.println("[DEBUG] 房间名: " + room.getName());
        
        // 更新玩家状态为游戏中
        PlayerInfo player = onlinePlayers.get(request.getHostId());
        if (player != null) {
            player.setStatus("PLAYING");
            System.out.println("[DEBUG] 更新玩家状态");
            System.out.println("[DEBUG] 玩家: " + player.getName());
            System.out.println("[DEBUG] 状态: PLAYING");
            broadcastPlayerList();
        }

        // 发送成功响应给房主
        JoinResponse response = new JoinResponse(true, "创建成功", room.getId());
        System.out.println("[DEBUG] 发送创建成功响应");
        System.out.println("[DEBUG] 房主: " + request.getHostId());
        System.out.println("[DEBUG] 房间ID: " + room.getId());
        messagingTemplate.convertAndSendToUser(
            request.getHostId(),
            "/queue/createRoom",
            response
        );
        
        // 广播房间列表更新
        System.out.println("[DEBUG] 广播房间列表更新");
        messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
    }

    @MessageMapping("/rooms/join")
    public void joinRoom(JoinRequest request) {
        System.out.println("[DEBUG] 收到加入房间请求");
        System.out.println("[DEBUG] 玩家: " + request.getPlayerId());
        System.out.println("[DEBUG] 房间: " + request.getRoomId());
        
        // 检查玩家是否已在同一房间
        RoomInfo targetRoom = rooms.get(request.getRoomId());
        if (targetRoom != null && targetRoom.getPlayers().contains(request.getPlayerId())) {
            System.out.println("[DEBUG] 加入房间失败 - 玩家已在此房间中");
            messagingTemplate.convertAndSendToUser(
                request.getPlayerId(),
                "/queue/joinRoom",
                new JoinResponse(false, "您已在此房间中")
            );
            return;
        }

        // 检查玩家是否在其他房间
        for (RoomInfo room : rooms.values()) {
            if (room.getPlayers().contains(request.getPlayerId())) {
                System.out.println("[DEBUG] 加入房间失败 - 玩家已在其他房间中");
                System.out.println("[DEBUG] 房间名: " + room.getName());
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
            System.out.println("[DEBUG] 玩家成功加入房间");
            System.out.println("[DEBUG] 玩家: " + request.getPlayerId());
            System.out.println("[DEBUG] 房间: " + targetRoom.getName());
            
            // 更新玩家状态为游戏中
            PlayerInfo player = onlinePlayers.get(request.getPlayerId());
            if (player != null) {
                player.setStatus("PLAYING");
                System.out.println("[DEBUG] 更新玩家状态");
                System.out.println("[DEBUG] 玩家: " + player.getName());
                System.out.println("[DEBUG] 状态: PLAYING");
                broadcastPlayerList();
            }

            // 发送成功响应给请求加入的玩家
            JoinResponse response = new JoinResponse(true, "加入成功", request.getRoomId());
            System.out.println("[DEBUG] 准备发送加入成功响应到用户队列");
            System.out.println("[DEBUG] 目标用户: " + request.getPlayerId());
            System.out.println("[DEBUG] 响应内容: " + response);

            messagingTemplate.convertAndSendToUser(
                request.getPlayerId(),
                "/queue/joinRoom",
                response
            );

            System.out.println("[DEBUG] 响应发送完成");
            
            // 广播房间列表更新
            System.out.println("[DEBUG] 广播房间列表更新");
            messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
        } else {
            String errorMsg = targetRoom == null ? "房间不存在" : "房间已满";
            System.out.println("[DEBUG] 加入房间失败 - " + errorMsg);
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
        onlinePlayers.put(player.getId(), player);
        broadcastPlayerList();
    }

    @MessageMapping("/players/offline")
    public void playerOffline(String playerId) {
        System.out.println("收到玩家离线请求 - 玩家: " + playerId);
        
        // 从在线玩家列表中移除
        PlayerInfo removedPlayer = onlinePlayers.remove(playerId);
        if (removedPlayer != null) {
            System.out.println("玩家已从在线列表移除 - 玩家: " + removedPlayer.getName());
        }
        
        // 从所有房间中移除该玩家
        for (RoomInfo room : rooms.values()) {
            if (room.getPlayers().contains(playerId)) {
                room.getPlayers().remove(playerId);
                room.setPlayerCount(room.getPlayerCount() - 1);
                System.out.println("玩家已从房间移除 - 玩家: " + playerId + ", 房间: " + room.getName() + ", 剩余玩家数: " + room.getPlayerCount());
                
                // 如果房间空了，删除房间
                if (room.getPlayerCount() == 0) {
                    rooms.remove(room.getId());
                    System.out.println("空房间已删除 - 房间ID: " + room.getId());
                }
                // 如果是房主退出，转移房主权
                else if (playerId.equals(room.getHostId()) && !room.getPlayers().isEmpty()) {
                    String newHostId = room.getPlayers().get(0);
                    room.setHostId(newHostId);
                    System.out.println("房主权已转移 - 新房主: " + newHostId);
                }
            }
        }

        // 广播玩家列表更新
        System.out.println("广播玩家列表更新 - 当前在线玩家数: " + onlinePlayers.size());
        broadcastPlayerList();
        // 广播房间列表更新
        System.out.println("广播房间列表更新 - 当前房间数: " + rooms.size());
        messagingTemplate.convertAndSend("/topic/rooms", new ArrayList<>(rooms.values()));
    }

    private void broadcastPlayerList() {
        messagingTemplate.convertAndSend("/topic/players", new ArrayList<>(onlinePlayers.values()));
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

    public static class RoomRequest {
        private String name;
        private int maxPlayers;
        private String hostId;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
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
} 