package com.example.poker.service;

import com.example.poker.model.Card;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class DeckService {
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] RANKS = {"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"};

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

    private void createSingleDeck(List<Card> decks) {
        for (String suit : SUITS) {
            for (int j = 0; j < RANKS.length; j++) {
                decks.add(new Card(RANKS[j], suit, String.valueOf(j + 3),suit)); // 3-15的数值
            }
        }
        // 添加大小王
        decks.add(new Card("Red Joker", "","16",""));
        decks.add(new Card("Black Joker", "","17",""));
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
}