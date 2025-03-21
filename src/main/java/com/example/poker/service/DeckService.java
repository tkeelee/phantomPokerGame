package com.example.poker.service;

import com.example.poker.model.Card;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.HashMap;

/**
 * 牌组服务类，负责创建、洗牌和发牌
 */
@Service
public class DeckService {

    /**
     * 生成指定数量的洗牌后的扑克牌堆
     * @param deckCount 扑克牌副数
     * @return 洗牌后的牌堆列表
     */
    public List<Card> generateShuffledDecks(int deckCount) {
        List<Card> decks = new ArrayList<>();
        
        for (int i = 0; i < deckCount; i++) {
            createSingleDeck(decks);
        }
        
        fisherYatesShuffle(decks);
        return decks;
    }

    /**
     * 创建单副牌（包含54张牌）
     * @param decks 牌的集合
     */
    private void createSingleDeck(List<Card> decks) {
        // 添加普通牌
        for (Card.Suit suit : Card.Suit.values()) {
            for (int value = 1; value <= 13; value++) {
                decks.add(new Card(suit, value));
            }
        }
        
        // 添加大小王
        decks.add(new Card(true));  // 大王
        decks.add(new Card(false)); // 小王
    }

    /**
     * Fisher-Yates洗牌算法实现
     * @param list 需要洗牌的牌堆
     */
    private void fisherYatesShuffle(List<Card> list) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = list.size() - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            Collections.swap(list, index, i);
        }
    }
    
    /**
     * 均匀分牌给玩家
     * @param deck 牌组
     * @param players 玩家ID列表
     * @return 玩家ID到手牌的映射
     */
    public Map<String, List<Card>> dealCards(List<Card> deck, List<String> players) {
        Map<String, List<Card>> playerHands = new HashMap<>();
        
        // 初始化每个玩家的手牌
        for (String playerId : players) {
            playerHands.put(playerId, new ArrayList<>());
        }
        
        // 轮流发牌
        int playerIndex = 0;
        int playerCount = players.size();
        
        while (!deck.isEmpty()) {
            String currentPlayerId = players.get(playerIndex);
            playerHands.get(currentPlayerId).add(deck.remove(0));
            playerIndex = (playerIndex + 1) % playerCount;
        }
        
        return playerHands;
    }

    /**
     * 从牌组中发指定数量的牌
     * @param deck 牌组
     * @param count 需要发的牌数量
     * @return 发出的牌
     */
    public List<Card> dealCards(List<Card> deck, int count) {
        List<Card> hand = new ArrayList<>();
        
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            hand.add(deck.remove(0));
        }
        
        return hand;
    }

    /**
     * 创建并洗牌
     * @param deckCount 牌组数量
     * @return 已洗牌的牌组
     */
    public List<Card> createAndShuffleDeck(int deckCount) {
        List<Card> deck = new ArrayList<>();
        for (int i = 0; i < deckCount; i++) {
            createSingleDeck(deck);
        }
        fisherYatesShuffle(deck);
        return deck;
    }
}