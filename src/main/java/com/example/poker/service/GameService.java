package com.example.poker.service;

import com.example.poker.model.*;
import com.example.poker.exception.GameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.Collectors;

/**
 * 游戏服务类，负责处理游戏的主要逻辑
 */
@Service
public class GameService {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, GameState> gameStates = new ConcurrentHashMap<>();
    private String currentRoomId;
    
    @Autowired
    private DeckService deckService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

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
        state.setMaxPlayers(maxPlayers);
        state.setRobotCount(0); // 初始化机器人数量为0
        
        // 存储房间和状态
        rooms.put(room.getId(), room);
        gameStates.put(room.getId(), state);
        
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
        log.info("使用指定ID创建房间: " + roomId);
        
        // 检查房间是否已存在
        if (rooms.containsKey(roomId)) {
            log.info("房间已存在，返回现有房间: " + roomId);
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
        
        log.info("成功创建指定ID的房间: " + roomId);
        log.info("当前所有房间: " + rooms.keySet());
        
        return room;
    }

    /**
     * 加入房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @return 加入的房间
     */
    public GameRoom joinRoom(String roomId, String playerId) {
        log.info("尝试加入房间, roomId: " + roomId + ", playerId: " + playerId);
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            log.error("房间不存在: " + roomId);
            log.info("当前存在的房间: " + rooms.keySet());
            throw new GameException("房间不存在：" + roomId, "ROOM_NOT_FOUND");
        }
        
        log.info("找到房间, 状态: " + room.getStatus() + ", 玩家: " + room.getPlayers() + ", 最大人数: " + room.getMaxPlayers());
        
        if (room.getStatus() != GameStatus.WAITING) {
            throw new GameException("游戏已开始，无法加入", "GAME_ALREADY_STARTED");
        }
        if (room.getPlayers().size() >= room.getMaxPlayers()) {
            throw new GameException("房间已满", "ROOM_FULL");
        }
        
        // 添加玩家到房间
        if (!room.getPlayers().contains(playerId)) {
            log.info("添加玩家到房间: " + playerId);
            room.addPlayer(playerId);
            
            // 更新游戏状态
            GameState state = gameStates.get(roomId);
            if (state != null && !state.getPlayers().contains(playerId)) {
                state.getPlayers().add(playerId);
            }
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        } else {
            log.info("玩家已在房间中: " + playerId);
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
            log.error("玩家 {} 尝试离开不存在的房间 {}", playerId, roomId);
            return;
        }
        
        // 从房间移除玩家
        room.removePlayer(playerId);
        log.info("玩家 {} 离开房间 {}", playerId, roomId);
        
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
            log.info("房间 {} 更换房主为 {}", roomId, newHostId);
        }
        
        // 如果房间中没有真实玩家了（只剩机器人或完全没有玩家），解散房间
        if (shouldDismissRoom(room)) {
            log.info("房间 {} 中没有真实玩家，自动解散", roomId);
            removeRoom(roomId);
            return;
        }
        
        // 广播房间状态更新
        broadcastRoomState(room);
        messagingTemplate.convertAndSend("/topic/game-state/" + roomId, state);
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
        
        // 如果已有上一玩家出牌且有声明值，则当前玩家必须遵守相同的声明值
        // 除非所有其他玩家都已过牌，此时玩家可以自由选择牌值
        if (state.getDeclaredValue() != null && !state.haveAllPlayersPassed()) {
            if (!message.getDeclaredValue().equals(state.getDeclaredValue())) {
                throw new RuntimeException("必须声明与上一玩家相同的牌值: " + state.getDeclaredValue());
            }
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
        
        // 记录上一次出牌的玩家ID
        state.setLastPlayerId(message.getPlayerId());
        
        // 清空过牌玩家列表，因为新的一轮出牌开始了
        state.clearPassedPlayers();
        
        // 检查是否有其他玩家已经打完手牌但仍在等待确认
        // 当其他玩家出牌后，这些玩家满足"被质疑失败或其他玩家出牌"的条件
        List<String> pendingWinners = new ArrayList<>(state.getWinners());
        for (String winner : pendingWinners) {
            // 如果赢家不是当前出牌的玩家，且仍在玩家列表中
            if (!winner.equals(message.getPlayerId()) && state.getPlayers().contains(winner)) {
                // 将该玩家从当前玩家列表中移除（确认胜利）
                state.getPlayers().remove(winner);
            }
        }
        
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
            
            // 如果被质疑的玩家之前已经打完手牌（手牌为空但仍在players列表中等待确认）
            // 则需要将其从winners列表中移除，因为质疑成功意味着他需要重新开始
            List<Card> targetPlayerHand = state.getPlayerHands().get(targetPlayerId);
            if (targetPlayerHand.isEmpty() && state.getWinners().contains(targetPlayerId)) {
                state.getWinners().remove(targetPlayerId);
            }
            
            // 质疑玩家获得出牌权
            state.setCurrentPlayer(message.getPlayerId());
            state.setCurrentPlayerIndex(state.getPlayers().indexOf(message.getPlayerId()));
        } else {
            // 质疑失败，质疑者收走底盘
            punishPlayer(state, message.getPlayerId());
            // 被质疑玩家ID
            String targetPlayerId = message.getTargetPlayerId();
            // 出牌者获得出牌权
            state.setCurrentPlayer(targetPlayerId);
            state.setCurrentPlayerIndex(state.getPlayers().indexOf(targetPlayerId));
            
            // 如果被质疑的玩家手牌为空，且在winners列表中，此时应确认其真正赢得比赛
            // 因为他被质疑但质疑失败，满足"出完手牌后无人质疑成功"的条件
            List<Card> targetPlayerHand = state.getPlayerHands().get(targetPlayerId);
            if (targetPlayerHand.isEmpty() && state.getWinners().contains(targetPlayerId)) {
                // 将玩家从游戏中移除，但保留在winners列表中
                state.getPlayers().remove(targetPlayerId);
            }
        }
        
        // 清空当前牌堆
        state.getCurrentPile().clear();
        state.setLastClaim(null);
        state.setDeclaredValue(null);
        state.setLastPlayerId(null);
        
        // 清空过牌玩家列表，因为新的一轮开始了
        state.clearPassedPlayers();
        
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
        
        // 记录该玩家已过牌
        state.addPassedPlayer(playerId);
        
        // 切换到下一个玩家
        int nextIndex = (state.getPlayers().indexOf(playerId) + 1) % state.getPlayers().size();
        state.setCurrentPlayer(state.getPlayers().get(nextIndex));
        state.setCurrentPlayerIndex(nextIndex);
        
        // 如果下一个玩家是最初出牌的玩家，说明已经轮了一圈都过牌了
        // 此时清空过牌记录，给这个玩家重新出牌的自由度
        if (nextIndex == state.getPlayers().indexOf(state.getLastPlayerId())) {
            state.clearPassedPlayers();
        }
        
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
            // 检查是否有人质疑当前出牌
            if (state.getLastClaim() != null && state.getLastPlayerId() != null 
                && state.getLastPlayerId().equals(playerId)) {
                // 添加玩家到胜利列表，但不从游戏中移除
                // 只有当被质疑失败或其他玩家出牌后，才算真正胜利
                if (!state.getWinners().contains(playerId)) {
                    state.getWinners().add(playerId);
                }
            } else {
                // 如果是被质疑失败或其他玩家已出牌，则算真正胜利
                // 将玩家从当前玩家列表中移除，但保留在胜利列表中
                if (!state.getWinners().contains(playerId)) {
                    state.getWinners().add(playerId);
                }
                state.getPlayers().remove(playerId);
            }
            
            // 如果只剩最后一个玩家有手牌，游戏结束
            if (room.checkGameEnd()) {
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

    /**
     * 获取当前玩家ID
     * @return 当前玩家ID
     */
    private String getCurrentPlayerId() {
        // 从当前房间状态中获取当前玩家ID
        GameState state = gameStates.get(currentRoomId);
        if (state != null) {
            return state.getCurrentPlayer();
        }
        return null;
    }

    /**
     * 添加机器人到房间
     * @param roomId 房间ID
     * @param count 机器人数量
     * @param difficulty 机器人难度
     * @param playerId 当前操作的玩家ID
     */
    public void addRobotsToRoom(String roomId, int count, String difficulty, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }

        // 检查是否是房主
        if (!room.getHostId().equals(playerId)) {
            throw new IllegalStateException("只有房主可以添加机器人");
        }

        // 检查游戏状态
        if (room.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("只能在等待状态添加机器人");
        }

        // 检查玩家数量限制
        if (room.getPlayers().size() + count > room.getMaxPlayers()) {
            throw new IllegalStateException("添加机器人后超过最大玩家数");
        }

        // 检查难度设置
        if (!isValidDifficulty(difficulty)) {
            throw new IllegalArgumentException("无效的机器人难度设置");
        }

        room.addRobots(count, difficulty);
        
        // 广播房间状态更新
        broadcastRoomState(room);
    }

    /**
     * 检查机器人难度设置是否有效
     * @param difficulty 难度设置
     * @return 是否有效
     */
    private boolean isValidDifficulty(String difficulty) {
        return difficulty != null && 
               (difficulty.equals("EASY") || 
                difficulty.equals("MEDIUM") || 
                difficulty.equals("HARD"));
    }

    /**
     * 移除房间中的所有机器人
     * @param roomId 房间ID
     * @param playerId 当前操作的玩家ID
     */
    public void removeRobotsFromRoom(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("房间不存在");
        }

        // 检查是否是房主
        if (!room.getHostId().equals(playerId)) {
            throw new IllegalStateException("只有房主可以移除机器人");
        }

        // 检查游戏状态
        if (room.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("只能在等待状态移除机器人");
        }

        room.removeAllRobots();
        
        // 检查房间是否需要解散
        if (room.getPlayers().isEmpty() || room.getPlayers().stream().allMatch(room::isRobot)) {
            // 如果房间内只剩下机器人或没有玩家，解散房间
            rooms.remove(roomId);
            gameStates.remove(roomId);
            
            // 广播房间解散消息
            if (messagingTemplate != null) {
                GameNotification notification = new GameNotification();
                notification.setType("ROOM_DISSOLVED");
                notification.setContent("房间已解散");
                notification.setRoomId(roomId);
                messagingTemplate.convertAndSend("/topic/game/notification/" + roomId, notification);
            }
        } else {
            // 广播房间状态更新
            broadcastRoomState(room);
        }
    }

    /**
     * 处理机器人的回合
     * @param room 游戏房间
     * @param gameState 游戏状态
     */
    private void handleRobotTurn(GameRoom room, GameState gameState) {
        String currentPlayerId = room.getCurrentPlayerId();
        if (!room.isRobot(currentPlayerId)) {
            return;
        }

        try {
            // 创建机器人玩家实例
            RobotPlayer robot = new RobotPlayer(
                currentPlayerId,
                "机器人" + currentPlayerId.substring(6), // 从"robot_X"中提取数字
                room.getRobotDifficulty()
            );
            robot.setHand(gameState.getPlayerHands().get(currentPlayerId));

            // 获取上一个声明
            String lastClaim = room.getLastClaim();
            List<Card> currentPile = gameState.getCurrentPile();

            // 根据难度设置延迟时间
            int delay = switch (room.getRobotDifficulty()) {
                case "EASY" -> 2000;    // 简单模式延迟2秒
                case "MEDIUM" -> 1500;  // 中等模式延迟1.5秒
                case "HARD" -> 1000;    // 困难模式延迟1秒
                default -> 1500;
            };

            // 延迟执行，让玩家能看清机器人的操作
            Thread.sleep(delay);

            // 决定是否质疑上一个玩家
            if (lastClaim != null && !currentPile.isEmpty() && robot.decideToChallenge(lastClaim, currentPile)) {
                // 机器人选择质疑
                handleChallenge(room.getId(), currentPlayerId);
                return;
            }

            // 选择要打出的牌
            List<Card> selectedCards = robot.selectCardsToPlay(lastClaim);
            if (selectedCards.isEmpty()) {
                // 机器人选择过牌
                handlePass(room.getId(), currentPlayerId);
                return;
            }

            // 生成声明
            String claim = robot.generateClaim(selectedCards, lastClaim);
            
            // 执行出牌
            handlePlayCards(room.getId(), currentPlayerId, selectedCards, claim);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 如果被中断，让下一个玩家继续
            String nextPlayerId = room.getNextPlayerId();
            if (nextPlayerId != null) {
                gameState.setCurrentPlayer(nextPlayerId);
                gameState.setCurrentPlayerIndex(room.getPlayers().indexOf(nextPlayerId));
                sendGameStateUpdate(room.getId());
            }
        } catch (Exception e) {
            // 处理其他异常，记录错误并让下一个玩家继续
            log.error("机器人回合处理出错: " + e.getMessage());
            e.printStackTrace();
            String nextPlayerId = room.getNextPlayerId();
            if (nextPlayerId != null) {
                gameState.setCurrentPlayer(nextPlayerId);
                gameState.setCurrentPlayerIndex(room.getPlayers().indexOf(nextPlayerId));
                sendGameStateUpdate(room.getId());
            }
        }
    }

    /**
     * 修改原有的handlePlayCards方法，在处理完玩家出牌后检查下一个玩家是否是机器人
     */
    public void handlePlayCards(String roomId, String playerId, List<Card> cards, String claim) {
        // ... 原有的出牌处理逻辑 ...

        // 在处理完出牌后，检查下一个玩家是否是机器人
        GameRoom room = rooms.get(roomId);
        GameState gameState = gameStates.get(roomId);
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room, gameState);
        }
    }

    /**
     * 修改原有的handleChallenge方法，在处理完质疑后检查下一个玩家是否是机器人
     */
    public void handleChallenge(String roomId, String playerId) {
        // ... 原有的质疑处理逻辑 ...

        // 在处理完质疑后，检查下一个玩家是否是机器人
        GameRoom room = rooms.get(roomId);
        GameState gameState = gameStates.get(roomId);
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room, gameState);
        }
    }

    /**
     * 修改原有的handlePass方法，在处理完过牌后检查下一个玩家是否是机器人
     */
    public void handlePass(String roomId, String playerId) {
        // ... 原有的过牌处理逻辑 ...

        // 在处理完过牌后，检查下一个玩家是否是机器人
        GameRoom room = rooms.get(roomId);
        GameState gameState = gameStates.get(roomId);
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room, gameState);
        }
    }

    /**
     * 广播房间状态更新
     * @param room 游戏房间
     */
    private void broadcastRoomState(GameRoom room) {
        if (messagingTemplate == null) return;
        
        // 获取游戏状态
        GameState state = gameStates.get(room.getId());
        if (state == null) return;
        
        // 更新状态中的机器人信息
        state.setRobotCount(room.getRobotCount());
        
        // 广播更新
        messagingTemplate.convertAndSend("/topic/game/state/" + room.getId(), state);
        
        // 广播房间列表更新（不包含机器人信息）
        List<GameRoom> publicRooms = rooms.values().stream()
                .map(this::createPublicRoomView)
                .toList();
        messagingTemplate.convertAndSend("/topic/rooms", publicRooms);
    }

    /**
     * 创建房间的公开视图（不包含机器人信息）
     * @param room 原始房间
     * @return 公开视图
     */
    private GameRoom createPublicRoomView(GameRoom room) {
        GameRoom publicView = new GameRoom();
        publicView.setId(room.getId());
        publicView.setHostId(room.getHostId());
        publicView.setMaxPlayers(room.getMaxPlayers());
        publicView.setStatus(room.getStatus());
        
        // 只包含非机器人玩家
        List<String> humanPlayers = room.getPlayers().stream()
                .filter(p -> !room.isRobot(p))
                .toList();
        publicView.setPlayers(new ArrayList<>(humanPlayers));
        
        // 设置准备状态
        List<String> readyHumanPlayers = room.getReadyPlayers().stream()
                .filter(p -> !room.isRobot(p))
                .toList();
        publicView.setReadyPlayers(new ArrayList<>(readyHumanPlayers));
        
        return publicView;
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
            log.warn("房间不存在: {}", roomId);
            return false;
        }
        
        try {
            // 获取房间对象
            GameRoom room = rooms.get(roomId);
            
            // 确保房间中没有玩家
            if (room.getPlayers() != null && !room.getPlayers().isEmpty()) {
                log.warn("房间 {} 中仍有玩家，无法删除", roomId);
                return false;
            }
            
            // 从房间集合中移除
            rooms.remove(roomId);
            
            // 从游戏状态集合中移除
            gameStates.remove(roomId);
            
            log.info("房间已从系统中移除: {}", roomId);
            return true;
        } catch (Exception e) {
            log.error("移除房间时发生错误: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 更新游戏状态中的机器人信息
     * @param roomId 房间ID
     */
    public void updateRobotInfo(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RuntimeException("房间不存在");
        }
        
        GameState state = gameStates.get(roomId);
        if (state == null) {
            throw new RuntimeException("游戏状态不存在");
        }
        
        // 更新机器人计数
        state.setRobotCount(room.getRobotCount());
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
                log.info("已将玩家 {} 从房间 {} 中移除", playerId, room.getId());
            } catch (Exception e) {
                log.error("从房间 {} 移除玩家 {} 时出错: {}", room.getId(), playerId, e.getMessage());
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
            
            log.info("已清理玩家 {} 的游戏状态", playerId);
        } catch (Exception e) {
            log.error("清理玩家游戏状态时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理玩家退出房间
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void handlePlayerExit(String roomId, String playerId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;

        room.handlePlayerExit(playerId);
        
        // 如果是房主退出，且房间还有其他玩家，转移房主权限
        if (playerId.equals(room.getHostId()) && !room.getPlayers().isEmpty()) {
            // 找到第一个非机器人玩家作为新房主
            String newHost = room.getPlayers().stream()
                    .filter(p -> !room.isRobot(p))
                    .findFirst()
                    .orElse(null);
            
            if (newHost != null) {
                room.setHostId(newHost);
            } else {
                // 如果没有非机器人玩家，解散房间
                rooms.remove(roomId);
                gameStates.remove(roomId);
                
                // 广播房间解散消息
                if (messagingTemplate != null) {
                    GameNotification notification = new GameNotification();
                    notification.setType("ROOM_DISSOLVED");
                    notification.setContent("房间已解散");
                    notification.setRoomId(roomId);
                    messagingTemplate.convertAndSend("/topic/game/notification/" + roomId, notification);
                }
                return;
            }
        }
        
        // 检查房间是否需要解散
        if (room.getPlayers().isEmpty() || room.getPlayers().stream().allMatch(room::isRobot)) {
            rooms.remove(roomId);
            gameStates.remove(roomId);
            
            // 广播房间解散消息
            if (messagingTemplate != null) {
                GameNotification notification = new GameNotification();
                notification.setType("ROOM_DISSOLVED");
                notification.setContent("房间已解散");
                notification.setRoomId(roomId);
                messagingTemplate.convertAndSend("/topic/game/notification/" + roomId, notification);
            }
        } else {
            // 广播房间状态更新
            broadcastRoomState(room);
        }
    }
}