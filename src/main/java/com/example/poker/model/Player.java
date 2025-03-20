package com.example.poker.model;

import java.util.List;
import java.util.ArrayList;

public class Player {
    private String id;
    private String name;
    private List<Card> hand;
    private int score;
    private boolean active;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.hand = new ArrayList<>();
        this.score = 0;
        this.active = true;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Card> getHand() { return hand; }
    public void addToHand(Card card) {
        hand.add(card);
    }

    public void removeFromHand(Card card) {
        hand.remove(card);
    }

    public void updateScore(int delta) {
        score += delta;
    }

    public int getScore() { return score; }
    public boolean isActive() { return active; }

    public void setHand(List<Card> hand) { this.hand = hand; }
    public void setScore(int score) { this.score = score; }
    public void setActive(boolean active) { this.active = active; }
}