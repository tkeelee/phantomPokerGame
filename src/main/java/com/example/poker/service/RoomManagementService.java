package com.example.poker.service;

import com.example.poker.model.GameRoom;
import com.example.poker.model.GameState;
import com.example.poker.model.GameStatus;
import com.example.poker.model.Card;
import com.example.poker.exception.GameException;
import com.example.poker.constant.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间管理服务
 * 负责处理房间的创建、加入、离开等基本操作
 */
@Service
public class RoomManagementService {
    private static final Logger logger = LoggerFactory.getLogger(RoomManagementService.class);
    
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, GameState> gameStates = new ConcurrentHashMap<>();

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
        state.setGameStatus(GameConstants.GAME_STATUS_WAITING);
        state.getPlayers().add(hostId);
        
        // 存储房间和状态
        rooms.put(room.getId(), room);
        gameStates.put(room.getId(), state);
        
        logger.info("房间创建成功 - 房间ID: {}", room.getId());
        return room;
    }

    /**
     * 加入房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 加入的房间
     */
    public GameRoom joinRoom(String roomId, String playerId) {
        logger.info("玩家加入房间 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            logger.error("房间不存在 - 房间ID: {}", roomId);
            throw new GameException("房间不存在", "ROOM_NOT_FOUND");
        }
        if (room.getStatus() != GameStatus.WAITING) {
            logger.error("游戏已开始，无法加入 - 房间ID: {}, 状态: {}", roomId, room.getStatus());
            throw new GameException("游戏已开始，无法加入", "GAME_ALREADY_STARTED");
        }
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            logger.error("房间已满 - 房间ID: {}, 当前人数: {}, 最大人数: {}", 
                roomId, room.getPlayers().size(), room.getMaxPlayers());
            throw new GameException("房间已满", "ROOM_FULL");
        }
        
        // 添加玩家到房间
        if (!room.getPlayers().contains(playerId)) {
            room.addPlayer(playerId);
            
            // 更新游戏状态
            GameState state = gameStates.get(roomId);
            if (state != null && !state.getPlayers().contains(playerId)) {
                state.getPlayers().add(playerId);
            }
            
            logger.info("玩家成功加入房间 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        }
        return room;
    }

    /**
     * 离开房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void leaveRoom(String roomId, String playerId) {
        logger.info("玩家离开房间 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            logger.error("房间不存在 - 房间ID: {}", roomId);
            throw new RuntimeException("房间不存在");
        }
        
        // 处理玩家退出
        room.handlePlayerExit(playerId);
        
        // 如果游戏已开始，将玩家手牌放入底盘
        GameState state = gameStates.get(roomId);
        if (state != null) {
            List<Card> playerCards = state.getPlayerHands().remove(playerId);
            if (playerCards != null && !playerCards.isEmpty()) {
                state.getCurrentPile().addAll(playerCards);
            }
            state.getPlayers().remove(playerId);
            
            // 如果是房主退出，转移房主
            if (playerId.equals(state.getHostId()) && !state.getPlayers().isEmpty()) {
                state.setHostId(state.getPlayers().get(0));
                room.setHostId(state.getPlayers().get(0));
            }
            
            // 如果是当前玩家退出，轮到下一个玩家
            if (playerId.equals(state.getCurrentPlayer()) && !state.getPlayers().isEmpty()) {
                int nextIndex = (state.getPlayers().indexOf(state.getCurrentPlayer()) + 1) % state.getPlayers().size();
                state.setCurrentPlayer(state.getPlayers().get(nextIndex));
            }
        }
        
        // 如果房间没有玩家了，删除房间
        if (room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
            gameStates.remove(roomId);
            logger.info("房间已清空并删除 - 房间ID: {}", roomId);
        }
    }

    /**
     * 获取房间
     * @param roomId 房间ID
     * @return 房间
     */
    public GameRoom getRoom(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            logger.error("房间不存在 - 房间ID: {}", roomId);
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
            logger.error("游戏状态不存在 - 房间ID: {}", roomId);
            throw new RuntimeException("游戏状态不存在");
        }
        return state;
    }
} 