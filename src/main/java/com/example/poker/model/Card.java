package com.example.poker.model;

public class Card {
    private String rank;
    private String suit;

    public Card(String rank, String suit) {
        this.rank = rank;
        this.suit = suit;
    }

    // Getters and setters
    public String getRank() { return rank; }
    public String getSuit() { return suit; }

    public void setRank(String rank) { this.rank = rank; }
    public void setSuit(String suit) { this.suit = suit; }

    @Override
    public String toString() {
        return rank + suit;
    }
}