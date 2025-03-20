package com.example.poker.model;

import java.util.*;

public class CardDeck {
    private Map<String, List<Card>> playerHands = new HashMap<>();
    private List<Card> discardPile = new ArrayList<>();
    private Map<String, String> lastClaims = new HashMap<>();
    private List<Card> fullDeck = new ArrayList<>();

    public void initializeDecks(int numberOfDecks) {
        fullDeck.clear();
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        
        for (int i = 0; i < numberOfDecks; i++) {
            for (String suit : suits) {
                for (String rank : ranks) {
                    fullDeck.add(rank + suit);
                }
            }
        }
        Collections.shuffle(fullDeck);
    }

    public void dealInitialHands(List<String> playerIds, int cardsPerPlayer) {
        for (String playerId : playerIds) {
            List<Card> hand = new ArrayList<>();
            for (int i = 0; i < cardsPerPlayer; i++) {
                hand.add(fullDeck.remove(0));
            }
            playerHands.put(playerId, hand);
        }
    }

    public void removeCards(String playerId, List<Card> cards) {
        List<Card> hand = playerHands.get(playerId);
        hand.removeAll(cards);
        discardPile.addAll(cards);
    }

    public boolean validateLastClaim(String playerId) {
        String claim = lastClaims.get(playerId);
        String[] parts = claim.split(" ");
        int claimedCount = Integer.parseInt(parts[0]);
        String claimedRank = parts[1];
        
        long actualCount = discardPile.stream()
            .filter(card -> card.startsWith(claimedRank))
            .count();
        
        return actualCount >= claimedCount;
    }

    public void recordClaim(String playerId, String claim) {
        lastClaims.put(playerId, claim);
    }
}