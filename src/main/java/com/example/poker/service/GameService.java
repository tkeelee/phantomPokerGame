package com.example.poker.service;

import com.example.poker.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;

/**
 * 游戏服务类，负责处理游戏的主要逻辑
 */
@Service
public class GameService {
    private Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    
    @Autowired
    private DeckService deckService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    @Lazy
    private RoomManagementService roomManagementService;

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    /**
     * 同步房间数据
     * @param rooms 房间映射
     */
    public void syncRooms(Map<String, GameRoom> rooms) {
        this.rooms = rooms;
    }

    /**
     * 保留此方法以兼容现有代码，但内部实现已更改
     */
    public void syncRoomsAndGameStates(Map<String, GameRoom> rooms, Map<String, GameState> gameStates) {
        this.rooms = rooms;
        // gameStates不再使用，忽略此参数
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

        // 发送状态更新
        sendGameStateUpdate(roomId);
        
        // 如果所有玩家都准备好了，房主可以开始游戏
        if (room.getReadyPlayers().size() == room.getPlayers().size() && room.getPlayers().size() >= 2) {
            room.setStatus(GameStatus.READY);
            room.setGameStatus("READY");
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
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
        //临时注释
        //if (room.getStatus() != GameStatus.READY) {
            //throw new RuntimeException("玩家未全部准备");
        //}
        if (room.getPlayers().size() < 2) {
            throw new RuntimeException("玩家人数不足");
        }
        
        // 生成牌堆
        List<Card> deck = deckService.generateShuffledDecks(deckCount);
        
        // 分发手牌
        Map<String, List<Card>> playerHands = deckService.dealCards(deck, room.getPlayers());
        
        // 更新房间状态
        room.setStatus(GameStatus.PLAYING);
        room.setGameStatus("PLAYING");
        room.setCardDeck(new ArrayList<>());  // 牌已经分完了
        room.setPlayerHands(playerHands);
        room.setCurrentPlayerIndex(0);  // 房主先手
        room.setCurrentPile(new ArrayList<>());
        room.setCurrentPlayer(room.getPlayers().get(0));
        
        // 发送状态更新
        sendGameStateUpdate(roomId);
        
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
        
        // 检查是否是当前玩家的回合
        if (!message.getPlayerId().equals(room.getCurrentPlayer())) {
            throw new RuntimeException("不是你的回合");
        }
        
        // 检查玩家是否有这些牌
        List<Card> playerCards = room.getPlayerHands().get(message.getPlayerId());
        List<Card> selectedCards = message.getCards();
        if (!hasAllCards(playerCards, selectedCards)) {
            throw new RuntimeException("你没有这些牌");
        }
        
        // 如果已有上一玩家出牌且有声明值，则当前玩家必须遵守相同的声明值
        // 除非所有其他玩家都已过牌，此时玩家可以自由选择牌值
        if (room.getLastClaim() != null && !room.haveAllPlayersPassed()) {
            if (!message.getDeclaredValue().equals(room.getLastClaim())) {
                throw new RuntimeException("必须声明与上一玩家相同的牌值: " + room.getLastClaim());
            }
        }
        
        // 从玩家手牌中移除这些牌
        removeCards(playerCards, selectedCards);
        
        // 添加到当前牌堆
        room.getCurrentPile().addAll(selectedCards);
        
        // 记录玩家声明
        room.setLastClaim("玩家" + message.getPlayerId() + 
                           "打出" + message.getDeclaredCount() + 
                           "张" + message.getDeclaredValue());
        
        // 清空过牌玩家列表，因为新的一轮出牌开始了
        room.clearPassedPlayers();
        
        // 检查是否有其他玩家已经打完手牌但仍在等待确认
        // 当其他玩家出牌后，这些玩家满足"被质疑失败或其他玩家出牌"的条件
        List<String> pendingWinners = new ArrayList<>(room.getWinners());
        for (String winner : pendingWinners) {
            // 如果赢家不是当前出牌的玩家，且仍在玩家列表中
            if (!winner.equals(message.getPlayerId()) && room.getPlayers().contains(winner)) {
                // 将该玩家从当前玩家列表中移除（确认胜利）
                room.getPlayers().remove(winner);
            }
        }
        
        // 检查是否胜利（手牌为空）
        if (playerCards.isEmpty()) {
            handlePlayerWin(roomId, message.getPlayerId());
            return room;
        }
        
        // 切换到下一个玩家
        int nextIndex = (room.getPlayers().indexOf(message.getPlayerId()) + 1) % room.getPlayers().size();
        room.setCurrentPlayer(room.getPlayers().get(nextIndex));
        room.setCurrentPlayerIndex(nextIndex);
        
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
        
        // 获取当前牌堆中的牌
        List<Card> currentPile = room.getCurrentPile();
        if (currentPile.isEmpty()) {
            throw new RuntimeException("当前没有可质疑的牌");
        }
        
        // 获取声明的值
        String declaredValue = room.getLastClaim();
        if (declaredValue == null) {
            throw new RuntimeException("当前没有声明");
        }
        
        // 判断质疑是否成功
        boolean challengeSuccess = !isClaimValid(currentPile, declaredValue);
        
        // 处理质疑结果
        if (challengeSuccess) {
            // 质疑成功，出牌者收走底盘
            String targetPlayerId = message.getTargetPlayerId(); // 被质疑的玩家
            punishPlayer(room, targetPlayerId);
            
            // 如果被质疑的玩家之前已经打完手牌（手牌为空但仍在players列表中等待确认）
            // 则需要将其从winners列表中移除，因为质疑成功意味着他需要重新开始
            List<Card> targetPlayerHand = room.getPlayerHands().get(targetPlayerId);
            if (targetPlayerHand.isEmpty() && room.getWinners().contains(targetPlayerId)) {
                room.getWinners().remove(targetPlayerId);
            }
            
            // 质疑玩家获得出牌权
            room.setCurrentPlayer(message.getPlayerId());
            room.setCurrentPlayerIndex(room.getPlayers().indexOf(message.getPlayerId()));
        } else {
            // 质疑失败，质疑者收走底盘
            punishPlayer(room, message.getPlayerId());
            // 被质疑玩家ID
            String targetPlayerId = message.getTargetPlayerId();
            // 出牌者获得出牌权
            room.setCurrentPlayer(targetPlayerId);
            room.setCurrentPlayerIndex(room.getPlayers().indexOf(targetPlayerId));
            
            // 如果被质疑的玩家手牌为空，且在winners列表中，此时应确认其真正赢得比赛
            // 因为他被质疑但质疑失败，满足"出完手牌后无人质疑成功"的条件
            List<Card> targetPlayerHand = room.getPlayerHands().get(targetPlayerId);
            if (targetPlayerHand.isEmpty() && room.getWinners().contains(targetPlayerId)) {
                // 将玩家从游戏中移除，但保留在winners列表中
                room.getPlayers().remove(targetPlayerId);
            }
        }
        
        // 清空当前牌堆
        room.getCurrentPile().clear();
        room.setLastClaim(null);
        
        // 清空过牌玩家列表，因为新的一轮开始了
        room.clearPassedPlayers();
        
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
        
        // 检查是否是当前玩家的回合
        if (!playerId.equals(room.getCurrentPlayer())) {
            throw new RuntimeException("不是你的回合");
        }
        
        // 记录该玩家已过牌
        room.addPassedPlayer(playerId);
        
        // 切换到下一个玩家
        int nextIndex = (room.getPlayers().indexOf(playerId) + 1) % room.getPlayers().size();
        room.setCurrentPlayer(room.getPlayers().get(nextIndex));
        room.setCurrentPlayerIndex(nextIndex);
        
        // 如果下一个玩家是最初出牌的玩家，说明已经轮了一圈都过牌了
        // 此时清空过牌记录，给这个玩家重新出牌的自由度
        if (nextIndex == room.getPlayers().indexOf(room.getLastPlayerId())) {
            room.clearPassedPlayers();
        }
        
        // 发送状态更新
        sendGameStateUpdate(roomId);
        
        return room;
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
        
        List<PlayerState> players = new ArrayList<>();
        for (String playerId : room.getPlayers()) {
            // 创建一个临时的Player对象
            Player player = new Player(playerId, playerId);
            player.setHand(room.getPlayerHands().getOrDefault(playerId, new ArrayList<>()));
            player.setReady(room.getReadyPlayers().contains(playerId));
            player.setHost(playerId.equals(room.getHostId()));
            
            // 转换为PlayerState
            PlayerState playerState = PlayerState.fromPlayer(player);
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
        
        if (room != null) {
            // 检查是否有人质疑当前出牌
            if (room.getLastClaim() != null && room.getLastPlayerId() != null 
                && room.getLastPlayerId().equals(playerId)) {
                // 添加玩家到胜利列表，但不从游戏中移除
                // 只有当被质疑失败或其他玩家出牌后，才算真正胜利
                if (!room.getWinners().contains(playerId)) {
                    room.getWinners().add(playerId);
                }
            } else {
                // 如果是被质疑失败或其他玩家已出牌，则算真正胜利
                // 将玩家从当前玩家列表中移除，但保留在胜利列表中
                if (!room.getWinners().contains(playerId)) {
                    room.getWinners().add(playerId);
                }
                room.getPlayers().remove(playerId);
            }
            
            // 如果只剩最后一个玩家有手牌，游戏结束
            if (room.checkGameEnd()) {
                room.setStatus(GameStatus.FINISHED);
                room.setGameStatus("FINISHED");
            }
            
            // 发送状态更新
            sendGameStateUpdate(roomId);
        }
    }
    
    /**
     * 惩罚玩家（将底盘牌加入其手牌）
     * @param room 游戏房间
     * @param playerId 被惩罚的玩家ID
     */
    private void punishPlayer(GameRoom room, String playerId) {
        List<Card> playerHand = room.getPlayerHands().get(playerId);
        if (playerHand != null && room.getCurrentPile() != null) {
            playerHand.addAll(room.getCurrentPile());
        }
    }
    
    /**
     * 发送游戏状态更新到客户端
     * @param roomId 房间ID
     */
    public void sendGameStateUpdate(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room != null && messagingTemplate != null) {
            // 使用toGameState方法转换为GameState对象，保持与前端的兼容性
            GameState state = room.toGameState();
            messagingTemplate.convertAndSend("/topic/game/state/" + roomId, state);
        }
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
     */
    private void handleRobotTurn(GameRoom room) {
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
            robot.setHand(room.getPlayerHands().get(currentPlayerId));

            // 获取上一个声明
            String lastClaim = room.getLastClaim();
            List<Card> currentPile = room.getCurrentPile();

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
                room.setCurrentPlayer(nextPlayerId);
                room.setCurrentPlayerIndex(room.getPlayers().indexOf(nextPlayerId));
                sendGameStateUpdate(room.getId());
            }
        } catch (Exception e) {
            // 处理其他异常，记录错误并让下一个玩家继续
            log.error("机器人回合处理出错: " + e.getMessage());
            e.printStackTrace();
            String nextPlayerId = room.getNextPlayerId();
            if (nextPlayerId != null) {
                room.setCurrentPlayer(nextPlayerId);
                room.setCurrentPlayerIndex(room.getPlayers().indexOf(nextPlayerId));
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
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room);
        }
    }

    /**
     * 修改原有的handleChallenge方法，在处理完质疑后检查下一个玩家是否是机器人
     */
    public void handleChallenge(String roomId, String playerId) {
        // ... 原有的质疑处理逻辑 ...

        // 在处理完质疑后，检查下一个玩家是否是机器人
        GameRoom room = rooms.get(roomId);
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room);
        }
    }

    /**
     * 修改原有的handlePass方法，在处理完过牌后检查下一个玩家是否是机器人
     */
    public void handlePass(String roomId, String playerId) {
        // ... 原有的过牌处理逻辑 ...

        // 在处理完过牌后，检查下一个玩家是否是机器人
        GameRoom room = rooms.get(roomId);
        
        String nextPlayerId = room.getNextPlayerId();
        if (room.isRobot(nextPlayerId)) {
            // 如果下一个玩家是机器人，执行机器人的回合
            handleRobotTurn(room);
        }
    }

    /**
     * 广播房间状态
     * @param room 房间
     */
    public void broadcastRoomState(GameRoom room) {
        if (room == null) {
            return;
        }
        
        // 获取公共视图的房间数据
        GameRoom publicRoom = createPublicRoomView(room);
        
        // 向房间内的每个玩家发送他们的私有视图
        for (String playerId : room.getPlayers()) {
            GameRoom playerView = createPlayerView(room, playerId);
            messagingTemplate.convertAndSendToUser(playerId, "/queue/game", playerView);
        }
        
        // 向房间主题广播公共视图
        messagingTemplate.convertAndSend("/topic/game/" + room.getId(), publicRoom);
        
        // 使用RoomManagementService广播房间列表更新，确保数据一致性
        roomManagementService.syncRoomData();
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
     * 创建房间的玩家私有视图
     * @param room 原始房间
     * @param playerId 玩家ID
     * @return 玩家私有视图
     */
    private GameRoom createPlayerView(GameRoom room, String playerId) {
        GameRoom playerView = new GameRoom();
        playerView.setId(room.getId());
        playerView.setHostId(room.getHostId());
        playerView.setMaxPlayers(room.getMaxPlayers());
        playerView.setStatus(room.getStatus());
        
        // 只包含玩家自己的手牌
        List<Card> playerCards = room.getPlayerHands().get(playerId);
        Map<String, List<Card>> playerHandMap = new HashMap<>();
        if (playerCards != null) {
            playerHandMap.put(playerId, playerCards);
        }
        playerView.setPlayerHands(playerHandMap);
        
        // 设置准备状态
        List<String> readyPlayers = new ArrayList<>();
        if (room.getReadyPlayers().contains(playerId)) {
            readyPlayers.add(playerId);
        }
        playerView.setReadyPlayers(readyPlayers);
        
        return playerView;
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
        
        // 更新机器人计数
        room.setRobotCount(room.getRobotCount());
    }

}