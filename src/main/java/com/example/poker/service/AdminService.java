package com.example.poker.service;

import com.example.poker.dto.AdminSystemInfo;
import com.example.poker.model.GameRoom;
import com.example.poker.model.GameState;
import com.example.poker.model.Player;
import com.example.poker.model.Room;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;

/**
 * 管理员服务类
 * <p>
 * 提供游戏系统的管理功能，包括：
 * - 房间管理（查看、解散房间）
 * - 玩家管理（查看、踢出玩家）
 * - 系统监控（在线人数、房间数量等）
 * - 会话管理（WebSocket连接的跟踪）
 * </p>
 * <p>
 * 该服务与GameService和PlayerService协同工作，为管理员页面提供必要的后端支持。
 * 通过SimpMessagingTemplate处理WebSocket消息的发送，实现实时通知功能。
 * </p>
 *
 * @author phantom
 * @version 1.0
 * @see GameService
 * @see PlayerService
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminService {
    
    private static final Instant START_TIME = Instant.now();
    
    private final Map<String, Player> playerCache = new ConcurrentHashMap<>();
    private final RoomManagementService roomManagementService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService scheduledExecutorService;
    
    /** 当前活跃的WebSocket会话映射表 */
    private static final Map<String, Instant> activeSessions = new ConcurrentHashMap<>();
    
    /** 存储被禁用的玩家ID及其禁用时间 */
    private final Map<String, Instant> bannedPlayers = new ConcurrentHashMap<>();
    
    /**
     * 初始化方法，在服务启动时调用
     * Spring会在bean创建后自动调用@PostConstruct注解的方法
     */
    @PostConstruct
    public void init() {
        updatePlayerCache();
        // 每60秒自动更新一次缓存
        scheduledExecutorService.scheduleAtFixedRate(this::updatePlayerCache, 60, 60, TimeUnit.SECONDS);
        // 初始化黑名单
        log.info("初始化管理服务和黑名单");
        loadBannedPlayers();
    }
    
    /**
     * 获取所有房间信息
     * <p>
     * 从GameService获取所有GameRoom对象，并转换为管理页面可用的Room对象。
     * 转换过程中会补充房间状态等信息。
     * </p>
     *
     * @return 房间信息列表
     */
    public List<Room> getAllRooms() {
        // 从GameService获取GameRoom列表并转换为Room列表
        List<GameRoom> gameRooms = roomManagementService.getAllRooms();
        List<Room> rooms = new ArrayList<>();
        
        for (GameRoom gameRoom : gameRooms) {
            Room room = convertGameRoomToRoom(gameRoom);
            rooms.add(room);
        }
        
        return rooms;
    }
    
    /**
     * 将GameRoom转换为Room
     * <p>
     * 转换过程包括：
     * - 基本信息复制（ID、房主、最大玩家数等）
     * - 生成房间名称
     * - 获取并设置房间状态
     * </p>
     *
     * @param gameRoom 游戏房间对象
     * @return 转换后的Room对象
     */
    private Room convertGameRoomToRoom(GameRoom gameRoom) {
        Room room = new Room();
        room.setId(gameRoom.getId());
        
        // 从GameState获取房间名称
        GameState state = roomManagementService.getGameState(gameRoom.getId());
        String roomName = "房间 " + gameRoom.getId();
        if (state != null && state.getRoomName() != null && !state.getRoomName().isEmpty()) {
            roomName = state.getRoomName();
        }
        room.setName(roomName);
        
        room.setHostId(gameRoom.getHostId());
        room.setMaxPlayers(gameRoom.getMaxPlayers());
        room.setPlayers(new ArrayList<>(gameRoom.getPlayers()));
        
        // 添加其他需要的字段
        if (state != null) {
            room.setStatus(state.getGameStatus());
        } else {
            room.setStatus("WAITING");
        }
        
        return room;
    }
    
    /**
     * 获取所有玩家信息
     * <p>
     * 返回系统中所有玩家的信息，包括：
     * - 在线玩家
     * - 房间中的玩家
     * - 离线玩家（如果在缓存中）
     * </p>
     *
     * @return 玩家信息列表
     */
    public List<Player> getAllPlayers() {
        // 直接使用playerCache中的数据或更新缓存
        updatePlayerCache();
        return new ArrayList<>(playerCache.values());
    }
    
    /**
     * 更新玩家缓存
     * <p>
     * 从GameService获取最新的房间信息，更新玩家状态：
     * - 更新玩家所在房间
     * - 更新玩家状态（PLAYING/ONLINE）
     * - 更新最后活跃时间
     * </p>
     */
    private void updatePlayerCache() {
        try {
            // 获取所有游戏房间中的玩家
            List<GameRoom> gameRooms = roomManagementService.getAllRooms();
            Set<String> activePlayerIds = new HashSet<>();
            
            // 更新房间中的玩家状态
            for (GameRoom room : gameRooms) {
                for (String playerId : room.getPlayers()) {
                    activePlayerIds.add(playerId);
                    Player player = getOrCreatePlayer(playerId);
                    player.setRoomId(room.getId());
                    player.setStatus("PLAYING");
                    player.setLastActiveTime(Instant.now());
                    player.setActive(true);
                }
            }
            
            // 更新不在房间中的在线玩家状态
            playerCache.values().stream()
                .filter(player -> player.isActive() && !activePlayerIds.contains(player.getId()))
                .forEach(player -> {
                    player.setRoomId(null);
                    player.setStatus("ONLINE");
                });
                
            // 移除长时间未活跃的玩家（超过30分钟）
            Instant cutoffTime = Instant.now().minus(30, ChronoUnit.MINUTES);
            playerCache.values().removeIf(player -> 
                !player.isActive() && player.getLastActiveTime().isBefore(cutoffTime));
                
            // 直接发送更新，不再调用broadcastSystemData
            messagingTemplate.convertAndSend("/topic/admin/players", new ArrayList<>(playerCache.values()));
            messagingTemplate.convertAndSend("/topic/admin/rooms", getAllRooms());
        } catch (Exception e) {
            log.error("更新玩家缓存时发生错误: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取或创建玩家对象
     * <p>
     * 如果玩家已在缓存中存在，则返回缓存的对象；
     * 否则创建新的玩家对象并加入缓存。
     * </p>
     *
     * @param playerId 玩家ID
     * @return 玩家对象
     */
    private Player getOrCreatePlayer(String playerId) {
        if (playerCache.containsKey(playerId)) {
            return playerCache.get(playerId);
        }
        
        Player player = new Player();
        player.setId(playerId);
        player.setName(playerId); // 默认使用ID作为名称
        player.setStatus("ONLINE");
        player.setLastActiveTime(Instant.now());
        player.setActive(true);
        
        playerCache.put(playerId, player);
        return player;
    }
    
    /**
     * 注册玩家
     * <p>
     * 将玩家信息添加到缓存中，用于管理后台显示。
     * 如果玩家已存在，则更新其信息。
     * </p>
     *
     * @param player 玩家对象
     */
    public void registerPlayer(Player player) {
        if (player == null || player.getId() == null) {
            log.warn("尝试注册无效玩家");
            return;
        }
        
        // 如果玩家已存在，则保留其房间信息
        if (playerCache.containsKey(player.getId())) {
            Player existingPlayer = playerCache.get(player.getId());
            if (existingPlayer.getRoomId() != null) {
                player.setRoomId(existingPlayer.getRoomId());
                player.setStatus("PLAYING");
            }
        }
        
        // 更新玩家信息
        playerCache.put(player.getId(), player);
        log.info("玩家已注册/更新: {}", player.getName());
    }
    
    /**
     * 获取系统信息
     * @return 系统信息对象
     */
    public AdminSystemInfo getSystemInfo() {
        return AdminSystemInfo.builder()
            .startTime(START_TIME)
            .connectionCount(getActiveConnectionCount())
            .roomCount(roomManagementService.getAllRooms().size())
            .playerCount(playerCache.size())
            .build();
    }
    
    /**
     * 更新活跃会话
     * <p>
     * 当有新的WebSocket连接建立时调用此方法，
     * 记录会话ID和连接时间。
     * </p>
     *
     * @param sessionId WebSocket会话ID
     */
    public void updateActiveSession(String sessionId) {
        activeSessions.put(sessionId, Instant.now());
        log.debug("新会话连接: {}, 当前连接数: {}", sessionId, activeSessions.size());
    }
    
    /**
     * 移除活跃会话
     * <p>
     * 当WebSocket连接断开时调用此方法，
     * 从活跃会话列表中移除对应的会话。
     * </p>
     *
     * @param sessionId WebSocket会话ID
     */
    public void removeActiveSession(String sessionId) {
        activeSessions.remove(sessionId);
        log.debug("会话断开: {}, 当前连接数: {}", sessionId, activeSessions.size());
    }
    
    /**
     * 解散房间
     * <p>
     * 强制解散指定的游戏房间，流程包括：
     * 1. 验证房间是否存在
     * 2. 将所有玩家移出房间
     * 3. 通知所有相关玩家
     * 4. 删除房间
     * </p>
     *
     * @param roomId 要解散的房间ID
     * @throws IllegalArgumentException 如果房间不存在
     */
    public void dissolveRoom(String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            throw new IllegalArgumentException("房间ID不能为空");
        }
        
        // 检查房间是否存在
        GameRoom room = roomManagementService.getRoom(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在: " + roomId);
        }
        
        try {
            // 通知所有在房间中的玩家房间已解散
            List<String> playersInRoom = new ArrayList<>(room.getPlayers());
            for (String playerId : playersInRoom) {
                // 发送房间解散通知
                messagingTemplate.convertAndSendToUser(playerId, "/queue/notifications", 
                        Map.of(
                            "type", "FORCE_ROOM_EXIT", 
                            "message", "房间已被管理员解散，您已被退回大厅",
                            "action", "RETURN_LOBBY"
                        ));
                
                // 将玩家从房间中移除
                roomManagementService.leaveRoom(roomId, playerId);
                
                // 更新玩家缓存中的状态
                if (playerCache.containsKey(playerId)) {
                    Player player = playerCache.get(playerId);
                    player.setRoomId(null);
                    player.setStatus("ONLINE");
                }
            }
            
            // 从gameService中移除房间
            boolean roomRemoved = roomManagementService.removeRoom(roomId);
            if (roomRemoved) {
                log.info("房间已从系统中移除: {}", roomId);
            } else {
                log.warn("房间移除失败，可能房间已被删除或仍有玩家在房间中: {}", roomId);
            }
            
            // 记录操作日志
            log.info("管理员解散了房间: {}", roomId);
            
            // 确保缓存更新
            updatePlayerCache();
            
            // 广播系统数据更新
            broadcastSystemData();
        } catch (Exception e) {
            log.error("解散房间时发生错误: {}", e.getMessage(), e);
            throw new RuntimeException("解散房间失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 踢出玩家
     * <p>
     * 踢出指定的玩家，流程包括：
     * 1. 如果玩家在房间中，先将其踢出房间
     * 2. 从缓存中移除玩家信息
     * 3. 将玩家加入禁用名单
     * 4. 通知玩家被踢出
     * 5. 广播系统数据更新
     * </p>
     *
     * @param playerId 要踢出的玩家ID
     */
    public void kickPlayer(String playerId) {
        Player player = getOrCreatePlayer(playerId);
        
        log.info("踢出玩家: {}", playerId);
        
        // 如果玩家在房间中，先将其踢出房间
        if (player.getRoomId() != null) {
            kickPlayerFromRoom(playerId, player.getRoomId());
        }
        
        // 移除玩家
        removePlayer(playerId);
        
        // 将玩家加入禁用名单（默认封禁10秒）
        banPlayer(playerId, 10);
        
        // 确保从WebSocketController的在线玩家列表中移除
        try {
            // 通过WebSocket通知大厅更新玩家列表
            // 先发送玩家离线消息，这会触发WebSocketController的playerOffline方法
            messagingTemplate.convertAndSend("/app/players/offline", playerId);
            log.info("已发送玩家离线通知以更新大厅玩家列表");
        } catch (Exception e) {
            log.error("发送玩家离线通知失败: {}", e.getMessage());
        }
        
        // 通知玩家被踢出
        messagingTemplate.convertAndSendToUser(
            playerId, 
            "/queue/notifications",
            Map.of(
                "type", "FORCE_LOGOUT",
                "message", "您已被管理员踢出游戏，请重新登录",
                "action", "LOGOUT",
                "reason", "ADMIN_KICK",
                "bannedUntil", getBannedUntilTime(playerId)
            )
        );
        
        // 广播系统数据更新
        broadcastSystemData();
    }
    
    /**
     * 移除玩家
     * <p>
     * 从系统中删除指定的玩家信息。
     * 注意：此方法仅在内部使用，不提供直接的玩家删除功能。
     * </p>
     *
     * @param playerId 要移除的玩家ID
     */
    public void removePlayer(String playerId) {
        try {
            // 从缓存中移除
            playerCache.remove(playerId);
            log.info("玩家已从系统中移除: {}", playerId);
            // 立即广播更新
            broadcastSystemData();
        } catch (Exception e) {
            log.error("移除玩家时出错: {}", e.getMessage());
        }
    }
    
    /**
     * 将玩家踢出房间
     * <p>
     * 将指定玩家从指定房间中踢出，流程包括：
     * 1. 验证房间和玩家是否存在
     * 2. 验证玩家是否在该房间中
     * 3. 将玩家移出房间
     * 4. 更新玩家状态
     * 5. 通知被踢出的玩家
     * </p>
     *
     * @param playerId 要踢出的玩家ID
     * @param roomId 房间ID
     * @throws IllegalArgumentException 如果房间不存在或玩家不在房间中
     */
    public void kickPlayerFromRoom(String playerId, String roomId) {
        GameRoom gameRoom = roomManagementService.getRoom(roomId);
        Player player = getOrCreatePlayer(playerId);
        
        if (gameRoom == null) {
            log.warn("尝试将玩家踢出不存在的房间: {}", roomId);
            throw new IllegalArgumentException("房间不存在: " + roomId);
        }
        
        if (!gameRoom.getPlayers().contains(playerId)) {
            log.warn("玩家 {} 不在房间 {} 中", playerId, roomId);
            throw new IllegalArgumentException("玩家不在此房间中");
        }
        
        log.info("将玩家 {} 踢出房间 {}", playerId, roomId);
        
        // 将玩家移出房间
        roomManagementService.leaveRoom(playerId, roomId);
        
        // 更新玩家状态
        player.setRoomId(null);
        player.setStatus("ONLINE");
        
        // 通知WebSocketController更新玩家状态
        try {
            // 发送玩家离开房间事件
            messagingTemplate.convertAndSend("/app/rooms/leave", Map.of(
                "roomId", roomId,
                "playerId", playerId
            ));
            log.info("已发送玩家离开房间通知以更新大厅玩家列表");
        } catch (Exception e) {
            log.error("发送玩家离开房间通知失败: {}", e.getMessage());
        }
        
        // 通知玩家被踢出房间
        messagingTemplate.convertAndSendToUser(
            playerId, 
            "/queue/notification", 
            Map.of(
                "type", "KICKED_FROM_ROOM",
                "message", "您已被管理员踢出房间"
            )
        );
    }
    
    /**
     * 广播系统数据更新
     */
    public void broadcastSystemData() {
        try {
            // 不再调用updatePlayerCache，直接发送当前状态
            messagingTemplate.convertAndSend("/topic/admin/players", new ArrayList<>(playerCache.values()));
            messagingTemplate.convertAndSend("/topic/admin/rooms", getAllRooms());
            log.debug("已广播系统数据更新");
        } catch (Exception e) {
            log.error("广播系统数据更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将玩家加入黑名单
     * 
     * @param playerId 玩家ID
     * @param seconds 禁用时长（秒）
     */
    public void banPlayer(String playerId, int seconds) {
        Instant banUntil = Instant.now().plusSeconds(seconds);
        bannedPlayers.put(playerId, banUntil);
        log.info("玩家 {} 已被加入黑名单，禁用至: {}", playerId, banUntil);
        
        // 存储到持久化存储中
        saveBannedPlayers();
    }

    /**
     * 检查玩家是否被禁用
     * 
     * @param playerId 玩家ID
     * @return 是否被禁用
     */
    public boolean isPlayerBanned(String playerId) {
        Instant banTime = bannedPlayers.get(playerId);
        if (banTime == null) {
            return false;
        }
        
        // 检查禁用时间是否已过
        if (Instant.now().isAfter(banTime)) {
            // 自动解除禁用
            bannedPlayers.remove(playerId);
            log.info("玩家 {} 的禁用期已过，已自动解除禁用", playerId);
            saveBannedPlayers();
            return false;
        }
        
        return true;
    }

    /**
     * 获取玩家禁用到期时间
     * 
     * @param playerId 玩家ID
     * @return 禁用到期时间，如果未被禁用则返回null
     */
    public Instant getBannedUntilTime(String playerId) {
        return bannedPlayers.get(playerId);
    }

    /**
     * 解除玩家禁用
     * 
     * @param playerId 玩家ID
     */
    public void unbanPlayer(String playerId) {
        if (bannedPlayers.remove(playerId) != null) {
            log.info("玩家 {} 已被解除禁用", playerId);
            saveBannedPlayers();
        }
    }

    /**
     * 保存被禁用玩家列表
     * 这里简化实现，实际应用中应考虑持久化存储
     */
    private void saveBannedPlayers() {
        // 在实际应用中，这里应该将bannedPlayers保存到数据库或文件系统
        // 此处为简化示例，仅打印日志
        log.info("保存被禁用玩家列表，当前共有 {} 名被禁用玩家", bannedPlayers.size());
    }

    /**
     * 加载被禁用玩家列表
     * 这里简化实现，实际应用中应考虑从持久化存储加载
     */
    private void loadBannedPlayers() {
        // 在实际应用中，这里应该从数据库或文件系统加载bannedPlayers
        // 此处为简化示例，仅打印日志
        log.info("加载被禁用玩家列表");
    }

    /**
     * 将玩家标记为离线
     * @param playerId 玩家ID
     */
    public void markPlayerOffline(String playerId) {
        Player player = playerCache.get(playerId);
        if (player != null) {
            player.setActive(false);
            player.setStatus("OFFLINE");
            player.setLastActiveTime(Instant.now());
            // 立即广播更新
            broadcastSystemData();
        }
    }

    /**
     * 广播系统信息更新
     */
    private void broadcastSystemInfo() {
        try {
            messagingTemplate.convertAndSend("/topic/admin/system", getSystemInfo());
            log.debug("已广播系统信息更新");
        } catch (Exception e) {
            log.error("广播系统信息更新失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取活跃连接数
     */
    private int getActiveConnectionCount() {
        return (int) playerCache.values().stream()
            .filter(Player::isActive)
            .count();
    }

    /**
     * 定时更新系统信息
     */
    @Scheduled(fixedRate = 60000) // 每分钟更新一次
    public void scheduledSystemInfoUpdate() {
        broadcastSystemInfo();
    }

} 