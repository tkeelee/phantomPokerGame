package com.example.poker.service;

import com.example.poker.model.GameRoom;
import com.example.poker.model.GameState;
import com.example.poker.model.GameStatus;
import com.example.poker.exception.GameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 房间管理服务
 * 负责处理房间的创建、加入、离开等基本操作
 */
@Service
public class RoomManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoomManagementService.class);
    
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, GameState> gameStates = new ConcurrentHashMap<>();

    @Autowired
    private GameService gameService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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
        room.setPlayers(new ArrayList<>());
        room.addPlayer(hostId);
        room.setCurrentPlayerIndex(0);
        
        // 初始化房间状态
        GameState state = new GameState();
        state.setRoomId(room.getId());
        state.setHostId(hostId);
        state.setStatus(GameStatus.WAITING);
        state.setGameStatus("WAITING");
        state.getPlayers().add(hostId);
        state.setMaxPlayers(maxPlayers);
        state.setRobotCount(0); // 初始化机器人数量为0
        
        // 存储房间和状态
        rooms.put(room.getId(), room);
        gameStates.put(room.getId(), state);
        
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
        room.setPlayers(new ArrayList<>());
        room.addPlayer(hostId);
        room.setCurrentPlayerIndex(0);
        
        // 初始化房间状态
        GameState state = new GameState();
        state.setRoomId(roomId);
        state.setHostId(hostId);
        state.setStatus(GameStatus.WAITING);
        state.setGameStatus("WAITING");
        state.getPlayers().add(hostId);
        state.setRoomName(roomName);
        state.setMaxPlayers(maxPlayers);
        
        // 存储房间和状态
        rooms.put(roomId, room);
        gameStates.put(roomId, state);
        
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
            
            // 更新游戏状态
            GameState state = gameStates.get(roomId);
            if (state != null && !state.getPlayers().contains(playerId)) {
                state.getPlayers().add(playerId);
            }
            
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
        
        GameRoom room = getRoom(roomId);
        if (room == null) {
            // 房间不存在，可能已经被解散
            logger.error("玩家 {} 尝试离开不存在的房间 {}", playerId, roomId);
            return;
        }
        
        // 从房间移除玩家
        room.removePlayer(playerId);
        logger.info("玩家 {} 离开房间 {}", playerId, roomId);
        
        // 获取房间状态并更新
        GameState state = getGameState(roomId);
        List<String> players = state.getPlayers();
        players.remove(playerId);
        state.setPlayers(players);
        
        // 如果玩家是房主，更换房主
        if (playerId.equals(room.getHostId()) && !room.getPlayers().isEmpty()) {
            // 找出第一个不是机器人的玩家作为新房主
            String newHostId = findNewHost(room);
            room.setHostId(newHostId);
            state.setHostId(newHostId);
            logger.info("房间 {} 更换房主为 {}", roomId, newHostId);
        }
        
        // 如果房间中没有真实玩家了（只剩机器人或完全没有玩家），解散房间
        if (shouldDismissRoom(room)) {
            logger.info("房间 {} 中没有真实玩家，自动解散", roomId);
            removeRoom(roomId);
            return;
        }
        
        // 广播房间状态更新
        gameService.broadcastRoomState(room);
        messagingTemplate.convertAndSend("/topic/game-state/" + roomId, state);
    }

    /**
     * 从系统中移除房间
     * 
     * @param roomId 要移除的房间ID
     * @return 是否成功移除
     */
    public boolean removeRoom(String roomId) {
        // 检查房间是否存在
        if (!rooms.containsKey(roomId)) {
            logger.warn("房间不存在: {}", roomId);
            return false;
        }
        
        try {
            // 获取房间对象
            GameRoom room = rooms.get(roomId);
            
            // 确保房间中没有玩家
            if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
                logger.warn("房间 {} 中仍有玩家，无法删除", roomId);
                return false;
            }
            
            // 从房间集合中移除
            rooms.remove(roomId);
            
            // 从游戏状态集合中移除
            gameStates.remove(roomId);
            
            logger.info("房间已从系统中移除: {}", roomId);
            return true;
        } catch (Exception e) {
            logger.error("移除房间时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 判断房间是否应该被解散
     * @param room 游戏房间
     * @return 是否应解散
     */
    private boolean shouldDismissRoom(GameRoom room) {
        // 如果没有玩家，直接解散
        if (room.getPlayers() == null || room.getPlayers().isEmpty()) {
            return true;
        }
        
        // 检查是否只剩下机器人
        for (String playerId : room.getPlayers()) {
            if (!room.isRobot(playerId)) {
                // 还有真实玩家，不解散
                return false;
            }
        }
        
        // 只剩下机器人，应该解散
        return true;
    }

    /**
     * 查找新房主
     * @param room 游戏房间
     * @return 新房主ID
     */
    private String findNewHost(GameRoom room) {
        // 优先选择真实玩家作为房主
        for (String playerId : room.getPlayers()) {
            if (!room.isRobot(playerId)) {
                return playerId;
            }
        }
        
        // 如果没有真实玩家，就用第一个玩家（可能是机器人）
        if (!room.getPlayers().isEmpty()) {
            return room.getPlayers().get(0);
        }
        
        // 没有任何玩家，返回null（房间将被解散）
        return null;
    }

    /**
     * 获取房间
     * @param roomId 房间ID
     * @return 房间
     */
    public GameRoom getRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        return room;
    }
    
    /**
     * 获取所有游戏房间列表
     * @return 房间列表
     */
    public List<GameRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 获取游戏状态
     * @param roomId 房间ID
     * @return 游戏状态
     */
    public GameState getGameState(String roomId) {
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        return state;
    }

       /**
     * 从所有房间中移除指定玩家
     * @param playerId 要移除的玩家ID
     */
    public void removePlayerFromAllRooms(String playerId) {
        List<GameRoom> playerRooms = getAllRooms().stream()
            .filter(room -> room.getPlayers().contains(playerId))
            .collect(Collectors.toList());
            
        for (GameRoom room : playerRooms) {
            try {
                leaveRoom(room.getId(), playerId);
                logger.info("已将玩家 {} 从房间 {} 中移除", playerId, room.getId());
            } catch (Exception e) {
                logger.error("从房间 {} 移除玩家 {} 时出错: {}", room.getId(), playerId, e.getMessage());
            }
        }
    }

    /**
     * 清理玩家的游戏状态
     * @param playerId 玩家ID
     */
    public void cleanupPlayerGameState(String playerId) {
        try {
            // 从所有房间中移除玩家
            removePlayerFromAllRooms(playerId);
            
            // 清理玩家的游戏状态
            for (GameState state : gameStates.values()) {
                if (state.getPlayers().contains(playerId)) {
                    state.getPlayers().remove(playerId);
                    state.getPlayerHands().remove(playerId);
                    state.getReadyPlayers().remove(playerId);
                    
                    // 如果是当前玩家，更新为下一个玩家
                    if (playerId.equals(state.getCurrentPlayer())) {
                        List<String> players = state.getPlayers();
                        if (!players.isEmpty()) {
                            int nextIndex = (players.indexOf(state.getCurrentPlayer()) + 1) % players.size();
                            state.setCurrentPlayer(players.get(nextIndex));
                        }
                    }
                }
            }
            
            logger.info("已清理玩家 {} 的游戏状态", playerId);
        } catch (Exception e) {
            logger.error("清理玩家游戏状态时出错: {}", e.getMessage(), e);
        }
    }
} 