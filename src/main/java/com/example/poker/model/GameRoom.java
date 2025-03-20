package com.example.poker.model;

import java.util.*;

public class GameRoom {
    private List<String> players = new ArrayList<>();
    private Map<String, List<Card>> playerHands = new HashMap<>();
    private List<Card> cardDeck = new LinkedList<>();
    private String lastClaim;
    
    // 初始化多副扑克牌
    public GameRoom() {
        initializeDecks(2); // 使用两副牌
    }

    private void initializeDecks(int deckCount) {
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A", "2"};
        
        for(int i=0; i<deckCount; i++) {
            for(String suit : suits) {
                for(String rank : ranks) {
                    cardDeck.add(new Card(rank, suit,rank, suit.equals("♥") || suit.equals("♦") ? "red" : "black"));
                }
            }
        }
        Collections.shuffle(cardDeck);
    }

    public void addPlayer(Player player) {
        players.add(player.getId());
        dealInitialCards(player.getId());
    }

    private void dealInitialCards(String playerId) {
        List<Card> hand = new ArrayList<>();
        for(int i=0; i<13; i++) {
            hand.add(cardDeck.remove(0));
        }
        playerHands.put(playerId, hand);
    }

    // 验证牌型声明
    public boolean validateCardClaim(List<Card> cards, String claim) {
        // 实现牌型验证逻辑（单张、对子、顺子等）
        return true; // 示例实现
    }

    public void transferCardsToDeck(List<Card> cards) {
        cardDeck.addAll(cards);
        lastClaim = cards.toString();
    }

    public boolean validateLastClaim() {
        // 实际验证逻辑
        return true;
    }

    public void applyChallengePenalty(String challenger, String target, boolean isValid) {
        // 处理挑战成功/失败的惩罚逻辑
    }

    public String getRoomStatus() {
        return players.size() >= 2 ? "READY" : "WAITING";
    }
}