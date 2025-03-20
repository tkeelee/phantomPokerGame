package com.example.poker.service;

import com.example.poker.model.*;
import com.example.poker.exception.GameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏服务类，负责处理游戏的主要逻辑
 */
@Service
public class GameService {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, GameState> gameStates = new ConcurrentHashMap<>();
    
    @Autowired
    private DeckService deckService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * 创建游戏房间
     * @param hostId 房主ID
     * @param maxPlayers 最大玩家数
     * @return 创建的房间
     */
    public GameRoom createRoom(String hostId, int maxPlayers) {
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
        
        // 存储房间和状态
        rooms.put(room.getId(), room);
        gameStates.put(room.getId(), state);
        
        return room;
    }

    /**
     * 加入房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 加入的房间
     */
    public GameRoom joinRoom(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new GameException("房间不存在", "ROOM_NOT_FOUND");
        }
        if (room.getStatus() != GameStatus.WAITING) {
            throw new GameException("游戏已开始，无法加入", "GAME_ALREADY_STARTED");
        }
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
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
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        }
        return room;
    }

    /**
     * 离开房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void leaveRoom(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
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
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        }
        
        // 如果房间没有玩家了，删除房间
        if (room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
            gameStates.remove(roomId);
        }
    }

    /**
     * 玩家准备
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 房间
     */
    public GameRoom playerReady(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        if (!room.getPlayers().contains(playerId)) {
            throw new RuntimeException("玩家不在房间中");
        }
        
        // 设置玩家准备状态
        if (!room.getReadyPlayers().contains(playerId)) {
            room.getReadyPlayers().add(playerId);
        }
        
        // 如果所有玩家都准备好了，房主可以开始游戏
        if (room.getReadyPlayers().size() == room.getPlayers().size() && room.getPlayers().size() >= 2) {
            room.setStatus(GameStatus.READY);
            
            // 更新游戏状态
            GameState state = gameStates.get(roomId);
            if (state != null) {
                state.setStatus(GameStatus.READY);
                state.setGameStatus("READY");
                
                // 发送状态更新
                sendGameStateUpdate(roomId);
            }
        }
        
        return room;
    }

    /**
     * 开始游戏
     * @param roomId 房间ID
     * @param playerId 发起开始的玩家ID（必须是房主）
     * @param deckCount 使用的牌堆数量
     * @return 房间
     */
    public GameRoom startGame(String roomId, String playerId, int deckCount) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        if (!playerId.equals(room.getHostId())) {
            throw new RuntimeException("只有房主可以开始游戏");
        }
        if (room.getStatus() != GameStatus.READY) {
            throw new RuntimeException("玩家未全部准备");
        }
        if (room.getPlayers().size() < 2) {
            throw new RuntimeException("玩家人数不足");
        }
        
        // 生成牌堆
        List<Card> deck = deckService.generateShuffledDecks(deckCount);
        
        // 分发手牌
        Map<String, List<Card>> playerHands = deckService.dealCards(deck, room.getPlayers());
        
        // 更新房间状态
        room.setStatus(GameStatus.PLAYING);
        room.setCardDeck(new ArrayList<>());  // 牌已经分完了
        room.setPlayerHands(playerHands);
        room.setCurrentPlayerIndex(0);  // 房主先手
        room.setCurrentPile(new ArrayList<>());
        
        // 更新游戏状态
        GameState state = gameStates.get(roomId);
        if (state != null) {
            state.setStatus(GameStatus.PLAYING);
            state.setGameStatus("PLAYING");
            state.setPlayerHands(playerHands);
            state.setCurrentPlayer(room.getPlayers().get(0));
            state.setCurrentPlayerIndex(0);
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        }
        
        return room;
    }

    /**
     * 玩家出牌
     * @param roomId 房间ID
     * @param message 包含出牌信息的消息
     * @return 房间
     */
    public GameRoom playCards(String roomId, GameMessage message) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        if (room.getStatus() != GameStatus.PLAYING) {
            throw new RuntimeException("游戏未开始");
        }
        if (!room.getPlayers().contains(message.getPlayerId())) {
            throw new RuntimeException("玩家不在房间中");
        }
        
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        
        // 检查是否是当前玩家的回合
        if (!message.getPlayerId().equals(state.getCurrentPlayer())) {
            throw new RuntimeException("不是你的回合");
        }
        
        // 检查玩家是否有这些牌
        List<Card> playerCards = state.getPlayerHands().get(message.getPlayerId());
        List<Card> selectedCards = message.getCards();
        if (!hasAllCards(playerCards, selectedCards)) {
            throw new RuntimeException("你没有这些牌");
        }
        
        // 从玩家手牌中移除这些牌
        removeCards(playerCards, selectedCards);
        
        // 添加到当前牌堆
        state.getCurrentPile().addAll(selectedCards);
        
        // 记录玩家声明
        state.setLastClaim("玩家" + message.getPlayerId() + 
                           "打出" + message.getDeclaredCount() + 
                           "张" + message.getDeclaredValue());
        state.setDeclaredValue(message.getDeclaredValue());
        
        // 检查是否胜利（手牌为空）
        if (playerCards.isEmpty()) {
            handlePlayerWin(roomId, message.getPlayerId());
            return room;
        }
        
        // 切换到下一个玩家
        int nextIndex = (state.getPlayers().indexOf(message.getPlayerId()) + 1) % state.getPlayers().size();
        state.setCurrentPlayer(state.getPlayers().get(nextIndex));
        state.setCurrentPlayerIndex(nextIndex);
        
        // 发送状态更新
        sendGameStateUpdate(roomId);
        
        return room;
    }

    /**
     * 玩家质疑
     * @param roomId 房间ID
     * @param message 包含质疑信息的消息
     * @return 房间
     */
    public GameRoom challenge(String roomId, GameMessage message) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        if (room.getStatus() != GameStatus.PLAYING) {
            throw new RuntimeException("游戏未开始");
        }
        if (!room.getPlayers().contains(message.getPlayerId())) {
            throw new RuntimeException("玩家不在房间中");
        }
        
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        
        // 获取当前牌堆中的牌
        List<Card> currentPile = state.getCurrentPile();
        if (currentPile.isEmpty()) {
            throw new RuntimeException("当前没有可质疑的牌");
        }
        
        // 获取声明的值
        String declaredValue = state.getDeclaredValue();
        if (declaredValue == null) {
            throw new RuntimeException("当前没有声明");
        }
        
        // 判断质疑是否成功
        boolean challengeSuccess = !isClaimValid(currentPile, declaredValue);
        
        // 处理质疑结果
        if (challengeSuccess) {
            // 质疑成功，出牌者收走底盘
            String targetPlayerId = message.getTargetPlayerId(); // 被质疑的玩家
            punishPlayer(state, targetPlayerId);
            // 质疑玩家获得出牌权
            state.setCurrentPlayer(message.getPlayerId());
            state.setCurrentPlayerIndex(state.getPlayers().indexOf(message.getPlayerId()));
        } else {
            // 质疑失败，质疑者收走底盘
            punishPlayer(state, message.getPlayerId());
            // 出牌者获得出牌权
            state.setCurrentPlayer(message.getTargetPlayerId());
            state.setCurrentPlayerIndex(state.getPlayers().indexOf(message.getTargetPlayerId()));
        }
        
        // 清空当前牌堆
        state.getCurrentPile().clear();
        state.setLastClaim(null);
        state.setDeclaredValue(null);
        
        // 发送状态更新
        sendGameStateUpdate(roomId);
        
        return room;
    }

    /**
     * 玩家过牌
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 房间
     */
    public GameRoom pass(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        if (room.getStatus() != GameStatus.PLAYING) {
            throw new RuntimeException("游戏未开始");
        }
        if (!room.getPlayers().contains(playerId)) {
            throw new RuntimeException("玩家不在房间中");
        }
        
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        
        // 检查是否是当前玩家的回合
        if (!playerId.equals(state.getCurrentPlayer())) {
            throw new RuntimeException("不是你的回合");
        }
        
        // 切换到下一个玩家
        int nextIndex = (state.getPlayers().indexOf(playerId) + 1) % state.getPlayers().size();
        state.setCurrentPlayer(state.getPlayers().get(nextIndex));
        state.setCurrentPlayerIndex(nextIndex);
        
        // 发送状态更新
        sendGameStateUpdate(roomId);
        
        return room;
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
     * 获取房间中的玩家状态列表
     * @param roomId 房间ID
     * @return 玩家状态列表
     */
    public List<PlayerState> getPlayers(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        
        List<PlayerState> players = new ArrayList<>();
        for (String playerId : room.getPlayers()) {
            PlayerState playerState = new PlayerState(playerId);
            playerState.setHand(state.getPlayerHands().getOrDefault(playerId, new ArrayList<>()));
            playerState.setReady(room.getReadyPlayers().contains(playerId));
            playerState.setHost(playerId.equals(room.getHostId()));
            players.add(playerState);
        }
        
        return players;
    }
    
    /**
     * 检查玩家是否拥有所有指定的牌
     * @param playerCards 玩家的手牌
     * @param selectedCards 选中的牌
     * @return 是否拥有所有牌
     */
    private boolean hasAllCards(List<Card> playerCards, List<Card> selectedCards) {
        // 创建玩家手牌的副本
        List<Card> playerCardsCopy = new ArrayList<>(playerCards);
        
        // 检查是否包含每一张选中的牌
        for (Card card : selectedCards) {
            if (!playerCardsCopy.remove(card)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 从玩家手牌中移除指定的牌
     * @param playerCards 玩家的手牌
     * @param cardsToRemove 要移除的牌
     */
    private void removeCards(List<Card> playerCards, List<Card> cardsToRemove) {
        for (Card card : cardsToRemove) {
            playerCards.remove(card);
        }
    }
    
    /**
     * 检查声明是否有效
     * @param cards 实际打出的牌
     * @param declaredValue 声明的值
     * @return 声明是否有效
     */
    private boolean isClaimValid(List<Card> cards, String declaredValue) {
        int count = 0;
        
        // 计算实际匹配声明的牌的数量
        for (Card card : cards) {
            if (card.isJoker() || card.getRank().equals(declaredValue)) {
                count++;
            }
        }
        
        // 如果所有牌都符合声明，则声明有效
        return count == cards.size();
    }
    
    /**
     * 处理玩家胜利
     * @param roomId 房间ID
     * @param playerId 胜利的玩家ID
     */
    private void handlePlayerWin(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        GameState state = gameStates.get(roomId);
        
        if (room != null && state != null) {
            // 移除胜利的玩家
            state.getPlayers().remove(playerId);
            
            // 如果只剩最后一个玩家，游戏结束
            if (state.getPlayers().size() <= 1) {
                room.setStatus(GameStatus.FINISHED);
                state.setStatus(GameStatus.FINISHED);
                state.setGameStatus("FINISHED");
            }
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        }
    }
    
    /**
     * 惩罚玩家（将底盘牌加入其手牌）
     * @param state 游戏状态
     * @param playerId 被惩罚的玩家ID
     */
    private void punishPlayer(GameState state, String playerId) {
        List<Card> playerHand = state.getPlayerHands().get(playerId);
        if (playerHand != null && state.getCurrentPile() != null) {
            playerHand.addAll(state.getCurrentPile());
        }
    }
    
    /**
     * 发送游戏状态更新到客户端
     * @param roomId 房间ID
     */
    private void sendGameStateUpdate(String roomId) {
        GameState state = gameStates.get(roomId);
        if (state != null && messagingTemplate != null) {
            messagingTemplate.convertAndSend("/topic/game/state/" + roomId, state);
        }
    }
}