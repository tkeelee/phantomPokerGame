package com.example.poker.service;

import java.util.concurrent.ConcurrentHashMap;
import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import java.util.List;
import java.util.ArrayList;

/**
 * 房间管理服务类
 * 1. 维护房间状态映射表
 * 2. 处理房主变更逻辑
 * 3. 管理玩家进出房间
 */
public class RoomService {
    private final DeckService deckService;
    
    // 房间存储结构：房间ID -> 游戏状态
    private static final ConcurrentHashMap<String, GameState> roomMap = new ConcurrentHashMap<>();

    public RoomService(DeckService deckService) {
        this.deckService = deckService;
    }

    /**
     * 创建新房间
     * @param hostId 房主ID
     * @return 新创建的房间ID
     */
    public synchronized String createRoom(String hostId) {
        String roomId = generateRoomId();
        GameState state = new GameState();
        state.setHostId(hostId);
        state.getPlayers().add(hostId);
        roomMap.put(roomId, state);
        return roomId;
    }

    /**
     * 处理玩家加入房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void joinRoom(String roomId, String playerId) {
        GameState state = roomMap.get(roomId);
        if (state != null && state.getGameStatus().equals("WAITING")) {
            state.getPlayers().add(playerId);
        }
    }

    /**
     * 房主转移逻辑
     * @param roomId 房间ID
     */
    public void transferHost(String roomId) {
        GameState state = roomMap.get(roomId);
        if (state != null) {
            // 寻找第一个在线玩家作为新房主
            state.setHostId(state.getPlayers().stream()
                .filter(p -> !p.equals(state.getHostId()))
                .findFirst()
                .orElse(null));
        }
    }

    public void startGame(String roomId, int deckCount) {
        GameState state = roomMap.get(roomId);
        if (state != null && state.getHostId() != null) {
            // 初始化牌堆并分发手牌
            List<Card> fullDeck = deckService.generateShuffledDecks(deckCount);
            int playerCount = state.getPlayers().size();
            int cardsPerPlayer = fullDeck.size() / playerCount;

            // 分发手牌
            for (int i = 0; i < playerCount; i++) {
                List<Card> hand = new ArrayList<>(fullDeck.subList(
                    i * cardsPerPlayer, 
                    (i + 1) * cardsPerPlayer));
                state.getPlayerHands().put(state.getPlayers().get(i), hand);
            }

            // 设置游戏状态
            state.setGameStatus("IN_PROGRESS");
            state.setCurrentPlayer(state.getHostId());
        }
    }

    /**
     * 获取房间状态
     * @param roomId 房间ID
     * @return 对应的游戏状态对象
     */
    public GameState getRoomState(String roomId) {
        return roomMap.get(roomId);
    }

    private String generateRoomId() {
        return String.valueOf(System.currentTimeMillis());
    }
}