package com.example.poker.model;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 牌组类，负责创建和管理扑克牌组
 */
@Component
public class CardDeck {
    
    /**
     * 创建指定数量的完整牌组（包括大小王）
     * @param deckCount 牌组数量
     * @return 包含所有牌的牌组
     */
    public List<Card> createDeck(int deckCount) {
        List<Card> deck = new ArrayList<>();
        
        for (int i = 0; i < deckCount; i++) {
            // 添加普通牌
            for (Card.Suit suit : Card.Suit.values()) {
                for (int value = 1; value <= 13; value++) {
                    deck.add(new Card(suit, value));
                }
            }
            
            // 添加大小王
            deck.add(new Card(true));  // 大王
            deck.add(new Card(false)); // 小王
        }
        
        return deck;
    }
    
    /**
     * 洗牌
     * @param deck 需要洗牌的牌组
     * @return 洗牌后的牌组
     */
    public List<Card> shuffle(List<Card> deck) {
        Collections.shuffle(deck);
        return deck;
    }
    
    /**
     * 创建并洗牌
     * @param deckCount 牌组数量
     * @return 已洗牌的牌组
     */
    public List<Card> createAndShuffleDeck(int deckCount) {
        List<Card> deck = createDeck(deckCount);
        return shuffle(deck);
    }
    
    /**
     * 从牌组中发牌
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
     * 均匀发牌给所有玩家
     * @param deck 牌组
     * @param playerCount 玩家数量
     * @return 每个玩家的手牌
     */
    public List<List<Card>> dealToPlayers(List<Card> deck, int playerCount) {
        List<List<Card>> playerHands = new ArrayList<>();
        
        // 初始化每个玩家的手牌列表
        for (int i = 0; i < playerCount; i++) {
            playerHands.add(new ArrayList<>());
        }
        
        // 轮流发牌，直到牌组为空
        int currentPlayer = 0;
        while (!deck.isEmpty()) {
            playerHands.get(currentPlayer).add(deck.remove(0));
            currentPlayer = (currentPlayer + 1) % playerCount;
        }
        
        return playerHands;
    }
}