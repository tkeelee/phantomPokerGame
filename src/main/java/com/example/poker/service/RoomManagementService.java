package com.example.poker.service;

import com.example.poker.model.GameRoom;
import com.example.poker.model.GameState;
import com.example.poker.model.GameStatus;
import com.example.poker.model.Card;
import com.example.poker.exception.GameException;
import com.example.poker.controller.WebSocketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.annotation.Lazy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 房间管理服务
 * 负责处理房间的创建、加入、离开等基本操作，并确保WebSocketController和GameService中的房间数据一致
 */
@Service
public class RoomManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoomManagementService.class);
    
    // 中央房间存储，作为唯一的房间数据源
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    private GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    @Lazy
    private WebSocketController webSocketController;

    /**
     * 初始化方法，确保服务启动时数据同步
     */
    public void init() {
        // 同步GameService中的房间数据
        gameService.syncRooms(rooms);
        // 同步WebSocketController中的房间数据
        webSocketController.syncRooms(getRoomInfoList());
    }

    /**
     * 创建游戏房间
     * @param hostId 房主ID
     * @param maxPlayers 最大玩家数
     * @return 创建的房间
     */
    public GameRoom createRoom(String hostId, int maxPlayers) {
        logger.info("创建新房间 - 房主: {}, 最大玩家数: {}", hostId, maxPlayers);
        
        GameRoom room = new GameRoom();
        room.setId(UUID.randomUUID().toString());
        room.setHostId(hostId);
        room.setMaxPlayers(maxPlayers);
        room.setStatus(GameStatus.WAITING);
        room.setGameStatus("WAITING");
        room.setPlayers(new ArrayList<>());
        room.addPlayer(hostId);
        room.setCurrentPlayerIndex(0);
        room.setRobotCount(0); // 初始化机器人数量为0
        
        // 存储房间
        rooms.put(room.getId(), room);

        // 同步游戏服务中的房间
        syncRoomData();
        
        logger.info("房间创建成功 - 房间ID: {}", room.getId());
        return room;
    }

    /**
     * 使用指定的房间ID创建房间
     * 这个方法用于从WebSocketController同步房间信息
     * @param roomId 房间ID
     * @param hostId 房主ID
     * @param maxPlayers 最大玩家数
     * @return 创建的房间
     */
    public GameRoom createRoomWithId(String roomId, String hostId, int maxPlayers, String roomName) {
        logger.info("使用指定ID创建房间: " + roomId);
        
        // 检查房间是否已存在
        if (rooms.containsKey(roomId)) {
            logger.info("房间已存在，返回现有房间: " + roomId);
            return rooms.get(roomId);
        }
        
        GameRoom room = new GameRoom();
        room.setId(roomId);
        room.setHostId(hostId);
        room.setMaxPlayers(maxPlayers);
        room.setStatus(GameStatus.WAITING);
        room.setGameStatus("WAITING");
        room.setPlayers(new ArrayList<>());
        room.addPlayer(hostId);
        room.setCurrentPlayerIndex(0);
        room.setRoomName(roomName);
        
        // 存储房间
        rooms.put(roomId, room);

        // 同步房间数据
        syncRoomData();
        
        logger.info("成功创建指定ID的房间: " + roomId);
        logger.info("当前所有房间: " + rooms.keySet());
        
        return room;
    }

    /**
     * 加入房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 加入的房间
     */
    public GameRoom joinRoom(String roomId, String playerId) {
        logger.info("尝试加入房间, roomId: " + roomId + ", playerId: " + playerId);
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            logger.error("房间不存在: " + roomId);
            logger.info("当前存在的房间: " + rooms.keySet());
            throw new GameException("房间不存在：" + roomId, "ROOM_NOT_FOUND");
        }
        
        logger.info("找到房间, 状态: " + room.getStatus() + ", 玩家: " + room.getPlayers() + ", 最大人数: " + room.getMaxPlayers());
        
        if (room.getStatus() != GameStatus.WAITING) {
            throw new GameException("游戏已开始，无法加入", "GAME_ALREADY_STARTED");
        }
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new GameException("房间已满", "ROOM_FULL");
        }
        
        // 添加玩家到房间
        if (!room.getPlayers().contains(playerId)) {
            logger.info("添加玩家到房间: " + playerId);
            room.addPlayer(playerId);
            
            // 同步房间数据
            syncRoomData();
            
            // 发送状态更新
            gameService.sendGameStateUpdate(roomId);
        } else {
            logger.info("玩家已在房间中: " + playerId);
        }
        return room;
    }

   /**
     * 玩家离开房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void leaveRoom(String roomId, String playerId) {
        if (roomId == null || playerId == null) {
            throw new IllegalArgumentException("房间ID和玩家ID不能为空");
        }
        
        logger.info("玩家 {} 尝试离开房间 {}", playerId, roomId);
        
        GameRoom room = getRoom(roomId);
        if (room == null) {
            // 房间不存在，可能已经被解散
            logger.error("玩家 {} 尝试离开不存在的房间 {}", playerId, roomId);
            return;
        }
        
        // 检查玩家是否在房间中
        if (!room.getPlayers().contains(playerId)) {
            logger.info("玩家 {} 不在房间 {} 中", playerId, roomId);
            return;
        }
        
        // 处理玩家手牌（如果游戏已经开始）
        if (room.getStatus() == GameStatus.PLAYING && room.getPlayerHands() != null) {
            // 将玩家手牌放入底盘
            List<Card> playerCards = room.getPlayerHands().remove(playerId);
            if (playerCards != null && !playerCards.isEmpty()) {
                logger.info("将玩家 {} 的 {} 张手牌放入底盘", playerId, playerCards.size());
                if (room.getCurrentPile() == null) {
                    room.setCurrentPile(new ArrayList<>());
                }
                room.getCurrentPile().addAll(playerCards);
            }
            
            // 如果是当前玩家退出，轮到下一个玩家
            if (playerId.equals(room.getCurrentPlayer()) && !room.getPlayers().isEmpty()) {
                // 先移除当前玩家，再计算下一个玩家
                room.removePlayer(playerId);
                
                if (!room.getPlayers().isEmpty()) {
                    int nextIndex = 0; // 默认从第一个玩家开始
                    room.setCurrentPlayer(room.getPlayers().get(nextIndex));
                    room.setCurrentPlayerIndex(nextIndex);
                    logger.info("当前玩家退出，轮到下一个玩家: {}", room.getCurrentPlayer());
                }
                
                // 发送游戏状态更新
                gameService.sendGameStateUpdate(roomId);
                return; // 已经处理完成，不需要执行后续的removePlayer
            }
        }
        
        // 从房间移除玩家
        room.removePlayer(playerId);
        logger.info("玩家 {} 离开房间 {}", playerId, roomId);
        
        // 如果玩家是房主，更换房主
        if (playerId.equals(room.getHostId()) && !room.getPlayers().isEmpty()) {
            // 找出第一个不是机器人的玩家作为新房主
            String newHostId = findNewHost(room);
            room.setHostId(newHostId);
            logger.info("房间 {} 更换房主为 {}", roomId, newHostId);
            
            // 发送房主变更通知
            try {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/notify", 
                    Map.of("type", "HOST_CHANGED", "newHostId", newHostId));
            } catch (Exception e) {
                logger.error("发送房主变更通知失败: {}", e.getMessage());
            }
        }
        
        // 检查是否应该解散房间
        if (shouldDismissRoom(room)) {
            logger.info("房间 {} 将被解散，因为没有真人玩家", roomId);
            
            // 发送房间解散通知
            try {
                messagingTemplate.convertAndSend("/topic/game/notification/" + roomId, 
                    Map.of("type", "ROOM_DISSOLVED", "content", "房间已解散", "roomId", roomId));
            } catch (Exception e) {
                logger.error("发送房间解散通知失败: {}", e.getMessage());
            }
            
            // 移除房间
            removeRoom(roomId);
        } else {
            // 发送玩家离开通知
            try {
                messagingTemplate.convertAndSend("/topic/room/" + roomId + "/notify", 
                    Map.of("type", "PLAYER_LEFT", "playerId", playerId));
            } catch (Exception e) {
                logger.error("发送玩家离开通知失败: {}", e.getMessage());
            }
            
            // 同步房间数据
            syncRoomData();
            
            // 发送状态更新
            gameService.sendGameStateUpdate(roomId);
        }
    }

    /**
     * 移除房间
     * @param roomId 房间ID
     * @return 是否成功移除
     */
    public boolean removeRoom(String roomId) {
        logger.info("尝试移除房间: {}", roomId);
        
        GameRoom room = rooms.remove(roomId);
        if (room != null) {
            logger.info("成功移除房间: {}", roomId);
            
            // 同步房间数据
            syncRoomData();
            
            return true;
        } else {
            logger.warn("尝试移除不存在的房间: {}", roomId);
            return false;
        }
    }

    /**
     * 判断是否应该解散房间
     * @param room 房间
     * @return 是否应该解散
     */
    private boolean shouldDismissRoom(GameRoom room) {
        // 如果房间中没有玩家，应该解散
        if (room.getPlayers().isEmpty()) {
            return true;
        }
        
        // 如果房间中只有机器人，应该解散
        boolean hasHumanPlayer = false;
        for (String playerId : room.getPlayers()) {
            if (!room.isRobot(playerId)) {
                hasHumanPlayer = true;
                break;
            }
        }
        
        return !hasHumanPlayer;
    }

    /**
     * 找出新房主
     * @param room 房间
     * @return 新房主ID
     */
    private String findNewHost(GameRoom room) {
        // 优先选择非机器人玩家
        for (String playerId : room.getPlayers()) {
            if (!room.isRobot(playerId)) {
                return playerId;
            }
        }
        
        // 如果没有非机器人玩家，选择第一个玩家
        if (!room.getPlayers().isEmpty()) {
            return room.getPlayers().get(0);
        }
        
        return null;
    }

    /**
     * 获取房间
     * @param roomId 房间ID
     * @return 房间
     */
    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 获取所有房间
     * @return 所有房间列表
     */
    public List<GameRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }
    
    /**
     * 获取房间状态
     * @param roomId 房间ID
     * @return 房间状态
     */
    public GameState getGameState(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            return null;
        }
        return room.toGameState();
    }
    
    /**
     * 从所有房间中移除玩家
     * @param playerId 玩家ID
     */
    public void removePlayerFromAllRooms(String playerId) {
        List<GameRoom> playerRooms = getAllRooms().stream()
                .filter(room -> room.getPlayers().contains(playerId))
                .collect(Collectors.toList());
        
        for (GameRoom room : playerRooms) {
            leaveRoom(room.getId(), playerId);
        }
    }
    
    /**
     * 清理玩家的游戏状态
     * @param playerId 玩家ID
     */
    public void cleanupPlayerGameState(String playerId) {
        // 从所有房间中移除玩家
        removePlayerFromAllRooms(playerId);
        
        // 同步房间数据
        syncRoomData();
    }
    
    /**
     * 将GameRoom转换为WebSocketController使用的RoomInfo对象
     * @return RoomInfo列表
     */
    public List<WebSocketController.RoomInfo> getRoomInfoList() {
        List<WebSocketController.RoomInfo> roomInfoList = new ArrayList<>();
        
        for (GameRoom gameRoom : rooms.values()) {
            WebSocketController.RoomInfo roomInfo = new WebSocketController.RoomInfo();
            roomInfo.setId(gameRoom.getId());
            roomInfo.setName(gameRoom.getRoomName());
            roomInfo.setMaxPlayers(gameRoom.getMaxPlayers());
            roomInfo.setHostId(gameRoom.getHostId());
            roomInfo.setPlayerCount(gameRoom.getPlayers().size());
            roomInfo.setPlayers(new ArrayList<>(gameRoom.getPlayers()));
            
            roomInfoList.add(roomInfo);
        }
        
        return roomInfoList;
    }
    
    /**
     * 同步房间数据到所有相关服务
     */
    public void syncRoomData() {
        // 同步到GameService
        gameService.syncRooms(rooms);
        
        // 同步到WebSocketController
        webSocketController.syncRooms(getRoomInfoList());
        
        // 广播房间列表更新
        webSocketController.broadcastRoomList();
    }
    
    /**
     * 从WebSocketController更新房间信息
     * @param roomInfoList WebSocketController中的房间信息列表
     */
    public void updateFromWebSocketController(List<WebSocketController.RoomInfo> roomInfoList) {
        // 仅在必要时更新，避免不必要的同步
        boolean needsSync = false;
        
        // 检查新增或更新的房间
        for (WebSocketController.RoomInfo roomInfo : roomInfoList) {
            GameRoom existingRoom = rooms.get(roomInfo.getId());
            
            if (existingRoom == null) {
                // 新房间，需要创建
                createRoomWithId(roomInfo.getId(), roomInfo.getHostId(), roomInfo.getMaxPlayers(), roomInfo.getName());
                needsSync = true;
            } else {
                // 检查是否需要更新现有房间
                boolean updated = false;
                
                if (!Objects.equals(existingRoom.getHostId(), roomInfo.getHostId())) {
                    existingRoom.setHostId(roomInfo.getHostId());
                    updated = true;
                }
                
                if (existingRoom.getMaxPlayers() != roomInfo.getMaxPlayers()) {
                    existingRoom.setMaxPlayers(roomInfo.getMaxPlayers());
                    updated = true;
                }
                
                if (!Objects.equals(existingRoom.getRoomName(), roomInfo.getName())) {
                    existingRoom.setRoomName(roomInfo.getName());
                    updated = true;
                }
                
                // 检查玩家列表是否有变化
                if (!existingRoom.getPlayers().equals(roomInfo.getPlayers())) {
                    // 更新玩家列表，保留现有玩家的其他状态
                    List<String> currentPlayers = new ArrayList<>(existingRoom.getPlayers());
                    
                    // 移除不在新列表中的玩家
                    for (String player : new ArrayList<>(currentPlayers)) {
                        if (!roomInfo.getPlayers().contains(player)) {
                            existingRoom.removePlayer(player);
                            updated = true;
                        }
                    }
                    
                    // 添加新玩家
                    for (String player : roomInfo.getPlayers()) {
                        if (!currentPlayers.contains(player)) {
                            existingRoom.addPlayer(player);
                            updated = true;
                        }
                    }
                }
                
                if (updated) {
                    needsSync = true;
                }
            }
        }
        
        // 检查需要删除的房间
        Set<String> roomInfoIds = roomInfoList.stream()
                .map(WebSocketController.RoomInfo::getId)
                .collect(Collectors.toSet());
        
        List<String> roomsToRemove = new ArrayList<>();
        for (String roomId : rooms.keySet()) {
            if (!roomInfoIds.contains(roomId)) {
                roomsToRemove.add(roomId);
                needsSync = true;
            }
        }
        
        // 移除不存在的房间
        for (String roomId : roomsToRemove) {
            rooms.remove(roomId);
        }
        
        // 如果有变化，同步数据
        if (needsSync) {
            syncRoomData();
        }
    }
} 