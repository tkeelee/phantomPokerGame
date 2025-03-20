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
}