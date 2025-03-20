package com.example.poker.model;

import java.util.List;

public class Player {
    private String id;
    private List<Card> hand;
    private int score;
    private boolean active;

    public Player(String id) {
        this.id = id;
    }

    // Getters and setters
    public String getId() { return id; }
    public List<Card> getHand() { return hand; }
    public int getScore() { return score; }
    public boolean isActive() { return active; }

    public void setHand(List<Card> hand) { this.hand = hand; }
    public void setScore(int score) { this.score = score; }
    public void setActive(boolean active) { this.active = active; }
}