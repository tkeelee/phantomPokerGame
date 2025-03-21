package com.example.poker.service;

import com.example.poker.model.GameState;
import com.example.poker.model.Card;
import com.example.poker.model.GameStatus;
import com.example.poker.exception.GameException;
import com.example.poker.constant.GameConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 游戏玩法服务
 * 负责处理游戏的核心玩法逻辑，包括：
 * 1. 游戏开始
 * 2. 出牌逻辑
 * 3. 挑战机制
 * 4. 游戏结束判定
 */
@Service
public class GamePlayService {
    private static final Logger logger = LoggerFactory.getLogger(GamePlayService.class);
    
    private final RoomManagementService roomManagementService;
    private final PlayerService playerService;

    public GamePlayService(RoomManagementService roomManagementService, PlayerService playerService) {
        this.roomManagementService = roomManagementService;
        this.playerService = playerService;
    }

    /**
     * 开始游戏
     * @param roomId 房间ID
     * @param hostId 房主ID
     */
    public void startGame(String roomId, String hostId) {
        logger.info("开始游戏 - 房间ID: {}, 房主ID: {}", roomId, hostId);
        
        GameState state = roomManagementService.getGameState(roomId);
        if (!state.getHostId().equals(hostId)) {
            logger.error("非房主无法开始游戏 - 房间ID: {}, 操作者ID: {}, 房主ID: {}", 
                roomId, hostId, state.getHostId());
            throw new GameException("只有房主可以开始游戏", "NOT_HOST");
        }
        
        if (state.getPlayers().size() < 2) {
            logger.error("玩家数量不足 - 房间ID: {}, 当前玩家数: {}", roomId, state.getPlayers().size());
            throw new GameException("至少需要2名玩家才能开始游戏", "NOT_ENOUGH_PLAYERS");
        }
        
        // 初始化游戏状态
        state.setStatus(GameStatus.PLAYING);
        state.setGameStatus(GameConstants.GAME_STATUS_PLAYING);
        state.setCurrentPlayer(state.getPlayers().get(0));
        state.setCurrentPile(new ArrayList<>());
        state.setPlayerHands(new HashMap<>());
        state.setLastPlayedCards(new ArrayList<>());
        state.setLastPlayedValue(null);
        state.setLastPlayedPlayer(null);
        state.setLastPlayedTime(null);
        state.setLastChallengeTime(null);
        state.setLastChallengePlayer(null);
        state.setLastChallengeResult(null);
        state.setLastChallengeCards(null);
        state.setLastChallengeValue(null);
        state.setLastChallengeSuccess(null);
        state.setLastChallengePile(null);
        state.setLastChallengeHands(null);
        
        // 发牌
        dealCards(state);
        
        // 通知所有玩家游戏开始
        for (String playerId : state.getPlayers()) {
            playerService.notifyGameStart(playerId, state);
        }
        
        logger.info("游戏开始成功 - 房间ID: {}", roomId);
    }

    /**
     * 出牌
     * @param roomId 房间ID
     * @param playerId 玩家ID
     * @param cards 要出的牌
     * @param declaredValue 声明的牌值
     */
    public void playCards(String roomId, String playerId, List<Card> cards, String declaredValue) {
        logger.info("玩家出牌 - 房间ID: {}, 玩家ID: {}, 牌数: {}, 声明值: {}", 
            roomId, playerId, cards.size(), declaredValue);
        
        GameState state = roomManagementService.getGameState(roomId);
        validatePlay(state, playerId, cards, declaredValue);
        
        // 更新游戏状态
        state.setLastPlayedCards(cards);
        state.setLastPlayedValue(declaredValue);
        state.setLastPlayedPlayer(playerId);
        state.setLastPlayedTime(new Date());
        
        // 从玩家手牌中移除打出的牌
        List<Card> playerHand = state.getPlayerHands().get(playerId);
        playerHand.removeAll(cards);
        
        // 将打出的牌加入底盘
        state.getCurrentPile().addAll(cards);
        
        // 检查玩家是否已经出完牌
        if (playerHand.isEmpty()) {
            logger.info("玩家出完所有牌 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
            endGame(state, playerId);
            return;
        }
        
        // 轮到下一个玩家
        int currentIndex = state.getPlayers().indexOf(playerId);
        int nextIndex = (currentIndex + 1) % state.getPlayers().size();
        String nextPlayer = state.getPlayers().get(nextIndex);
        state.setCurrentPlayer(nextPlayer);
        
        // 通知所有玩家出牌结果
        for (String pId : state.getPlayers()) {
            playerService.notifyPlayResult(pId, state, playerId, cards, declaredValue, nextPlayer);
        }
        
        logger.info("出牌成功，轮到下一个玩家 - 房间ID: {}, 下一个玩家: {}", roomId, nextPlayer);
    }

    /**
     * 挑战上一个玩家
     * @param roomId 房间ID
     * @param playerId 玩家ID
     */
    public void challenge(String roomId, String playerId) {
        logger.info("玩家发起挑战 - 房间ID: {}, 玩家ID: {}", roomId, playerId);
        
        GameState state = roomManagementService.getGameState(roomId);
        validateChallenge(state, playerId);
        
        // 记录挑战信息
        state.setLastChallengeTime(new Date());
        state.setLastChallengePlayer(playerId);
        
        // 检查上一个玩家是否说谎
        boolean isLying = checkIfLastPlayerLied(state);
        state.setLastChallengeSuccess(isLying);
        
        String lastPlayer = state.getLastPlayedPlayer();
        if (isLying) {
            // 上一个玩家说谎，获得所有底盘牌
            logger.info("挑战成功，上一个玩家说谎 - 房间ID: {}", roomId);
            state.getPlayerHands().get(lastPlayer).addAll(state.getCurrentPile());
            state.setCurrentPile(new ArrayList<>());
            state.setCurrentPlayer(lastPlayer);
        } else {
            // 挑战失败，挑战者获得所有底盘牌
            logger.info("挑战失败，挑战者获得所有底盘牌 - 房间ID: {}", roomId);
            state.getPlayerHands().get(playerId).addAll(state.getCurrentPile());
            state.setCurrentPile(new ArrayList<>());
            state.setCurrentPlayer(playerId);
        }
        
        // 通知所有玩家挑战结果
        for (String pId : state.getPlayers()) {
            playerService.notifyChallengeResult(pId, state, playerId, lastPlayer, isLying);
        }
    }

    /**
     * 验证出牌是否合法
     */
    private void validatePlay(GameState state, String playerId, List<Card> cards, String declaredValue) {
        if (!state.getCurrentPlayer().equals(playerId)) {
            logger.error("不是当前玩家的回合 - 房间ID: {}, 当前玩家: {}, 操作玩家: {}", 
                state.getRoomId(), state.getCurrentPlayer(), playerId);
            throw new GameException("不是你的回合", "NOT_YOUR_TURN");
        }
        
        if (cards == null || cards.isEmpty()) {
            logger.error("出牌不能为空 - 房间ID: {}, 玩家ID: {}", state.getRoomId(), playerId);
            throw new GameException("必须出牌", "MUST_PLAY_CARDS");
        }
        
        // 验证牌的数量是否与上一次相同
        if (state.getLastPlayedCards() != null && !state.getLastPlayedCards().isEmpty()) {
            if (cards.size() != state.getLastPlayedCards().size()) {
                logger.error("出牌数量必须与上一次相同 - 房间ID: {}, 玩家ID: {}, 当前数量: {}, 上一次数量: {}", 
                    state.getRoomId(), playerId, cards.size(), state.getLastPlayedCards().size());
                throw new GameException("出牌数量必须与上一次相同", "INVALID_CARD_COUNT");
            }
        }
        
        // 验证声明的牌值是否合法
        if (!isValidValue(declaredValue)) {
            logger.error("声明的牌值不合法 - 房间ID: {}, 玩家ID: {}, 声明值: {}", 
                state.getRoomId(), playerId, declaredValue);
            throw new GameException("声明的牌值不合法", "INVALID_VALUE");
        }
    }

    /**
     * 验证挑战是否合法
     */
    private void validateChallenge(GameState state, String playerId) {
        if (!state.getCurrentPlayer().equals(playerId)) {
            logger.error("不是当前玩家的回合 - 房间ID: {}, 当前玩家: {}, 操作玩家: {}", 
                state.getRoomId(), state.getCurrentPlayer(), playerId);
            throw new GameException("不是你的回合", "NOT_YOUR_TURN");
        }
        
        if (state.getLastPlayedCards() == null || state.getLastPlayedCards().isEmpty()) {
            logger.error("没有可以挑战的出牌 - 房间ID: {}, 玩家ID: {}", state.getRoomId(), playerId);
            throw new GameException("没有可以挑战的出牌", "NO_CARDS_TO_CHALLENGE");
        }
    }

    /**
     * 检查上一个玩家是否说谎
     */
    private boolean checkIfLastPlayerLied(GameState state) {
        List<Card> lastPlayedCards = state.getLastPlayedCards();
        String declaredValue = state.getLastPlayedValue();
        
        return lastPlayedCards.stream()
            .anyMatch(card -> !String.valueOf(card.getValue()).equals(declaredValue));
    }

    /**
     * 验证牌值是否合法
     */
    private boolean isValidValue(String value) {
        return Arrays.asList("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")
            .contains(value);
    }

    /**
     * 发牌
     */
    private void dealCards(GameState state) {
        List<Card> deck = createDeck();
        Collections.shuffle(deck);
        
        int cardsPerPlayer = deck.size() / state.getPlayers().size();
        for (String playerId : state.getPlayers()) {
            List<Card> playerHand = new ArrayList<>();
            for (int i = 0; i < cardsPerPlayer; i++) {
                playerHand.add(deck.remove(0));
            }
            state.getPlayerHands().put(playerId, playerHand);
        }
    }

    /**
     * 创建一副牌
     */
    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        Card.Suit[] suits = {Card.Suit.SPADES, Card.Suit.HEARTS, Card.Suit.CLUBS, Card.Suit.DIAMONDS};
        int[] values = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}; // A-K
        
        for (Card.Suit suit : suits) {
            for (int value : values) {
                deck.add(new Card(suit, value));
            }
        }
        
        return deck;
    }

    /**
     * 结束游戏
     */
    private void endGame(GameState state, String winnerId) {
        logger.info("游戏结束 - 房间ID: {}, 获胜者: {}", state.getRoomId(), winnerId);
        
        state.setStatus(GameStatus.FINISHED);
        state.setGameStatus(GameConstants.GAME_STATUS_FINISHED);
        state.setWinner(winnerId);
    }
} 